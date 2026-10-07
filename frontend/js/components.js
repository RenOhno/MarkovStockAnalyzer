import { STATES, HORIZONS, EMPTY, state, number, probability, dateTime, paging } from './format.js';

/** External values enter the DOM exclusively through textContent or validated URLs. */
export function el(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = String(text);
  if (className) node.className = className;
  return node;
}
export function stateBadge(value) { return el('span', state(value), `badge state-${STATES.includes(value) ? value.toLowerCase() : 'unknown'}`); }
export function table(headers, rows, { caption = '', rowHeaders = false, className = '' } = {}) {
  const result = el('table', null, className);
  if (caption) result.append(el('caption', caption));
  const head = el('thead'); const headRow = el('tr');
  headers.forEach(header => { const cell = el('th', header); cell.scope = 'col'; headRow.append(cell); });
  head.append(headRow); result.append(head);
  const body = el('tbody');
  if (!rows.length) {
    const row = el('tr'); const cell = el('td', '表示するデータはありません。', 'empty-cell');
    cell.colSpan = headers.length; row.append(cell); body.append(row);
  }
  rows.forEach(values => {
    const row = el('tr');
    values.forEach((value, index) => {
      const cell = el(rowHeaders && index === 0 ? 'th' : 'td');
      if (rowHeaders && index === 0) cell.scope = 'row';
      if (value && typeof value === 'object' && value.nodeType) cell.append(value);
      else cell.textContent = value === null || value === undefined ? EMPTY : String(value);
      row.append(cell);
    });
    body.append(row);
  });
  result.append(body);
  const wrapper = el('div', null, 'table-scroll'); wrapper.tabIndex = 0;
  wrapper.setAttribute('role', 'region'); wrapper.setAttribute('aria-label', caption || 'データ表');
  wrapper.append(result); return wrapper;
}
export function matrix(values, { probabilities = false, confusion = false } = {}) {
  return table([confusion ? '実際 ↓ / 予測 →' : '現在 ↓ / 次 →', ...STATES.map(state)], STATES.map((value, row) =>
    [state(value), ...STATES.map((_, column) => {
      const input = values?.[row]?.[column];
      return probabilities && input == null ? '未推定' : probabilities ? probability(input, 2) : number(input);
    })]), { rowHeaders: true, className: 'matrix', caption: confusion ? '混同行列：行は実際の状態、列は予測した状態' : '遷移行列：行は現在の状態、列は次の状態' });
}
export function forecasts(result) {
  const available = result.predictionStatus === 'AVAILABLE';
  return table(['予測対象', ...STATES.map(state)], HORIZONS.map(horizon => {
    const forecast = available ? result.forecasts?.find(item => item.horizon === horizon) : null;
    return [`${horizon}営業日後`, ...STATES.map((_, index) => available ? probability(forecast?.probabilities?.[index], 2) : '推定不可')];
  }), { rowHeaders: true, caption: '将来の状態確率：営業日は市場の取引日を表します' });
}
export function warningText(warning) {
  if (warning?.code === 'ZERO_ROW_UNESTIMATED') {
    const labels = warning.states?.map(state).filter(value => value !== EMPTY).join('・');
    return `${labels || '一部の状態'}からの遷移が観測されていません。行列の一部を推定できないため、1/3/5/10営業日後の予測は利用できません。遷移回数と価格系列は確認できます。`;
  }
  if (['SMALL_SAMPLE', 'LOW_SAMPLE_COUNT', 'INSUFFICIENT_SAMPLE'].includes(warning?.code))
    return '観測数が少ないため、推定した確率には大きな不確実性があります。';
  return `計算時の警告：${warning?.code ?? '詳細なし'}${warning?.states?.length ? `（${warning.states.map(state).join('・')}）` : ''}`;
}
export function warnings(values = [], unavailable = false) {
  const box = el('div', null, 'notice warning');
  box.append(el('h3', unavailable ? '予測を表示できない理由' : '計算時の注意'));
  const list = el('ul');
  values.forEach(value => list.append(el('li', warningText(value))));
  if (!values.length && unavailable) list.append(el('li', '完全な遷移行列が得られていないため予測は利用できません。計算根拠は下の遷移回数で確認できます。'));
  box.append(list); box.hidden = !values.length && !unavailable; return box;
}
export function errorBox(error) {
  const box = el('div', null, 'notice error'); box.setAttribute('role', 'alert');
  box.append(el('p', '処理を完了できませんでした', 'notice-title'), el('p', error.message || '入力内容または通信状態を確認してください。'));
  if (error.code === 'ENGINE_VERSION_UNSUPPORTED') box.append(el('p', '保存時の計算版を再現できないため、系列を表示できません。保存済みの集計結果は引き続き確認できます。'));
  box.append(el('p', `コード：${error.code || 'INPUT_ERROR'} / requestId：${error.requestId ?? EMPTY}`, 'error-reference'));
  const link = el('a', '履歴で保存済みの結果を確認'); link.href = 'history.html'; box.append(link); return box;
}
export function showError(target, error) { target.replaceChildren(errorBox(error)); target.hidden = false; }
export function clearError(target) { target.replaceChildren(); target.hidden = true; }
export function statusController(target, controls = []) {
  let locked = false; let previous = [];
  return { set(status, message) {
    if (status === 'loading' && !locked) { previous = controls.map(control => control.disabled); locked = true; controls.forEach(control => { control.disabled = true; }); }
    if (status !== 'loading' && locked) { controls.forEach((control, index) => { control.disabled = previous[index]; }); locked = false; }
    target.dataset.state = status;
    target.setAttribute('aria-busy', String(status === 'loading'));
    target.replaceChildren();
    if (status === 'loading') { const spinner = el('span', null, 'spinner'); spinner.setAttribute('aria-hidden', 'true'); target.append(spinner); }
    const label = ({ idle: '準備完了', loading: '処理中', success: '完了', partial: '部分結果', error: 'エラー' })[status];
    target.append(el('span', label, 'status-label'), el('span', message));
  }, get busy() { return locked; } };
}
export function renderPaging(target, { page, size, totalElements }, onPage) {
  const info = paging(page, size, totalElements);
  const previous = el('button', '← 前へ', 'button secondary'); previous.type = 'button'; previous.disabled = !info.hasPrevious;
  const next = el('button', '次へ →', 'button secondary'); next.type = 'button'; next.disabled = !info.hasNext;
  const label = el('span', `${info.from}〜${info.to}件 / 全${totalElements}件 · ${page + 1}ページ目`, 'paging-label');
  previous.addEventListener('click', () => onPage(page - 1)); next.addEventListener('click', () => onPage(page + 1));
  target.replaceChildren(previous, label, next); return { previous, next };
}
export function stats(target, entries) {
  target.replaceChildren(...entries.map(([label, value, note]) => {
    const item = el('div', null, 'stat'); item.append(el('dt', label), el('dd', value));
    if (note) item.append(el('span', note, 'stat-note')); return item;
  }));
}
export function provenance(target, result) {
  const source = result.dataSource ?? {}; const runtime = result.provenance ?? {};
  const entries = [
    ['提供元', source.provider], ['提供元の版', source.providerVersion], ['調整方針', source.adjustmentPolicy],
    ['データ取得日時', dateTime(source.fetchedAt)], ['収録期間', `${source.coverageStart ?? EMPTY} 〜 ${source.coverageEnd ?? EMPTY}`],
    ['データ内容 SHA-256', source.contentSha256], ['カレンダー', `${source.calendarName ?? EMPTY} / ${source.calendarVersion ?? EMPTY}`],
    ['計算エンジン', result.engineVersion ?? runtime.engineVersion], ['Git commit', runtime.gitCommit],
    ['依存ライブラリ', runtime.dependencyVersions ? Object.entries(runtime.dependencyVersions).map(([name, version]) => `${name}: ${version}`).join(' / ') || EMPTY : EMPTY],
    ['正規化の版', runtime.normalizationVersion], ['設定の版', runtime.configurationVersion], ['結果保存日時', dateTime(result.createdAt)]
  ];
  const definition = el('dl', null, 'metadata');
  entries.forEach(([label, value]) => definition.append(el('dt', label), el('dd', value ?? EMPTY)));
  target.replaceChildren(definition);
}
export function populateSelect(select, items, label, placeholder = '選択してください') {
  const first = el('option', placeholder); first.value = '';
  select.replaceChildren(first, ...items.map(item => { const option = el('option', label(item)); option.value = String(item.id); return option; }));
}
