import { readdir, readFile } from 'node:fs/promises';

const directory = new URL('../backend/target/surefire-reports/', import.meta.url);
const files = (await readdir(directory)).filter(name => /^TEST-.*\.xml$/.test(name));
if (!files.length || !files.some(name => name.includes('MySqlPersistenceTests'))
    || !files.some(name => name.includes('MySqlRepositoryAdapterTests'))) throw new Error('Real MySQL test reports are required');
const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
for (const file of files) {
  const xml = await readFile(new URL(file, directory), 'utf8');
  const suite = xml.match(/<testsuite\b[^>]*>/)?.[0];
  if (!suite) throw new Error(`Invalid test report: ${file}`);
  for (const field of Object.keys(totals)) {
    const value = suite.match(new RegExp(`\\b${field}="(\\d+)"`));
    if (!value) throw new Error(`Missing test count: ${file}`);
    totals[field] += Number(value[1]);
  }
}
console.log(JSON.stringify(totals));
if (!totals.tests || totals.failures || totals.errors || totals.skipped) throw new Error('Every Java/MySQL test must pass without skips');
