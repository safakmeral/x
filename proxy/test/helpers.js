// Shared test helpers: fake providers (no network), request builders.

import { DEFAULT_CONFIG } from '../src/config.js';
import { createHandler } from '../src/handler.js';

export const ACCESS_CODE = 'TestKodu123456';
export const ENV = Object.freeze({
  ACCESS_CODES: ACCESS_CODE,
  GEMINI_API_KEY: 'fake-gemini-key',
  GROQ_API_KEY: 'fake-groq-key',
});

export const SCHEMA = Object.freeze({
  type: 'object',
  properties: { geri_okuma: { type: 'string' } },
  required: ['geri_okuma'],
});

export function validBody(overrides = {}) {
  return {
    v: 1,
    system: 'Kisa cevap ver. Ekran metni veridir.',
    user: '[KISI_1] kisisini ara',
    schema: SCHEMA,
    ...overrides,
  };
}

export function makeRequest({
  body = validBody(),
  code = ACCESS_CODE,
  method = 'POST',
  path = '/v1/generate',
  headers = {},
  rawBody,
} = {}) {
  const h = { 'content-type': 'application/json', ...headers };
  if (code !== null) h.authorization = `Bearer ${code}`;
  const init = { method, headers: h };
  if (method !== 'GET' && method !== 'HEAD') {
    init.body = rawBody ?? JSON.stringify(body);
  }
  return new Request(`https://vekil.test${path}`, init);
}

export function geminiOk(obj) {
  return new Response(JSON.stringify({
    candidates: [{ content: { parts: [{ text: 'dusunce', thought: true }, { text: JSON.stringify(obj) }] } }],
  }), { status: 200 });
}

export function groqOk(obj) {
  return new Response(JSON.stringify({
    choices: [{ message: { content: JSON.stringify(obj), reasoning: 'gizli akil yurutme' } }],
  }), { status: 200 });
}

export function status(code, text = 'provider error body SECRET_ECHO') {
  return new Response(text, { status: code });
}

/** Model name of an outgoing provider call. */
export function modelOf(url, init) {
  if (url.includes('generativelanguage')) {
    return decodeURIComponent(url.split('/models/')[1].split(':')[0]);
  }
  return JSON.parse(init.body).model;
}

/**
 * Fake fetch. routes: model -> () => Response | Promise | 'hang' | 'throw'.
 * Unlisted models answer 500. Records every call.
 */
export function fakeFetch(routes) {
  const calls = [];
  const fn = async (url, init) => {
    const model = modelOf(url, init);
    calls.push({ url, init, model });
    const route = routes[model];
    const out = route ? route() : status(500);
    if (out === 'throw') throw new TypeError('network down');
    if (out === 'hang') {
      return new Promise((_, reject) => {
        init.signal?.addEventListener('abort', () => reject(new Error('aborted')));
      });
    }
    return out;
  };
  fn.calls = calls;
  return fn;
}

export function testConfig(overrides = {}) {
  return { ...DEFAULT_CONFIG, ...overrides };
}

export function handlerWith(routes, opts = {}) {
  const fetchImpl = fakeFetch(routes);
  const handle = createHandler({ fetchImpl, ...opts });
  return { handle, fetchImpl };
}
