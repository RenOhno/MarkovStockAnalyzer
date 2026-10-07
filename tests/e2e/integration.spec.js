import { test, expect } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { compose, health, waitReady } from './compose-control.mjs';

test.describe.configure({ mode: 'serial' });
let savedAnalysis; let savedPartial; let savedBacktest;
const screenshots = new URL('./artifacts/screenshots/', import.meta.url);
test.beforeAll(async () => { await mkdir(screenshots, { recursive: true }); });

async function screenshot(page, name) { await page.screenshot({ path: fileURLToPath(new URL(`${name}.png`, screenshots)), fullPage: true }); }
function monitor(page, { expectedHTTP = [] } = {}) {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' && !expectedHTTP.some(status => message.text().includes(String(status)))) errors.push(message.text());
  });
  page.on('response', response => {
    if (response.status() === 404) errors.push(`404: ${new URL(response.url()).pathname}`);
    if (response.status() >= 400 && /\.(js|css|svg)$/.test(new URL(response.url()).pathname)) errors.push(`Asset error: ${response.status()}`);
  });
  return errors;
}
function persistedPayload(actual, expected, path = '$') {
  // Design 10.4 explicitly permits absolute error 1e-9 at DB/API boundaries.
  // IDs, dates, counts, nulls, array sizes and object keys still match exactly.
  if (typeof expected === 'number' && !Number.isInteger(expected)) {
    expect(typeof actual, path).toBe('number'); expect(Number.isFinite(actual), path).toBe(true);
    expect(Math.abs(actual - expected), path).toBeLessThanOrEqual(1e-9); return;
  }
  if (Array.isArray(expected)) {
    expect(Array.isArray(actual), path).toBe(true); expect(actual.length, path).toBe(expected.length);
    expected.forEach((value, index) => persistedPayload(actual[index], value, `${path}[${index}]`)); return;
  }
  if (expected !== null && typeof expected === 'object') {
    expect(Object.keys(actual).sort(), path).toEqual(Object.keys(expected).sort());
    Object.entries(expected).forEach(([key, value]) => persistedPayload(actual[key], value, `${path}.${key}`)); return;
  }
  expect(actual, path).toEqual(expected);
}
async function fillAnalysis(page, name, end = '2025-06-30', start = '2025-01-06') {
  await page.goto('/analysis.html');
  await expect(page.locator('#analysis-fields')).toBeEnabled();
  await page.getByLabel('銘柄', { exact: true }).selectOption('7203');
  await page.getByLabel('開始日', { exact: true }).fill(start);
  await page.getByLabel('終了日', { exact: true }).fill(end);
  await page.getByLabel('下側閾値（%）').fill('-0.5');
  await page.getByLabel('上側閾値（%）').fill('0.5');
  await page.getByLabel('条件名', { exact: true }).fill(name);
}
async function newAnalysis(page, name, end) {
  await fillAnalysis(page, name, end);
  const response = page.waitForResponse(response => new URL(response.url()).pathname === '/api/analysis' && response.request().method() === 'POST');
  await page.getByRole('button', { name: '条件を保存して分析', exact: true }).click();
  const received = await response; expect(received.status()).toBe(201);
  const result = await received.json();
  await expect(page.locator('#result')).toBeVisible();
  await expect(page.locator('#series-content')).toBeVisible();
  await expect(page.locator('#series-status')).toContainText('取引日の保存系列');
  return result;
}

test('A: four real pages, modules and local Chart.js load without console/asset errors', async ({ page }) => {
  const errors = monitor(page);
  for (const name of ['index', 'analysis', 'backtest', 'history']) {
    const response = await page.goto(`/${name}.html`); expect(response.status()).toBe(200);
    await expect(page.locator('h1')).toBeVisible();
    if (name === 'analysis') { await expect(page.locator('#analysis-fields')).toBeEnabled(); expect(await page.evaluate(() => Chart.version)).toBe('4.5.1'); }
    if (name === 'history') await expect(page.locator('#page-status')).toHaveAttribute('data-state', 'success');
  }
  await page.goto('/index.html'); await page.getByRole('link', { name: '分析を始める' }).click();
  await expect(page).toHaveURL(/analysis\.html$/); await expect(page.locator('#analysis-fields')).toBeEnabled();
  expect(errors).toEqual([]);
});
test('B: real condition save → fetch → analyze → MySQL result, charts and one submission', async ({ page }) => {
  const errors = monitor(page); const posts = [];
  page.on('request', request => { if (request.method() === 'POST') posts.push(new URL(request.url()).pathname); });
  savedAnalysis = await newAnalysis(page, 'E2E / success');
  expect(savedAnalysis.predictionStatus).toBe('AVAILABLE'); expect(savedAnalysis.dataSource.provider).toBe('FIXTURE');
  await expect(page.locator('#page-status')).toHaveAttribute('data-state', 'success');
  await expect(page.locator('#result-stats')).toContainText(savedAnalysis.currentState);
  await expect(page.locator('#transition-counts tbody tr')).toHaveCount(3); await expect(page.locator('#transition-matrix tbody tr')).toHaveCount(3);
  for (const horizon of [1, 3, 5, 10]) await expect(page.locator('#forecasts')).toContainText(`${horizon}営業日後`);
  expect(await page.evaluate(() => [Chart.getChart(document.querySelector('#price-chart'))?.data.datasets[0].data.length,
    Chart.getChart(document.querySelector('#return-chart'))?.data.datasets[0].data.length].every(count => count > 30))).toBe(true);
  expect(posts).toEqual(['/api/conditions', '/api/analysis']);
  await screenshot(page, 'analysis-success'); expect(errors).toEqual([]);
});
test('C: zero-row analysis is a real partial result with reasons and no invented probabilities', async ({ page }) => {
  const errors = monitor(page); savedPartial = await newAnalysis(page, 'E2E / partial', '2025-03-31');
  expect(savedPartial.predictionStatus).toBe('UNAVAILABLE'); expect(savedPartial.forecasts).toEqual([]);
  await expect(page.locator('#page-status')).toHaveAttribute('data-state', 'partial');
  await expect(page.locator('#warnings')).toContainText('遷移が観測されていません');
  await expect(page.locator('#warnings')).toContainText('横ばい FLAT');
  await expect(page.locator('#forecasts')).not.toContainText('%'); await expect(page.locator('#forecasts')).toContainText('推定不可');
  await expect(page.locator('#transition-matrix')).toContainText('未推定');
  await screenshot(page, 'analysis-partial'); expect(errors).toEqual([]);
});
test('D: saved analysis is read with GET only and keeps its original data source', async ({ page, request }) => {
  const errors = monitor(page); const posts = [];
  page.on('request', value => { if (value.method() === 'POST') posts.push(value.url()); });
  await page.goto(`/analysis.html?id=${savedAnalysis.id}`); await expect(page.locator('#series-content')).toBeVisible();
  await expect(page.locator('#page-status')).toContainText('保存済み');
  const restored = await (await request.get(`/api/analysis/${savedAnalysis.id}`)).json();
  persistedPayload(restored, savedAnalysis);
  savedAnalysis = restored; // The restart test compares GET → GET exactly, without tolerance.
  expect(posts).toEqual([]); expect(errors).toEqual([]);
});
test('E: same condition/dataset backtest, scored/skipped days, metrics and paging', async ({ page }) => {
  const errors = monitor(page);
  await page.goto(`/analysis.html?id=${savedAnalysis.id}`); await expect(page.locator('#series-content')).toBeVisible();
  await page.locator('#backtest-link').click();
  await expect(page).toHaveURL(new RegExp(`conditionId=${savedAnalysis.conditionId}&datasetId=${savedAnalysis.datasetId}`));
  await expect(page.locator('#backtest-fields')).toBeEnabled();
  await page.getByLabel('評価対象の開始日').fill('2025-02-20'); await page.getByLabel('評価対象の終了日').fill('2025-06-30');
  await page.getByLabel('最小学習状態数').fill('30');
  const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/backtest' && value.request().method() === 'POST');
  await page.getByRole('button', { name: 'このデータ版で検証する' }).click();
  const received = await response; expect(received.status()).toBe(201); savedBacktest = await received.json();
  expect(savedBacktest.conditionId).toBe(savedAnalysis.conditionId); expect(savedBacktest.datasetId).toBe(savedAnalysis.datasetId);
  expect(savedBacktest.predictedCount).toBeGreaterThan(0); expect(savedBacktest.skippedCount).toBeGreaterThan(0);
  expect(savedBacktest.eligibleCount).toBeGreaterThan(20); expect(savedBacktest.coverage).toBeGreaterThan(0); expect(savedBacktest.coverage).toBeLessThan(1);
  await expect(page.locator('#metric-stats')).toContainText('正解率'); await expect(page.locator('#result-stats')).toContainText('カバレッジ');
  await expect(page.locator('#confusion-matrix tbody tr')).toHaveCount(3); await expect(page.locator('#predictions')).toContainText('SKIPPED');
  await page.locator('#prediction-paging').getByRole('button', { name: '次へ →' }).click();
  await expect(page.locator('#predictions')).toContainText('SCORED');
  await expect(page.locator('#prediction-paging')).toContainText('2ページ目');
  await screenshot(page, 'backtest-result'); expect(errors).toEqual([]);
});
test('F: history shows both types, filtering, paging and GET-only detail links', async ({ page, request }) => {
  const errors = monitor(page);
  await page.goto('/history.html?size=100'); await expect(page.locator('#history-results')).toContainText('ANALYSIS');
  await expect(page.locator('#history-results')).toContainText('BACKTEST');
  await screenshot(page, 'history');
  for (let i = 0; i < 20; i++) {
    const response = await request.post('/api/analysis', { data: { conditionId: Number(savedAnalysis.conditionId), datasetId: Number(savedAnalysis.datasetId) } });
    expect(response.status()).toBe(201);
  }
  await page.goto('/history.html'); await expect(page.locator('#history-results')).toContainText('ANALYSIS');
  await page.locator('#history-paging').getByRole('button', { name: '次へ →' }).click(); await expect(page.locator('#history-paging')).toContainText('2ページ目');
  await page.getByLabel('種類').selectOption('BACKTEST'); await page.getByLabel('銘柄', { exact: true }).selectOption('7203');
  await page.getByRole('button', { name: '絞り込む' }).click(); await expect(page.locator('#history-results')).toContainText('BACKTEST');
  await expect(page.locator('#history-results')).not.toContainText('ANALYSIS'); await expect(page.locator('#history-paging')).toContainText('1ページ目');
  await screenshot(page, 'history-filtered');
  await page.locator(`a[href="backtest.html?id=${savedBacktest.id}"]`).click(); await expect(page.locator('#predictions')).toBeVisible();
  expect(errors).toEqual([]);
});
test('G: actual backend restart preserves history and exact saved result payloads', async ({ page, request }) => {
  compose(['restart', 'backend']); await waitReady();
  expect(await (await request.get(`/api/analysis/${savedAnalysis.id}`)).json()).toEqual(savedAnalysis);
  expect(await (await request.get(`/api/backtest/${savedBacktest.id}`)).json()).toEqual(savedBacktest);
  const history = await (await request.get('/api/history?type=ALL&page=0&size=100')).json(); expect(history.totalElements).toBeGreaterThanOrEqual(23);
  await page.goto(`/analysis.html?id=${savedAnalysis.id}`); await expect(page.locator('#series-content')).toBeVisible();
  await page.goto('/history.html?type=BACKTEST'); await expect(page.locator('#history-results')).toContainText('BACKTEST');
});
test('H: mobile pages and result tables stay within the viewport; charts remain available', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 }); const errors = monitor(page);
  for (const path of ['/index.html', `/analysis.html?id=${savedAnalysis.id}`, `/backtest.html?id=${savedBacktest.id}`, '/history.html']) {
    await page.goto(path); await expect(page.locator('h1')).toBeVisible();
    if (path.includes('analysis.html')) await expect(page.locator('#series-content')).toBeVisible();
    if (path.includes('backtest.html')) await expect(page.locator('#predictions')).toBeVisible();
    if (path.includes('history.html')) await expect(page.locator('#page-status')).toHaveAttribute('data-state', 'success');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  }
  await screenshot(page, 'history-mobile'); expect(errors).toEqual([]);
});
test('I: actual invalid condition produces public 422 and a visible requestId', async ({ page }) => {
  monitor(page, { expectedHTTP: [422] });
  await fillAnalysis(page, 'E2E / invalid', '2025-06-30', '2019-01-06');
  const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/conditions' && value.request().method() === 'POST');
  await page.getByRole('button', { name: '条件を保存して分析', exact: true }).click();
  const received = await response; expect(received.status()).toBe(422); const body = await received.json();
  await expect(page.locator('#page-error')).toContainText(body.code); await expect(page.locator('#page-error')).toContainText(body.requestId);
  await expect(page.locator('#page-status')).toHaveAttribute('data-state', 'error');
});
test('J: stopped FastAPI returns 503 through the real Java connection failure mapping', async ({ page }) => {
  const errors = monitor(page, { expectedHTTP: [503] }); compose(['stop', 'analysis']);
  try {
    await fillAnalysis(page, 'E2E / unavailable');
    const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/analysis' && value.request().method() === 'POST');
    await page.getByRole('button', { name: '条件を保存して分析', exact: true }).click();
    const received = await response; expect(received.status()).toBe(503); const body = await received.json();
    expect(body.code).toBe('ANALYSIS_SERVICE_UNAVAILABLE'); await expect(page.locator('#page-error')).toContainText(body.requestId);
  } finally { compose(['start', 'analysis']); compose(['up', '-d', '--wait', '--wait-timeout', '120']); }
  expect(errors).toEqual([]);
});
test('K: real delayed fixture causes provider timeout 504 with requestId', async ({ page }) => {
  const errors = monitor(page, { expectedHTTP: [504] });
  const delayed = { ...process.env, FIXTURE_FETCH_DELAY_SECONDS: '13' };
  compose(['up', '-d', '--force-recreate', '--no-deps', '--wait', '--wait-timeout', '120', 'analysis'], { env: delayed });
  compose(['restart', 'backend']); await waitReady();
  try {
    await fillAnalysis(page, 'E2E / provider timeout');
    const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/analysis' && value.request().method() === 'POST', { timeout: 30_000 });
    await page.getByRole('button', { name: '条件を保存して分析', exact: true }).click();
    const received = await response; expect(received.status()).toBe(504); const body = await received.json();
    expect(body.code).toBe('PROVIDER_TIMEOUT'); await expect(page.locator('#page-error')).toContainText(body.requestId);
  } finally {
    compose(['up', '-d', '--force-recreate', '--no-deps', '--wait', '--wait-timeout', '120', 'analysis']);
    compose(['restart', 'backend']); await waitReady();
  }
  expect(errors).toEqual([]);
});
test('L: browser 35-second POST timeout aborts waiting, blocks duplicate submit and never retries', async ({ page }) => {
  await fillAnalysis(page, 'E2E / browser timeout'); await page.clock.install();
  let attempts = 0;
  await page.route('**/api/analysis', async route => { attempts++; /* Hold the POST at the browser boundary; no server response is fabricated. */ });
  await page.getByRole('button', { name: '条件を保存して分析', exact: true }).click();
  await expect.poll(() => attempts).toBe(1); await expect(page.locator('#run-analysis')).toBeDisabled();
  await page.locator('#analysis-form').evaluate(form => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
  await page.clock.fastForward(35_001);
  await expect(page.locator('#page-error')).toContainText('BROWSER_TIMEOUT');
  await expect(page.locator('#page-error')).toContainText('サーバー処理が中止されたとは限りません');
  await page.clock.fastForward(70_000); expect(attempts).toBe(1);
  await page.unrouteAll({ behavior: 'ignoreErrors' });
});
test.afterAll(async () => { await writeFile(new URL('./artifacts/saved-identities.json', import.meta.url), JSON.stringify({
  analysisId: savedAnalysis?.id, partialId: savedPartial?.id, backtestId: savedBacktest?.id,
  conditionId: savedAnalysis?.conditionId, datasetId: savedAnalysis?.datasetId, health: health()
}, null, 2)); });
