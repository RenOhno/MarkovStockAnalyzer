import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const root = new URL('../', import.meta.url);
const chart = new URL('node_modules/chart.js/', root);
const metadata = JSON.parse(await readFile(new URL('package.json', chart), 'utf8'));
if (metadata.version !== '4.5.1') throw new Error('Chart.js version must be 4.5.1');
await mkdir(new URL('vendor/', root), { recursive: true });
const source = (await readFile(new URL('dist/chart.umd.min.js', chart), 'utf8'))
  .replace(/^\/\/\# sourceMappingURL=.*$/gm, '');
await writeFile(new URL('vendor/chart.umd.min.js', root), source);
const license = await readFile(new URL('LICENSE.md', chart), 'utf8');
const colorLicense = await readFile(new URL('node_modules/@kurkle/color/LICENSE.md', root), 'utf8');
await writeFile(new URL('vendor/LICENSE.chartjs.md', root),
  `# Chart.js ${metadata.version}\n\nSource: https://github.com/chartjs/Chart.js/tree/v${metadata.version}\n\n${license}\n\n# Bundled @kurkle/color\n\n${colorLicense}`);
await writeFile(new URL('vendor/manifest.json', root), JSON.stringify({
  package: 'chart.js', version: metadata.version,
  source: `https://registry.npmjs.org/chart.js/-/chart.js-${metadata.version}.tgz`,
  sha256: createHash('sha256').update(source).digest('hex')
}, null, 2) + '\n');
console.log(`Vendored Chart.js ${metadata.version}`);
