import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, BROWSER_TIMEOUT_MS, request } from '../js/api.js';

test('POST serializes JSON once with JSON headers', async t => {
  const calls = []; t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options }); return new Response(JSON.stringify({ id: '1' }), { status: 201, headers: { 'Content-Type': 'application/json' } });
  });
  assert.deepEqual(await request('/api/analysis', { method: 'POST', body: { conditionId: 101 } }), { id: '1' });
  assert.equal(calls.length, 1); assert.equal(calls[0].options.body, '{"conditionId":101}'); assert.equal(calls[0].options.headers['Content-Type'], 'application/json');
});
test('Python/public ErrorResponse retains code, message, requestId, status and details without retry', async t => {
  let count = 0; t.mock.method(globalThis, 'fetch', async () => { count++; return new Response(JSON.stringify({ code: 'ENGINE_VERSION_UNSUPPORTED', message: 'saved version unavailable', requestId: 'request-123', details: { engineVersion: 'old' } }), { status: 409 }); });
  await assert.rejects(request('/api/analysis/1/series'), error => error instanceof ApiError && error.code === 'ENGINE_VERSION_UNSUPPORTED'
    && error.message === 'saved version unavailable' && error.requestId === 'request-123' && error.status === 409 && error.details.engineVersion === 'old');
  assert.equal(count, 1);
});
test('browser timeout aborts at the configured deadline and explains server ambiguity', async t => {
  assert.equal(BROWSER_TIMEOUT_MS, 35000); let count = 0;
  t.mock.method(globalThis, 'fetch', async (_, { signal }) => { count++; return new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true })); });
  await assert.rejects(request('/api/analysis', { method: 'POST', body: { conditionId: 1 }, timeoutMs: 5 }), error => error.code === 'BROWSER_TIMEOUT' && /サーバー処理が中止されたとは限りません/.test(error.message));
  assert.equal(count, 1);
});
test('network failures are safe errors and POST is not automatically retried', async t => {
  let count = 0; t.mock.method(globalThis, 'fetch', async () => { count++; throw new TypeError('private path'); });
  await assert.rejects(request('/api/backtest', { method: 'POST', body: {} }), error => error.code === 'NETWORK_ERROR' && !error.message.includes('private path'));
  assert.equal(count, 1);
});
test('non-JSON errors do not expose raw HTML and use the response requestId', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('<script>secret</script>', { status: 503, headers: { 'X-Request-Id': 'request-header' } }));
  await assert.rejects(request('/api/history'), error => error.status === 503 && error.requestId === 'request-header' && !error.message.includes('script'));
});
test('malformed successful JSON and explicit cancellation are distinguished', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('not-json', { status: 200 }));
  await assert.rejects(request('/api/history'), error => error.code === 'INVALID_RESPONSE');
  t.mock.method(globalThis, 'fetch', async (_, { signal }) => { if (signal.aborted) throw new DOMException('aborted', 'AbortError'); });
  const controller = new AbortController(); controller.abort();
  await assert.rejects(request('/api/history', { signal: controller.signal }), error => error.code === 'REQUEST_ABORTED');
});
