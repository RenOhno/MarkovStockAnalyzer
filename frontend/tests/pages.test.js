import test from 'node:test';
import assert from 'node:assert/strict';
import { page } from './dom-harness.js';
import { analysis, backtest, condition, historyPage, predictions, series, stocks, unavailable } from './fixtures.js';

function routes(request) {
  const url = new URL(request.path, 'http://fixture.test');
  if (url.pathname === '/api/stocks') return stocks;
  if (url.pathname === '/api/conditions') return [condition];
  if (url.pathname === '/api/conditions/101') return condition;
  if (url.pathname === '/api/analysis/1001') return analysis;
  if (url.pathname === '/api/analysis/1001/series') return series;
  if (url.pathname === '/api/backtest/2001') return backtest;
  if (url.pathname === '/api/backtest/2001/predictions') return { ...predictions, page: Number(url.searchParams.get('page')), size: Number(url.searchParams.get('size')) };
  if (url.pathname === '/api/history') return { ...historyPage, page: Number(url.searchParams.get('page')), size: Number(url.searchParams.get('size')) };
}
test('saved analysis URL uses GET only and renders preserved identities, probabilities and source', async t => {
  const view = await page(t, 'analysis.html', '?id=1001', routes);
  assert.ok(view.requests.every(request => request.method === 'GET'));
  assert.equal(view.document.getElementById('input-panel').hidden, true);
  assert.match(view.document.getElementById('result-context').textContent, /Dataset #501/);
  assert.match(view.document.getElementById('forecasts').textContent, /100\.00%/);
  assert.equal(view.document.getElementById('backtest-link').getAttribute('href'), 'backtest.html?conditionId=101&datasetId=501');
});
test('saved UNAVAILABLE analysis is partial, gives a reason and never invents a forecast', async t => {
  const view = await page(t, 'analysis.html', '?id=1001', request => request.path === '/api/analysis/1001' ? unavailable : routes(request));
  assert.equal(view.document.getElementById('page-status').dataset.state, 'partial');
  assert.match(view.document.getElementById('warnings').textContent, /推定できない/);
  assert.doesNotMatch(view.document.getElementById('forecasts').textContent, /100\.00%/);
});
test('new analysis saves a condition then analyzes it once; repeated submit is locked', async t => {
  let release; let requested;
  const started = new Promise(resolve => { requested = resolve; });
  const view = await page(t, 'analysis.html', '', request => {
    if (request.path === '/api/conditions' && request.method === 'POST') return { ...condition, id: 102, name: request.body.name };
    if (request.path === '/api/analysis' && request.method === 'POST') { requested(); return new Promise(resolve => { release = () => resolve({ ...analysis, conditionId: '102' }); }); }
    return routes(request);
  });
  view.document.getElementById('condition-name').value = 'New condition'; view.submit('analysis-form'); await started;
  assert.equal(view.document.getElementById('analysis-fields').disabled, true); view.submit('analysis-form'); release();
  await view.settle(() => view.document.getElementById('series-status').textContent.includes('取引日の保存系列'));
  const posts = view.requests.filter(request => request.method === 'POST'); assert.equal(posts.length, 2);
  assert.equal(posts[0].path, '/api/conditions'); assert.equal(posts[0].body.lowerThreshold, -0.005); assert.equal(posts[0].body.upperThreshold, 0.005);
  assert.deepEqual(posts[1].body, { conditionId: 102 });
});
test('series version error keeps saved calculation visible and reports requestId', async t => {
  const view = await page(t, 'analysis.html', '?id=1001', request => request.path.endsWith('/series') ? {
    status: 409, body: { code: 'ENGINE_VERSION_UNSUPPORTED', message: 'unsupported', requestId: 'series-error' }
  } : routes(request));
  assert.equal(view.document.getElementById('result').hidden, false); assert.equal(view.document.getElementById('page-status').dataset.state, 'partial');
  assert.match(view.document.getElementById('series-error').textContent, /series-error/);
});
test('saved high-precision condition keeps percent values and is reused without saving a duplicate', async t => {
  const precise = { ...condition, lowerThreshold: -0.0050000001, upperThreshold: 0.0050000001 };
  const view = await page(t, 'analysis.html', '?conditionId=101', request => {
    if (request.path === '/api/conditions') return [precise];
    if (request.path === '/api/analysis' && request.method === 'POST') return analysis;
    return routes(request);
  });
  assert.equal(Number(view.document.getElementById('lower-threshold').value), -0.50000001);
  assert.equal(view.document.getElementById('lower-threshold').getAttribute('step'), 'any');
  view.submit('analysis-form'); await view.settle(() => view.document.getElementById('series-status').textContent.includes('取引日の保存系列'));
  assert.deepEqual(view.requests.filter(request => request.method === 'POST').map(request => request.path), ['/api/analysis']);
});
test('saved backtest only GETs the result/predictions and preserves skipped/null metrics', async t => {
  const view = await page(t, 'backtest.html', '?id=2001', routes);
  assert.ok(view.requests.every(request => request.method === 'GET'));
  assert.match(view.document.getElementById('predictions').textContent, /SKIPPED/); assert.match(view.document.getElementById('predictions').textContent, /ZERO_ROW_UNESTIMATED/);
  assert.match(view.document.getElementById('state-metrics').textContent, /—/);
  assert.equal(view.document.getElementById('page-status').dataset.state, 'partial');
});
test('new backtest sends the exact fixed contract with inherited condition/dataset IDs', async t => {
  const view = await page(t, 'backtest.html', '?conditionId=101&datasetId=501', request =>
    request.path === '/api/backtest' && request.method === 'POST' ? backtest : routes(request));
  view.document.getElementById('test-start').value = '2025-02-20'; view.document.getElementById('test-end').value = '2025-02-21';
  view.submit('backtest-form'); await view.settle(() => view.document.getElementById('predictions').textContent.includes('SKIPPED'));
  assert.deepEqual(view.requests.find(request => request.method === 'POST').body,
    { conditionId: 101, datasetId: 501, testStart: '2025-02-20', testEnd: '2025-02-21', minTrainStates: 60, trainingMode: 'EXPANDING', windowSize: null, horizon: 1 });
  assert.equal(view.requests.some(request => request.path.includes('fetch')), false);
});
test('bare backtest page explains how to select a snapshot without calling the API', async t => {
  const view = await page(t, 'backtest.html', '', routes); assert.equal(view.requests.length, 0);
  assert.equal(view.document.getElementById('choose-context').hidden, false); assert.equal(view.document.getElementById('input-panel').hidden, true);
});
test('history uses saved summaries, matching detail links and external condition names as text', async t => {
  const attack = '<img src=x onerror=alert(1)>';
  const view = await page(t, 'history.html', '?type=ALL&stockId=7203', request => request.path.startsWith('/api/history') ?
    { ...historyPage, items: historyPage.items.map(item => ({ ...item, conditionName: attack })) } : routes(request));
  assert.ok(view.requests.every(request => request.method === 'GET'));
  const result = view.document.getElementById('history-results'); assert.equal(result.querySelector('img'), null); assert.match(result.textContent, /<img/);
  assert.ok(result.querySelector('a[href="analysis.html?id=1001"]')); assert.ok(result.querySelector('a[href="backtest.html?id=2001"]'));
  assert.match(result.textContent, /Toyota Motor/); assert.match(result.textContent, /2025/);
});
test('history paging preserves filters and handles a valid empty page', async t => {
  const view = await page(t, 'history.html', '?type=ANALYSIS&stockId=7203&page=1&size=20', request => request.path.startsWith('/api/history') ?
    { page: 1, size: 20, totalElements: 20, items: [] } : routes(request));
  assert.match(view.requests.find(request => request.path.startsWith('/api/history')).path, /type=ANALYSIS.*page=1.*size=20.*stockId=7203/);
  assert.match(view.document.getElementById('history-paging').textContent, /0〜0件/); assert.match(view.document.getElementById('history-results').textContent, /表示するデータはありません/);
  const previous = view.document.getElementById('history-paging').querySelector('button'); assert.equal(previous.disabled, false);
});
test('invalid history paging is an error and causes no API call', async t => {
  const view = await page(t, 'history.html', '?page=-1', routes); assert.equal(view.requests.length, 0);
  assert.equal(view.document.getElementById('page-status').dataset.state, 'error'); assert.match(view.document.getElementById('page-error').textContent, /不正/);
});
