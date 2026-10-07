import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const root = fileURLToPath(new URL('../../', import.meta.url));
export function compose(args, { offline = true, env = process.env, stdio = 'inherit' } = {}) {
  const project = env.COMPOSE_PROJECT_NAME;
  if (!/^msa-e2e-[a-z0-9]+$/.test(project ?? '')) throw new Error('Compose control is limited to this isolated E2E project');
  return execFileSync('docker', ['compose', '-p', project, '-f', 'compose.yaml',
    ...(offline ? ['-f', 'compose.e2e.yaml'] : []), ...args], { cwd: root, env, stdio, timeout: 1_200_000 });
}
export async function waitReady(baseURL = process.env.E2E_BASE_URL, timeout = 180_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const response = await fetch(`${baseURL}/api/stocks`, { signal: AbortSignal.timeout(4000) }); if (response.ok) return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Backend public health did not become ready');
}
export function health(env = process.env, offline = true) {
  const ids = compose(['ps', '-q'], { env, offline, stdio: 'pipe' }).toString().trim().split(/\s+/).filter(Boolean);
  return ids.map(id => JSON.parse(execFileSync('docker', ['inspect', '--format',
    '{"name":{{json .Name}},"status":{{json .State.Status}},"health":{{json .State.Health.Status}},"ports":{{json .NetworkSettings.Ports}}}', id],
    { env, encoding: 'utf8' })));
}
