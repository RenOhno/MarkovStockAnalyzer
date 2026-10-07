export const STATES = ['UP', 'FLAT', 'DOWN'];
export const HORIZONS = [1, 3, 5, 10];
export const EMPTY = '—';

const finite = value => (typeof value === 'number' || (typeof value === 'string' && value.trim() !== '')) && Number.isFinite(Number(value));
export function number(value, digits = 0) {
  return finite(value) ? new Intl.NumberFormat('ja-JP', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(Number(value)) : EMPTY;
}
export function probability(value, digits = 1) {
  return finite(value) ? `${number(Number(value) * 100, digits)}%` : EMPTY;
}
export function percentToRatio(value) {
  if (!finite(value)) throw new RangeError('閾値に数値を入力してください。');
  // Trim binary floating-point display artifacts while retaining the DB contract's precision.
  return Number((Number(value) / 100).toPrecision(15));
}
export function ratioToPercent(value) { return finite(value) ? Number((Number(value) * 100).toPrecision(15)) : null; }
export function state(value) { return STATES.includes(value) ? ({ UP: '上昇 UP', FLAT: '横ばい FLAT', DOWN: '下落 DOWN' })[value] : EMPTY; }
export function priceBasis(value) { return value === 'PROVIDER_ADJUSTED_CLOSE' ? '提供元調整後終値' : value ?? EMPTY; }
export function date(value) { return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : EMPTY; }
export function dateTime(value) {
  if (!value) return EMPTY;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? EMPTY : new Intl.DateTimeFormat('ja-JP', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    timeZone: 'Asia/Tokyo', timeZoneName: 'short', hour12: false
  }).format(parsed);
}
export function positiveId(value) {
  if (!/^[1-9]\d*$/.test(String(value ?? ''))) throw new RangeError('有効なIDが指定されていません。履歴または分析結果から開いてください。');
  return String(value);
}
export function requestIdValue(value) {
  const id = Number(positiveId(value));
  if (!Number.isSafeInteger(id)) throw new RangeError('IDがブラウザーで安全に扱える範囲を超えています。');
  return id;
}
export function detailLink(type, id) {
  if (!['ANALYSIS', 'BACKTEST'].includes(type)) return null;
  const page = type === 'ANALYSIS' ? 'analysis.html' : 'backtest.html';
  return `${page}?${new URLSearchParams({ id: positiveId(id) })}`;
}
export function backtestLink(conditionId, datasetId) {
  return `backtest.html?${new URLSearchParams({ conditionId: positiveId(conditionId), datasetId: positiveId(datasetId) })}`;
}
export function paging(page, size, totalElements) {
  if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100
      || !Number.isSafeInteger(totalElements) || totalElements < 0) throw new RangeError('ページの指定が不正です。');
  const offset = page * size;
  if (!Number.isSafeInteger(offset)) throw new RangeError('ページの指定が大きすぎます。');
  return { page, size, totalElements, totalPages: Math.ceil(totalElements / size),
    hasPrevious: page > 0, hasNext: offset + size < totalElements,
    from: offset < totalElements ? offset + 1 : 0, to: offset < totalElements ? Math.min(offset + size, totalElements) : 0 };
}
export function pageParameters(params, defaults = { page: 0, size: 20 }) {
  const values = {};
  for (const key of ['page', 'size']) {
    const value = params.get(key);
    if (value !== null && !/^\d+$/.test(value)) throw new RangeError('ページの指定が不正です。');
    values[key] = value === null ? defaults[key] : Number(value);
  }
  paging(values.page, values.size, 0);
  return values;
}
export function analysisDisplayStatus(result) { return result.predictionStatus === 'AVAILABLE' ? 'success' : 'partial'; }

export function conditionRequest(form) {
  const lowerThreshold = percentToRatio(form.lowerThreshold);
  const upperThreshold = percentToRatio(form.upperThreshold);
  if (!form.name?.trim() || form.name.trim().length > 100) throw new RangeError('条件名を1〜100文字で入力してください。');
  if (date(form.startDate) === EMPTY || date(form.endDate) === EMPTY || form.startDate > form.endDate)
    throw new RangeError('開始日と終了日を確認してください。');
  if (lowerThreshold <= -1 || lowerThreshold > 0 || upperThreshold < 0 || upperThreshold >= 1 || lowerThreshold >= upperThreshold)
    throw new RangeError('下側閾値は−100%より大きく0%以下、上側閾値は0%以上100%未満で、下側 < 上側にしてください。');
  return { name: form.name.trim(), stockId: positiveId(form.stockId), startDate: form.startDate, endDate: form.endDate,
    lowerThreshold, upperThreshold, stateCount: 3, estimator: 'MLE_STRICT', windowMode: 'FULL', windowSize: null, horizons: [...HORIZONS] };
}
