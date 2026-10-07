import { state, probability } from './format.js';

const instances = new Map();
const COLORS = { UP: '#16796b', FLAT: '#69778d', DOWN: '#b14b50' };
export function destroyCharts() { instances.forEach(chart => chart.destroy()); instances.clear(); }
function render(canvas, configuration) {
  instances.get(canvas)?.destroy(); instances.delete(canvas);
  if (typeof globalThis.Chart !== 'function') throw new Error('チャートを読み込めませんでした。下の表で数値を確認できます。');
  const chart = new globalThis.Chart(canvas, configuration); instances.set(canvas, chart); return chart;
}
function options(yTitle) {
  return { responsive: true, maintainAspectRatio: false, animation: false,
    interaction: { intersect: false, mode: 'index' },
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { display: false }, ticks: { maxTicksLimit: 7, maxRotation: 0 } },
      y: { title: { display: true, text: yTitle }, grid: { color: '#e7ecf1' } }
    } };
}
export function renderSeriesCharts(priceCanvas, returnCanvas, points) {
  const labels = points.map(point => point.date);
  render(priceCanvas, { type: 'line', data: { labels, datasets: [{ label: '提供元調整後終値',
    data: points.map(point => point.adjustedClose == null ? null : Number(point.adjustedClose)),
    borderColor: '#16796b', backgroundColor: '#16796b12', fill: true, pointRadius: 0, borderWidth: 2,
    spanGaps: false, tension: 0 }] }, options: options('提供元調整後終値') });
  const returnOptions = options('日次リターン（%）');
  returnOptions.plugins.tooltip = { callbacks: {
    label: context => `リターン：${probability(points[context.dataIndex]?.returnValue, 3)}`,
    afterLabel: context => `状態：${state(points[context.dataIndex]?.state)}`
  } };
  render(returnCanvas, { type: 'bar', data: { labels, datasets: [{ label: '日次リターン・状態',
    data: points.map(point => point.returnValue == null ? null : point.returnValue * 100),
    backgroundColor: points.map(point => Object.hasOwn(COLORS, point.state) ? COLORS[point.state] : '#69778d'), borderWidth: 0 }] }, options: returnOptions });
}
