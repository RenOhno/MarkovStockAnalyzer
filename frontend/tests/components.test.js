import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import vm from 'node:vm';
import { parseHTML } from 'linkedom';
import { errorBox, forecasts, matrix, provenance, renderPaging, statusController, table, warnings } from '../js/components.js';
import { destroyCharts, renderSeriesCharts } from '../js/charts.js';
import { analysis, unavailable, series } from './fixtures.js';

function dom(t) {
  const original = globalThis.document; const { document } = parseHTML('<html><body></body></html>'); globalThis.document = document;
  t.after(() => { globalThis.document = original; }); return document;
}
test('AVAILABLE forecasts show all four horizons and semantic column/row headers', t => {
  dom(t); const view = forecasts(analysis); assert.match(view.textContent, /1営業日後/); assert.match(view.textContent, /10営業日後/);
  assert.match(view.textContent, /100\.00%/); assert.equal(view.querySelectorAll('tbody tr').length, 4);
  assert.equal(view.querySelector('thead th').scope, 'col'); assert.equal(view.querySelector('tbody th').scope, 'row');
});
test('UNAVAILABLE ignores stale probabilities and explains zero-row states', t => {
  dom(t); const view = forecasts({ ...unavailable, forecasts: analysis.forecasts });
  assert.doesNotMatch(view.textContent, /100|0\.00%/); assert.equal(view.querySelectorAll('td').length, 12); assert.match(view.textContent, /推定不可/);
  const reason = warnings(unavailable.warnings, true); assert.match(reason.textContent, /横ばい FLAT/); assert.match(reason.textContent, /下落 DOWN/); assert.match(reason.textContent, /遷移が観測されていません/);
  assert.match(matrix(unavailable.transitionMatrix, { probabilities: true }).textContent, /未推定/);
});
test('external HTML-shaped values remain text in errors, tables, warnings and dataSource', t => {
  const document = dom(t); const attack = '<img src=x onerror="alert(1)"><script>alert(2)</script>';
  const views = [errorBox({ message: attack, code: attack, requestId: attack }), table(['name'], [[attack]]), warnings([{ code: attack }])];
  const metadata = document.createElement('div'); provenance(metadata, { ...analysis, dataSource: { ...analysis.dataSource, provider: attack } }); views.push(metadata);
  for (const view of views) { assert.equal(view.querySelector('img,script'), null); assert.ok(view.textContent.includes(attack)); }
});
test('loading locks controls and success, partial and error restore their previous disabled state', t => {
  const document = dom(t); const target = document.createElement('div'); const control = { disabled: false }; const controller = statusController(target, [control]);
  for (const ending of ['success', 'partial', 'error']) {
    controller.set('loading', 'loading'); assert.equal(control.disabled, true); assert.equal(controller.busy, true);
    controller.set(ending, ending); assert.equal(control.disabled, false); assert.equal(controller.busy, false); assert.equal(target.dataset.state, ending);
  }
});
test('paging exposes disabled buttons for first/last and keeps empty pages honest', t => {
  const document = dom(t); const target = document.createElement('div'); const clicks = [];
  let controls = renderPaging(target, { page: 0, size: 20, totalElements: 41 }, page => clicks.push(page));
  assert.equal(controls.previous.disabled, true); controls.next.click(); assert.deepEqual(clicks, [1]);
  controls = renderPaging(target, { page: 2, size: 20, totalElements: 41 }, () => {}); assert.equal(controls.next.disabled, true);
  renderPaging(target, { page: 1, size: 20, totalElements: 20 }, () => {}); assert.match(target.textContent, /0〜0件/);
});
test('existing Chart instances are destroyed before redraw and on cleanup', t => {
  const document = dom(t); let destroyed = 0; let created = 0; const original = globalThis.Chart;
  globalThis.Chart = class { constructor() { created++; } destroy() { destroyed++; } };
  t.after(() => { destroyCharts(); globalThis.Chart = original; });
  const first = document.createElement('canvas'); const second = document.createElement('canvas');
  renderSeriesCharts(first, second, series.points); renderSeriesCharts(first, second, series.points);
  assert.equal(created, 4); assert.equal(destroyed, 2); destroyCharts(); assert.equal(destroyed, 4);
});
test('four pages have Japanese language, viewport, labeled controls and one current navigation item', async () => {
  for (const filename of ['index', 'analysis', 'backtest', 'history']) {
    const { document } = parseHTML(await readFile(new URL(`../${filename}.html`, import.meta.url), 'utf8'));
    assert.equal(document.documentElement.lang, 'ja'); assert.ok(document.querySelector('meta[name="viewport"]'));
    assert.equal(document.querySelectorAll('h1').length, 1); assert.equal(document.querySelectorAll('nav [aria-current="page"]').length, 1);
    for (const control of document.querySelectorAll('input,select')) assert.ok(document.querySelector(`label[for="${control.id}"]`), `${filename}: ${control.id}`);
    if (filename !== 'index') assert.ok(document.querySelector('[aria-live="polite"]'));
  }
});
test('application sources contain no HTML sinks and CSS includes narrow-screen/focus/reduced-motion support', async () => {
  for (const path of ['api', 'format', 'components', 'charts', 'pages/analysis', 'pages/backtest', 'pages/history']) {
    const source = await readFile(new URL(`../js/${path}.js`, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /(?:innerHTML|outerHTML)\s*=|insertAdjacentHTML\s*\(|document\.write\s*\(/);
  }
  const css = await readFile(new URL('../css/style.css', import.meta.url), 'utf8');
  assert.match(css, /max-width:600px/); assert.match(css, /focus-visible/); assert.match(css, /prefers-reduced-motion/);
});
test('vendored Chart.js version and checksum match the pinned manifest', async () => {
  const manifest = JSON.parse(await readFile(new URL('../vendor/manifest.json', import.meta.url), 'utf8'));
  const source = await readFile(new URL('../vendor/chart.umd.min.js', import.meta.url), 'utf8');
  assert.equal(manifest.version, '4.5.1'); assert.equal(createHash('sha256').update(source).digest('hex'), manifest.sha256);
  const context = {}; vm.runInNewContext(source, context); assert.equal(context.Chart.version, '4.5.1');
  assert.ok((await readFile(new URL('../vendor/LICENSE.chartjs.md', import.meta.url), 'utf8')).includes('MIT'));
});
