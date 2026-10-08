import { randomBytes } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { compose, health, root, waitReady } from './compose-control.mjs';
import { assertArtifactsSafe } from './artifact-safety.mjs';

const artifacts = new URL('./artifacts/', import.meta.url);
await mkdir(artifacts, { recursive: true });
const stopping = process.argv.includes('--stop');
const reusing = process.argv.includes('--reuse');
const prior = stopping || reusing ? JSON.parse(await readFile(new URL('run-summary.json', artifacts), 'utf8')) : null;
const env = { ...process.env,
  COMPOSE_PROJECT_NAME: prior?.project ?? `msa-e2e-${Date.now().toString(36)}`,
  MYSQL_DATABASE: 'msa_e2e', MYSQL_USER: 'msa_e2e',
  MYSQL_PASSWORD: randomBytes(24).toString('hex'), MYSQL_ROOT_PASSWORD: randomBytes(24).toString('hex'),
  INTERNAL_API_TOKEN: randomBytes(32).toString('hex'),
  BACKEND_PORT: process.env.E2E_PORT ?? '8081', MARKET_CALENDAR_VERSION: '4.13.2',
  FIXTURE_FETCH_DELAY_SECONDS: '0'
};
env.E2E_BASE_URL = `http://127.0.0.1:${env.BACKEND_PORT}`;
if (stopping) {
  // Values above only satisfy YAML parsing. Stopping does not connect to the database.
  compose(['down', ...(process.argv.includes('--remove-test-volume') ? ['-v'] : [])], { env });
  process.exit(0);
}
if (reusing) {
  // Inspect only containers in the explicitly validated, self-created E2E project.
  // Credentials remain in memory and are never printed or written into artifacts.
  for (const [service, keys] of [['mysql', ['MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD']],
    ['analysis', ['INTERNAL_API_TOKEN']]]) {
    const id = compose(['ps', '-q', service], { env, stdio: 'pipe' }).toString().trim();
    if (!id) throw new Error('Previous E2E container is missing; start a fresh integration run');
    const values = JSON.parse(execFileSync('docker', ['inspect', '--format', '{{json .Config.Env}}', id], { encoding: 'utf8' }));
    for (const key of keys) env[key] = values.find(value => value.startsWith(`${key}=`))?.slice(key.length + 1) ?? '';
  }
  env.E2E_BASE_URL = prior.baseURL; env.BACKEND_PORT = new URL(prior.baseURL).port;
}
const summary = { ...(prior ?? {}), project: env.COMPOSE_PROJECT_NAME, baseURL: env.E2E_BASE_URL, startedAt: new Date().toISOString() };
delete summary.error;
await writeFile(new URL('run-summary.json', artifacts), JSON.stringify(summary, null, 2));
try {
  if (!reusing) {
  // Verify the normal production-provider deployment's startup without fetching external prices.
  compose(['up', '--build', '-d', '--wait', '--wait-timeout', '240'], { env, offline: false });
  summary.productionHealth = health(env, false);
  await waitReady(env.E2E_BASE_URL);
  const stocks = await (await fetch(`${env.E2E_BASE_URL}/api/stocks`)).json();
  if (!stocks.some(stock => stock.id === '7203') || !stocks.some(stock => stock.id === '9001')) throw new Error('Flyway seed stocks missing');
  summary.seedStocks = stocks.map(stock => stock.id);
  compose(['down'], { env, offline: false }); // Named volume is deliberately retained.
  // Use the existing calendar code to generate an explicitly artificial CSV, never YFinance.
  execFileSync('docker', ['run', '--rm', '--user', '0', '--entrypoint', 'python', '--network', 'none',
    '-e', 'PYTHONPATH=/app',
    '-v', `${join(root, 'tests/e2e/fixtures')}:/fixtures`, 'markov-stock-analyzer-analysis:local', '/fixtures/generate.py'], { env, stdio: 'inherit' });
  compose(['up', '--build', '-d', '--wait', '--wait-timeout', '240'], { env });
  summary.offlineHealth = health(env);
  } else {
    if (process.argv.includes('--rebuild')) compose(['up', '--build', '-d', '--wait', '--wait-timeout', '240'], { env });
    await waitReady(env.E2E_BASE_URL); summary.offlineHealth = health(env);
  }
  const bindings = summary.offlineHealth.filter(service => Object.values(service.ports ?? {}).some(value => Array.isArray(value) && value.length));
  if (bindings.length !== 1 || !bindings[0].name.includes('backend')) throw new Error('Only backend may publish a host port');
  const cli = join(root, 'tests/e2e/node_modules/@playwright/test/cli.js');
  const result = spawnSync(process.execPath, [cli, 'test'], { cwd: join(root, 'tests/e2e'), env, stdio: 'inherit', timeout: 1_800_000 });
  summary.exitCode = result.status; summary.finalHealth = health(env); summary.finishedAt = new Date().toISOString();
  await writeFile(new URL('run-summary.json', artifacts), JSON.stringify(summary, null, 2));
  await assertArtifactsSafe(artifacts, env);
  console.log(`E2E deployment: ${summary.baseURL} (${summary.project}); secrets exist only in process/container environments.`);
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
} catch (error) {
  // Child-process diagnostics can contain environment values; never serialize them into reports.
  summary.error = 'Integration run failed; inspect the isolated project locally.'; summary.finishedAt = new Date().toISOString();
  await writeFile(new URL('run-summary.json', artifacts), JSON.stringify(summary, null, 2));
  await assertArtifactsSafe(artifacts, env);
  console.error('Integration run failed; inspect service health/logs for the isolated project.');
  process.exitCode = 1;
}
