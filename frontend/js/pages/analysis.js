import { api } from '../api.js';
import { EMPTY, analysisDisplayStatus, backtestLink, conditionRequest, date, dateTime, number, positiveId, priceBasis,
  probability, ratioToPercent, requestIdValue } from '../format.js';
import { el, clearError, forecasts, matrix, populateSelect, provenance, renderPaging, showError, stateBadge, stats, statusController, table, warnings } from '../components.js';
import { destroyCharts, renderSeriesCharts } from '../charts.js';

const $ = id => document.getElementById(id);
const form = $('analysis-form'); const fields = $('analysis-fields');
const status = statusController($('page-status'), [fields]);
let conditions = []; let selected = null; let resultIsSaved = false;

function formValues() {
  return Object.fromEntries(['stockId', 'startDate', 'endDate', 'lowerThreshold', 'upperThreshold', 'name']
    .map(name => [name, form.elements.namedItem(name).value]));
}
function applyCondition(condition, duplicate = false) {
  $('stock').value = String(condition.stockId);
  refreshConditions();
  ['startDate', 'endDate', 'name'].forEach(name => { form.elements.namedItem(name).value = condition[name] ?? ''; });
  $('lower-threshold').value = ratioToPercent(condition.lowerThreshold) ?? '';
  $('upper-threshold').value = ratioToPercent(condition.upperThreshold) ?? '';
  selected = duplicate ? null : condition;
  $('saved-condition').value = duplicate ? '' : String(condition.id);
  if (duplicate) $('condition-name').value = `${condition.name}（コピー）`.slice(0, 100);
  updateConditionMode();
}
function refreshConditions() {
  populateSelect($('saved-condition'), conditions.filter(item => String(item.stockId) === $('stock').value),
    item => `${item.name} / ${item.startDate}〜${item.endDate}`, '新しい条件');
}
function updateConditionMode() {
  $('run-analysis').textContent = selected ? 'この保存条件で分析' : '条件を保存して分析';
  $('condition-hint').textContent = selected ? `保存済みCondition #${selected.id}を使用します。編集すると別の条件として保存します。` : '新しい条件として保存した後、分析を実行します。';
  $('duplicate-condition').disabled = !selected;
}
function markEdited() {
  if (!selected) return;
  selected = null; $('saved-condition').value = ''; updateConditionMode();
}
function renderResult(result) {
  destroyCharts();
  $('result').hidden = false;
  $('series-content').hidden = true; clearError($('series-error'));
  $('result-id').textContent = `分析 #${result.id}`;
  stats($('result-stats'), [['基準日', date(result.asOfDate)], ['現在の状態', ''], ['有効状態数', number(result.sampleCount), 'sampleCount'], ['遷移数', number(result.transitionCount), 'transitionCount']]);
  $('result-stats').children[1].querySelector('dd').append(stateBadge(result.currentState));
  $('result-context').replaceChildren(el('span', `価格基準：${priceBasis(result.priceBasis)}`),
    el('span', `データ取得：${dateTime(result.dataSource?.fetchedAt)}`), el('span', `Condition #${result.conditionId} / Dataset #${result.datasetId}`));
  $('warnings').replaceChildren(warnings(result.warnings, result.predictionStatus !== 'AVAILABLE'));
  $('forecasts').replaceChildren(forecasts(result));
  $('transition-counts').replaceChildren(matrix(result.transitionCounts));
  $('transition-matrix').replaceChildren(matrix(result.transitionMatrix, { probabilities: true }));
  provenance($('provenance'), result);
  $('backtest-link').href = backtestLink(result.conditionId, result.datasetId);
  $('copy-link').href = `analysis.html?${new URLSearchParams({ copyConditionId: positiveId(result.conditionId) })}`;
  status.set(analysisDisplayStatus(result), resultIsSaved ? '保存済みの分析結果です。新規実行・価格の再取得は行っていません。' :
    result.predictionStatus === 'AVAILABLE' ? '分析結果を保存しました。計算根拠とデータ出典を確認できます。' : '結果を保存しました。予測は利用できませんが、遷移回数と価格系列を確認できます。');
}
async function loadSeries(result) {
  $('series-status').textContent = '保存データから価格・状態の系列を読み込んでいます。';
  try {
    const series = await api.series(result.id);
    $('series-basis').textContent = priceBasis(series.priceBasis);
    $('series-content').hidden = false;
    const points = series.points ?? [];
    const renderPage = page => {
      $('series-table').replaceChildren(table(['取引日', '終値', '調整後終値', '日次リターン', '状態'], points.slice(page * 20, (page + 1) * 20).map(point =>
        [date(point.date), point.close ?? EMPTY, point.adjustedClose ?? EMPTY, probability(point.returnValue, 3), stateBadge(point.state)]),
      { caption: '価格・日次リターン・状態：APIが返した保存系列', className: 'data-table' }));
      renderPaging($('series-paging'), { page, size: 20, totalElements: points.length }, renderPage);
    };
    renderPage(0);
    if (points.length) {
      try { renderSeriesCharts($('price-chart'), $('return-chart'), points); }
      catch (error) { showError($('series-error'), error); status.set('partial', '集計結果と価格表を表示しています。チャートの描画は利用できません。'); }
    }
    $('series-status').textContent = `${number(points.length)}取引日の保存系列。リターンや状態を画面で再計算していません。`;
  } catch (error) {
    $('series-status').textContent = '集計結果は保存済みです。系列の表示のみ利用できません。'; showError($('series-error'), error);
    status.set('partial', '保存済みの集計結果を表示しています。価格系列の取得に失敗しました。');
  }
}
async function submit(event) {
  event.preventDefault(); if (status.busy || !form.reportValidity()) return;
  clearError($('page-error')); $('result').hidden = true; destroyCharts();
  status.set('loading', '条件を確認し、分析を実行しています。二重送信はできません。');
  try {
    if (!selected) {
      const saved = await api.createCondition(conditionRequest(formValues()));
      conditions.push(saved); applyCondition(saved);
    }
    const result = await api.analyze({ conditionId: requestIdValue(selected.id) });
    resultIsSaved = false; renderResult(result);
    window.history.replaceState(null, '', `analysis.html?${new URLSearchParams({ id: result.id })}`);
    $('input-panel').hidden = true;
    $('result').scrollIntoView({ behavior: 'instant', block: 'start' });
    await loadSeries(result);
  } catch (error) { showError($('page-error'), error); status.set('error', '結果を確認できませんでした。自動再送は行いません。'); }
}
async function initialize() {
  const params = new URLSearchParams(window.location.search);
  form.addEventListener('submit', submit);
  $('stock').addEventListener('change', () => { selected = null; refreshConditions(); updateConditionMode(); });
  $('saved-condition').addEventListener('change', () => {
    const condition = conditions.find(item => String(item.id) === $('saved-condition').value);
    if (condition) applyCondition(condition); else { selected = null; updateConditionMode(); }
  });
  form.addEventListener('input', event => { if (!['stock', 'saved-condition'].includes(event.target.id)) markEdited(); });
  $('duplicate-condition').addEventListener('click', () => { if (selected) applyCondition(selected, true); });
  if (params.has('id')) {
    $('input-panel').hidden = true; resultIsSaved = true;
    status.set('loading', '保存済みの分析結果を読み込んでいます。');
    try { const result = await api.analysis(positiveId(params.get('id'))); renderResult(result); await loadSeries(result); }
    catch (error) { showError($('page-error'), error); status.set('error', '保存済み結果を取得できませんでした。'); }
    return;
  }
  status.set('loading', '銘柄と保存条件を読み込んでいます。');
  try {
    const [stocks, savedConditions] = await Promise.all([api.stocks(), api.conditions()]); conditions = savedConditions;
    populateSelect($('stock'), stocks, item => `${item.ticker} · ${item.name} (${item.exchange})`);
    if (stocks.length) $('stock').value = String(stocks[0].id);
    refreshConditions();
    const today = new Date(); const isoLocal = value => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
    $('end-date').value = isoLocal(today); today.setFullYear(today.getFullYear() - 1); $('start-date').value = isoLocal(today);
    if (params.has('copyConditionId') || params.has('conditionId')) {
      const id = positiveId(params.get('copyConditionId') ?? params.get('conditionId'));
      const condition = conditions.find(item => String(item.id) === id) ?? await api.condition(id);
      if (!conditions.some(item => String(item.id) === id)) conditions.push(condition);
      applyCondition(condition, params.has('copyConditionId'));
    }
    status.set('idle', stocks.length ? '条件を指定して実行してください。' : '登録銘柄がありません。'); fields.disabled = !stocks.length;
  } catch (error) { showError($('page-error'), error); status.set('error', '条件の入力に必要な情報を読み込めませんでした。'); }
}
export const ready = initialize();
