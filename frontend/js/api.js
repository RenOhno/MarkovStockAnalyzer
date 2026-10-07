export const BROWSER_TIMEOUT_MS = 35_000;

export class ApiError extends Error {
  constructor(message, { code = 'HTTP_ERROR', requestId = null, status = 0, details = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.requestId = requestId;
    this.status = status;
    this.details = details;
  }
}

/** One attempt only, including POST. Aborting the browser never guarantees server cancellation. */
export async function request(path, options = {}) {
  const { timeoutMs = BROWSER_TIMEOUT_MS, signal, body, headers, ...fetchOptions } = options;
  const controller = new AbortController();
  let timedOut = false;
  const abort = () => controller.abort(signal?.reason);
  if (signal?.aborted) abort();
  else signal?.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
  let requestId = null;
  try {
    const response = await fetch(path, {
      ...fetchOptions,
      signal: controller.signal,
      headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
    requestId = response.headers.get('X-Request-Id');
    if (response.status === 204) return null;
    let data;
    try { data = await response.json(); }
    catch {
      if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
      throw new ApiError('サーバーの応答を読み取れませんでした。', {
        code: response.ok ? 'INVALID_RESPONSE' : 'HTTP_ERROR', status: response.status, requestId
      });
    }
    if (!response.ok) {
      throw new ApiError(typeof data?.message === 'string' ? data.message : '処理に失敗しました。', {
        code: typeof data?.code === 'string' ? data.code : 'HTTP_ERROR',
        requestId: typeof data?.requestId === 'string' ? data.requestId : requestId,
        status: response.status, details: data?.details ?? {}
      });
    }
    return data;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (timedOut) throw new ApiError(
      'ブラウザーの待機時間（35秒）を超えました。サーバー処理が中止されたとは限りません。履歴を確認してから再実行してください。',
      { code: 'BROWSER_TIMEOUT', requestId }
    );
    if (controller.signal.aborted) throw new ApiError('画面での待機を中断しました。サーバー処理が中止されたとは限りません。', { code: 'REQUEST_ABORTED', requestId });
    throw new ApiError('サーバーに接続できませんでした。実行済みの可能性があるため、再実行する前に履歴を確認してください。', { code: 'NETWORK_ERROR', requestId });
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}

export const api = {
  stocks: () => request('/api/stocks'),
  conditions: stockId => request(`/api/conditions${stockId ? `?${new URLSearchParams({ stockId })}` : ''}`),
  condition: id => request(`/api/conditions/${encodeURIComponent(id)}`),
  createCondition: body => request('/api/conditions', { method: 'POST', body }),
  analyze: body => request('/api/analysis', { method: 'POST', body }),
  analysis: id => request(`/api/analysis/${encodeURIComponent(id)}`),
  series: id => request(`/api/analysis/${encodeURIComponent(id)}/series`),
  backtest: body => request('/api/backtest', { method: 'POST', body }),
  backtestResult: id => request(`/api/backtest/${encodeURIComponent(id)}`),
  predictions: (id, page, size, options) => request(`/api/backtest/${encodeURIComponent(id)}/predictions?${new URLSearchParams({ page, size })}`, options),
  history: (filters, options) => request(`/api/history?${new URLSearchParams(filters)}`, options)
};
