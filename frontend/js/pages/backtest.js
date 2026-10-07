import { api } from '../api.js';
import { EMPTY, STATES, date, dateTime, number, pageParameters, positiveId, probability, requestIdValue, state } from '../format.js';
import { clearError, el, matrix, provenance, renderPaging, showError, stateBadge, stats, statusController, table } from '../components.js';

const $ = id => document.getElementById(id);
const form = $('backtest-form'); const fields = $('backtest-fields');
const status = statusController($('page-status'), [fields]);
let conditionId; let datasetId; let condition; let result; let predictionsBusy = false; let predictionAbort;
let size = 20;
function renderResult(value) {
  result = value; $('result').hidden = false; $('input-panel').hidden = true;
  $('result-id').textContent = `バックテスト #${value.id}`;
  stats($('result-stats'), [['評価候補', number(value.eligibleCount), 'eligibleCount'], ['予測できた日', number(value.predictedCount), 'predictedCount'],
    ['正解', number(value.correctCount), 'correctCount'], ['スキップ', number(value.skippedCount), 'skippedCount'], ['カバレッジ', probability(value.coverage), '予測できた日の割合']]);
  $('result-context').replaceChildren(el('span', `対象日：${date(value.testStart)} 〜 ${date(value.testEnd)}`),
    el('span', `Condition #${value.conditionId} / Dataset #${value.datasetId}`), el('span', `データ取得：${dateTime(value.dataSource?.fetchedAt)}`));
  const metrics = value.metrics ?? {};
  stats($('metric-stats'), [['正解率', probability(metrics.accuracy), '予測できた日の正解割合'], ['Brier score', number(metrics.brierScore, 4), '確率予測の誤差 · 小さいほど良い'],
    ['Log loss', number(metrics.logLoss, 4), '確率予測の損失 · 小さいほど良い'], ['同率の最大確率', number(metrics.tieCount), 'tieCount']]);
  $('state-metrics').replaceChildren(table(['状態', 'precision', 'recall'], STATES.map((label, index) => [stateBadge(label), probability(metrics.precision?.[index]), probability(metrics.recall?.[index])]),
    { caption: '状態別のprecision・recall' }));
  $('baseline-metrics').replaceChildren(table(['比較する基準モデル', '正解率'], [['最頻状態モデル', probability(metrics.majorityAccuracy)], ['持続モデル（直前と同じ状態）', probability(metrics.persistenceAccuracy)]],
    { caption: '基準モデルとの比較：同じ評価対象日で確認します' }));
  $('confusion-matrix').replaceChildren(matrix(metrics.confusionMatrix, { confusion: true }));
  const skipReasons = Object.entries(metrics.skipReasons ?? {});
  $('skip-reasons').replaceChildren(...(skipReasons.length ? [table(['スキップ理由', '件数'], skipReasons.map(([code, count]) => [code, number(count)]), { caption: '予測できなかった理由' })] : []));
  provenance($('provenance'), value);
  $('result-analysis-link').href = `analysis.html?${new URLSearchParams({ conditionId: positiveId(value.conditionId) })}`;
  status.set(value.skippedCount > 0 ? 'partial' : 'success', value.skippedCount > 0 ? '一部の対象日をスキップしました。カバレッジとスキップ理由を確認してください。' : '保存済みの検証結果を表示しています。');
}
async function loadPredictions(page) {
  if (!result || predictionsBusy) return;
  predictionsBusy = true; predictionAbort?.abort(); predictionAbort = new AbortController();
  $('prediction-size').disabled = true; $('prediction-paging').querySelectorAll('button').forEach(button => { button.disabled = true; });
  $('prediction-status').textContent = '予測明細を読み込んでいます。'; $('predictions').replaceChildren(); clearError($('prediction-error'));
  try {
    const response = await api.predictions(result.id, page, size, { signal: predictionAbort.signal });
    $('predictions').replaceChildren(table(['起点日', '対象日', '学習開始', '学習最終日', '上昇確率', '横ばい確率', '下落確率', '予測', '実際', '最頻状態', '持続', '状態', '判定', 'skipCode'],
      response.items.map(item => [date(item.originDate), date(item.targetDate), date(item.trainStart), date(item.trainEnd),
        ...STATES.map((_, index) => probability(item.probabilities?.[index], 2)), stateBadge(item.predictedState), stateBadge(item.actualState),
        stateBadge(item.majorityState), stateBadge(item.persistenceState), el('span', item.status === 'SKIPPED' ? 'スキップ SKIPPED' : '評価済み SCORED', 'badge'),
        item.status === 'SKIPPED' ? '評価対象外' : item.actualState === item.predictedState ? '正解' : '不正解', item.skipCode ?? EMPTY]),
      { caption: '対象日別の予測明細：取得したページを表示', className: 'data-table' }));
    renderPaging($('prediction-paging'), response, loadPredictions);
    $('prediction-status').textContent = 'SCOREDは予測できた日、SKIPPEDは予測できず評価から除外した日です。';
  } catch (error) {
    showError($('prediction-error'), error); $('prediction-status').textContent = '集計結果は保存済みです。明細の読込に失敗しました。';
    const retry = el('button', '明細を再取得', 'button secondary'); retry.type = 'button'; retry.addEventListener('click', () => loadPredictions(page));
    $('prediction-paging').replaceChildren(retry);
  } finally { predictionsBusy = false; $('prediction-size').disabled = false; }
}
async function submit(event) {
  event.preventDefault(); if (status.busy || !form.reportValidity()) return;
  clearError($('page-error')); $('result').hidden = true;
  status.set('loading', '保存データを使って検証しています。二重送信はできません。');
  try {
    const testStart = $('test-start').value; const testEnd = $('test-end').value; const minTrainStates = Number($('min-train-states').value);
    if (testStart > testEnd || (condition && (testStart < condition.startDate || testEnd > condition.endDate))) throw new RangeError('評価期間は条件期間の範囲内で、開始日 ≤ 終了日にしてください。');
    if (!Number.isInteger(minTrainStates) || minTrainStates < 30) throw new RangeError('最小学習状態数は30以上の整数で指定してください。');
    const value = await api.backtest({ conditionId: requestIdValue(conditionId), datasetId: requestIdValue(datasetId), testStart, testEnd,
      minTrainStates, trainingMode: 'EXPANDING', windowSize: null, horizon: 1 });
    renderResult(value); window.history.replaceState(null, '', `backtest.html?${new URLSearchParams({ id: value.id })}`);
    $('result').scrollIntoView({ behavior: 'instant', block: 'start' }); await loadPredictions(0);
  } catch (error) { showError($('page-error'), error); status.set('error', '検証結果を確認できませんでした。自動再送は行いません。'); }
}
async function initialize() {
  const params = new URLSearchParams(window.location.search);
  form.addEventListener('submit', submit);
  $('prediction-size').addEventListener('change', () => { size = Number($('prediction-size').value); loadPredictions(0); });
  try {
    const pageOptions = pageParameters(params); size = pageOptions.size;
    if (!$('prediction-size').querySelector(`option[value="${size}"]`) && ![20, 50, 100].includes(size)) { const option = el('option', size); option.value = size; $('prediction-size').append(option); }
    $('prediction-size').value = String(size);
    if (params.has('id')) {
      $('input-panel').hidden = true; status.set('loading', '保存済みの検証結果を読み込んでいます。');
      renderResult(await api.backtestResult(positiveId(params.get('id')))); await loadPredictions(pageOptions.page); return;
    }
    if (!params.has('conditionId') && !params.has('datasetId')) {
      $('input-panel').hidden = true; $('choose-context').hidden = false;
      status.set('idle', '分析結果から、検証する条件とデータを選んでください。'); return;
    }
    conditionId = positiveId(params.get('conditionId')); datasetId = positiveId(params.get('datasetId'));
    $('input-context').textContent = `Condition #${conditionId} / 入力データ版 Dataset #${datasetId}。この保存snapshotを固定して使用し、最新価格へ自動置換しません。`;
    $('back-to-analysis').href = `analysis.html?${new URLSearchParams({ conditionId })}`;
    status.set('loading', '保存条件を確認しています。'); condition = await api.condition(conditionId);
    $('condition-range').textContent = `${condition.name} · 条件期間 ${date(condition.startDate)} 〜 ${date(condition.endDate)}`;
    for (const id of ['test-start', 'test-end']) { $(id).min = condition.startDate; $(id).max = condition.endDate; }
    status.set('idle', '条件期間内の評価対象日を指定してください。'); fields.disabled = false;
  } catch (error) { showError($('page-error'), error); status.set('error', '使用する条件または保存結果を確認できませんでした。'); }
}
export const ready = initialize();
