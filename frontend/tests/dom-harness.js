import { readFile } from 'node:fs/promises';
import { parseHTML } from 'linkedom';

/** Small DOM-only harness; no browser runner and no network requests. */
export async function page(t, filename, query, routes) {
  const source = await readFile(new URL(`../${filename}`, import.meta.url), 'utf8');
  const { window, document } = parseHTML(source);
  const requests = []; const visited = [];
  Object.defineProperty(window, 'location', { value: { search: query }, configurable: true });
  Object.defineProperty(window, 'history', { value: { replaceState: (_state, _title, url) => visited.push(url) }, configurable: true });
  // linkedom deliberately omits browser form validation and the select value setter.
  for (const select of document.querySelectorAll('select')) {
    Object.defineProperty(select, 'value', {
      get() { const option = this.querySelector('option[selected]') ?? this.querySelector('option'); return option?.getAttribute('value') ?? option?.textContent ?? ''; },
      set(value) { for (const option of this.querySelectorAll('option')) {
        if ((option.getAttribute('value') ?? option.textContent) === String(value)) option.setAttribute('selected', ''); else option.removeAttribute('selected');
      } }, configurable: true
    });
  }
  for (const form of document.querySelectorAll('form')) {
    form.reportValidity = () => true;
    form.elements = { namedItem: name => form.querySelector(`[name="${name}"]`) };
  }
  for (const node of document.querySelectorAll('fieldset,button,select,input')) {
    if (typeof node.disabled !== 'boolean') Object.defineProperty(node, 'disabled', {
      get() { return this.hasAttribute('disabled'); }, set(value) { if (value) this.setAttribute('disabled', ''); else this.removeAttribute('disabled'); }, configurable: true
    });
  }
  document.defaultView.HTMLElement.prototype.scrollIntoView = () => {};
  const originals = { document: globalThis.document, window: globalThis.window, Chart: globalThis.Chart };
  globalThis.document = document; globalThis.window = window;
  globalThis.Chart = class { constructor() {} destroy() {} };
  t.mock.method(globalThis, 'fetch', async (path, options = {}) => {
    const request = { path: String(path), method: options.method ?? 'GET', body: options.body ? JSON.parse(options.body) : undefined };
    requests.push(request);
    const response = await routes(request);
    if (!response) throw new Error(`Unexpected request: ${path}`);
    return new Response(JSON.stringify(response.body ?? response), { status: response.status ?? 200, headers: { 'Content-Type': 'application/json' } });
  });
  t.after(() => { globalThis.document = originals.document; globalThis.window = originals.window; globalThis.Chart = originals.Chart; });
  const name = filename.replace('.html', '');
  const module = await import(new URL(`../js/pages/${name}.js?test=${Math.random()}`, import.meta.url));
  await module.ready;
  const settle = async predicate => {
    for (let i = 0; i < 100; i++) {
      if (predicate()) return;
      await new Promise(resolve => setTimeout(resolve, 2));
    }
    throw new Error('UI state did not settle');
  };
  return { document, window, requests, visited, settle,
    submit: id => document.getElementById(id).dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })) };
}
