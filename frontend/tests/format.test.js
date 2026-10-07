import test from 'node:test';
import assert from 'node:assert/strict';
import { analysisDisplayStatus, backtestLink, conditionRequest, dateTime, detailLink, EMPTY, number, pageParameters, paging, percentToRatio, probability, ratioToPercent, requestIdValue, state } from '../js/format.js';

test('percent input converts ±0.5% to the API ratios and back', () => {
  assert.equal(percentToRatio('-0.5'), -0.005); assert.equal(percentToRatio('0.5'), 0.005);
  assert.equal(ratioToPercent(-0.005), -0.5); assert.equal(ratioToPercent(0.005), 0.5);
  assert.equal(ratioToPercent(-0.0050000001), -0.50000001); assert.equal(percentToRatio(-0.50000001), -0.0050000001);
});
test('null and invalid values are unavailable, while actual zero remains zero', () => {
  for (const value of [null, undefined, '', ' ', NaN, Infinity, true]) { assert.equal(number(value), EMPTY); assert.equal(probability(value), EMPTY); }
  assert.equal(number(0), '0'); assert.equal(probability(0), '0.0%'); assert.equal(state(null), EMPTY); assert.equal(state('__proto__'), EMPTY);
});
test('input validation rejects nonnumeric and unordered thresholds', () => {
  assert.throws(() => percentToRatio('invalid'), RangeError);
  assert.throws(() => conditionRequest({ name: 'test', stockId: '7203', startDate: '2025-01-01', endDate: '2025-02-01', lowerThreshold: 1, upperThreshold: 0.5 }), RangeError);
});
test('condition JSON contains only the frozen computational settings and identity', () => {
  assert.deepEqual(conditionRequest({ name: ' test ', stockId: '7203', startDate: '2025-01-06', endDate: '2025-02-28', lowerThreshold: -0.5, upperThreshold: 0.5 }),
    { name: 'test', stockId: '7203', startDate: '2025-01-06', endDate: '2025-02-28', lowerThreshold: -0.005, upperThreshold: 0.005,
      stateCount: 3, estimator: 'MLE_STRICT', windowMode: 'FULL', windowSize: null, horizons: [1, 3, 5, 10] });
});
test('paging knows first, middle, last and empty pages including exact boundaries', () => {
  assert.equal(paging(0, 20, 41).hasPrevious, false); assert.equal(paging(0, 20, 41).hasNext, true);
  assert.equal(paging(1, 20, 41).hasNext, true); assert.equal(paging(2, 20, 41).to, 41); assert.equal(paging(2, 20, 41).hasNext, false);
  for (const [page, total] of [[0, 0], [1, 20], [3, 10]]) { assert.equal(paging(page, 20, total).from, 0); assert.equal(paging(page, 20, total).to, 0); }
});
test('paging rejects invalid page and size without silently changing the query', () => {
  for (const query of ['page=-1', 'size=0', 'size=101', 'page=1.5', 'page=abc']) assert.throws(() => pageParameters(new URLSearchParams(query)), RangeError);
  assert.deepEqual(pageParameters(new URLSearchParams()), { page: 0, size: 20 });
});
test('history and backtest links retain identities and reject URL injection', () => {
  assert.equal(detailLink('ANALYSIS', '1001'), 'analysis.html?id=1001'); assert.equal(detailLink('BACKTEST', '2001'), 'backtest.html?id=2001');
  assert.equal(detailLink('__proto__', '1'), null); assert.throws(() => detailLink('ANALYSIS', 'javascript:alert(1)'), RangeError);
  assert.equal(backtestLink('101', '501'), 'backtest.html?conditionId=101&datasetId=501');
  assert.throws(() => requestIdValue('9007199254740993'), RangeError);
});
test('AVAILABLE and UNAVAILABLE are separate complete and partial states', () => {
  assert.equal(analysisDisplayStatus({ predictionStatus: 'AVAILABLE' }), 'success'); assert.equal(analysisDisplayStatus({ predictionStatus: 'UNAVAILABLE' }), 'partial');
});
test('timestamps are displayed in Japan time and missing timestamps stay empty', () => {
  assert.match(dateTime('2025-01-01T00:00:00Z'), /09:00:00/); assert.equal(dateTime(null), EMPTY); assert.equal(dateTime('invalid'), EMPTY);
});
