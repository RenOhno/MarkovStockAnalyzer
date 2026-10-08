import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const files = execFileSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'],
  { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean);
const forbidden = /(^|\/)(\.env(?:\..*)?|db-dumps|node_modules|target|artifacts)(\/|$)|^(data|exports|backups)\/|\.(?:pem|key|dump|sql\.gz)$/i;
const credentials = /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{50,}|AKIA[A-Z0-9]{16})\b/g;
const violations = [];
let dotenv = '';
try { dotenv = await readFile(new URL('../.env', import.meta.url), 'utf8'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const localSecrets = ['MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'INTERNAL_API_TOKEN', 'DB_PASSWORD']
  .flatMap(key => [process.env[key], dotenv.match(new RegExp(`^${key}=(.*)$`, 'm'))?.[1]?.trim().replace(/^['"]|['"]$/g, '')])
  .filter(value => value?.length >= 16).map(value => Buffer.from(value));
for (const file of files) {
  if (file !== '.env.example' && forbidden.test(file)) violations.push(`${file}: private/generated file`);
  if (/\.sql$/i.test(file) && !/^backend\/src\/main\/resources\/db\/migration\/V\d+__[^/]+\.sql$/.test(file))
    violations.push(`${file}: SQL outside canonical Flyway migrations`);
  const content = await readFile(new URL(file, new URL('../', import.meta.url)));
  // Only report file names, never matching credential values. Test-only dummy tokens are not credentials.
  credentials.lastIndex = 0;
  if (credentials.test(content.toString('utf8'))) violations.push(`${file}: credential signature`);
  if (localSecrets.some(secret => content.includes(secret))) violations.push(`${file}: local credential value`);
}
const example = await readFile(new URL('../.env.example', import.meta.url), 'utf8');
for (const key of ['MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'INTERNAL_API_TOKEN']) {
  if (!new RegExp(`^${key}=\\s*$`, 'm').test(example)) violations.push(`.env.example: ${key} must be blank`);
}
const probes = ['.env', '.env.local', 'data/prices.csv', 'data/prices.json', 'exports/prices.json',
  'backups/mysql.sql', 'db-dumps/mysql.dump', 'credentials.key', 'tests/e2e/artifacts/screenshot.png'];
const ignored = execFileSync('git', ['check-ignore', '--stdin'], { cwd: root, input: probes.join('\n') + '\n', encoding: 'utf8' }).trim().split(/\r?\n/);
if (probes.some(path => !ignored.includes(path))) violations.push('Required secret/data/artifact ignore rule is missing');
if (violations.length) { console.error(violations.join('\n')); process.exitCode = 1; }
else console.log(`Repository safety: ${files.length} files checked; secret/data/artifact rules passed (not a full historical secret scan).`);
