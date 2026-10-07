import { api } from '../api.js';
import { EMPTY, date, dateTime, detailLink, pageParameters, positiveId } from '../format.js';
import { clearError, el, populateSelect, renderPaging, showError, statusController, table } from '../components.js';

const $ = id => document.getElementById(id);
const fields = $('history-fields'); const status = statusController($('page-status'), [fields]);
let page = 0; let size = 20; let stockLabels = new Map();
let busy = false;
function row(summary) {
  const type = el('span', summary.type === 'ANALYSIS' ? '分析 ANALYSIS' : 'バックテスト BACKTEST', `badge ${summary.type === 'ANALYSIS' ? 'type-analysis' : 'type-backtest'}`);
  const links = el('div', null, 'actions'); links.style.margin = '0';
  const href = detailLink(summary.type, summary.id);
  if (href) { const detail = el('a', '詳細 →'); detail.href = href; links.append(detail); }
  const copy = el('a', '条件を複製'); copy.href = `analysis.html?${new URLSearchParams({ copyConditionId: positiveId(summary.conditionId) })}`; links.append(copy);
  return [type, summary.conditionName ?? EMPTY, stockLabels.get(String(summary.stockId)) ?? `銘柄 #${summary.stockId}`,
    `${date(summary.startDate)} 〜 ${date(summary.endDate)}`, summary.datasetId ?? EMPTY,
    summary.predictionStatus === null ? EMPTY : summary.predictionStatus === 'AVAILABLE' ? '利用可 AVAILABLE' : '予測不可 UNAVAILABLE',
    summary.engineVersion ?? EMPTY, dateTime(summary.createdAt), dateTime(summary.dataFetchedAt), links];
}
async function load(nextPage = 0) {
  if (busy) return; busy = true;
  clearError($('page-error')); $('history-results').replaceChildren(); $('history-paging').querySelectorAll('button').forEach(button => { button.disabled = true; });
  status.set('loading', '保存済みの履歴を読み込んでいます。');
  try {
    const filters = { type: $('history-type').value, page: nextPage, size: Number($('history-size').value) };
    if ($('history-stock').value) filters.stockId = $('history-stock').value;
    const response = await api.history(filters); page = response.page; size = response.size;
    $('history-results').replaceChildren(table(['種類', '条件名', '銘柄', '対象期間', 'Dataset ID', 'predictionStatus', '計算版', '結果保存日時', 'データ取得日時', '操作'],
      response.items.map(row), { caption: '保存済みの分析・バックテスト履歴', className: 'data-table' }));
    renderPaging($('history-paging'), response, load);
    window.history.replaceState(null, '', `history.html?${new URLSearchParams(filters)}`);
    status.set('success', response.items.length ? '保存済み結果を表示しています。新規実行・価格の再取得は行いません。' : 'この条件・ページに一致する保存結果はありません。');
  } catch (error) { showError($('page-error'), error); $('history-paging').replaceChildren(); status.set('error', '履歴を取得できませんでした。絞込み条件を確認し、もう一度取得してください。'); }
  finally { busy = false; }
}
async function initialize() {
  $('history-form').addEventListener('submit', event => { event.preventDefault(); load(0); });
  const params = new URLSearchParams(window.location.search);
  try {
    ({ page, size } = pageParameters(params));
    const type = params.get('type') ?? 'ALL';
    if (!['ALL', 'ANALYSIS', 'BACKTEST'].includes(type)) throw new RangeError('履歴の種類はALL / ANALYSIS / BACKTESTから選んでください。');
    $('history-type').value = type;
    if (![20, 50, 100].includes(size)) { const option = el('option', size); option.value = String(size); $('history-size').append(option); }
    $('history-size').value = String(size);
    status.set('loading', '銘柄情報を読み込んでいます。');
    try {
      const stocks = await api.stocks(); stockLabels = new Map(stocks.map(stock => [String(stock.id), `${stock.ticker} · ${stock.name}`]));
      populateSelect($('history-stock'), stocks, stock => `${stock.ticker} · ${stock.name}`, 'すべての銘柄');
    } catch { $('history-stock').replaceChildren(Object.assign(el('option', 'すべての銘柄（銘柄名の取得不可）'), { value: '' })); }
    const stockId = params.get('stockId');
    if (stockId) {
      if (!stockLabels.has(stockId)) { const option = el('option', `銘柄 #${stockId}`); option.value = stockId; $('history-stock').append(option); }
      $('history-stock').value = stockId;
    }
    status.set('idle', '履歴を読み込みます。'); fields.disabled = false; await load(page);
  } catch (error) { showError($('page-error'), error); status.set('error', '履歴の検索条件を確認してください。'); fields.disabled = false; }
}
export const ready = initialize();
