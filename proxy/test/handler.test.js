// End-to-end tests of the G-33 acceptance criteria (providers are fake).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { MODEL_CHAIN } from '../src/config.js';
import { createHandler } from '../src/handler.js';
import { ACCESS_CODE, ENV, fakeFetch, geminiOk, groqOk, handlerWith, makeRequest, status } from './helpers.js';

const okRoutes = { 'gemini-3.5-flash-lite': () => geminiOk({ geri_okuma: 'tamam' }) };

test('success: versioned contract', async () => {
  const { handle } = handlerWith(okRoutes);
  const res = await handle(makeRequest(), ENV);
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await res.json(), {
    v: 1, ok: true, result: { geri_okuma: 'tamam' }, provider: 'gemini', model: 'gemini-3.5-flash-lite', attempts: 1,
  });
});

test('unauthorized: missing or wrong access code, no provider call', async () => {
  const { handle, fetchImpl } = handlerWith(okRoutes);
  for (const code of [null, 'YanlisKod123456', '']) {
    const res = await handle(makeRequest({ code }), ENV);
    assert.equal(res.status, 401);
    assert.equal((await res.json()).error, 'UNAUTHORIZED');
  }
  assert.equal(fetchImpl.calls.length, 0);
});

test('missing ACCESS_CODES secret -> SERVER_MISCONFIGURED, never open access', async () => {
  const { handle } = handlerWith(okRoutes);
  const res = await handle(makeRequest(), { GEMINI_API_KEY: 'k' });
  assert.equal(res.status, 500);
  assert.equal((await res.json()).error, 'SERVER_MISCONFIGURED');
});

test('rate limit: burst above 20/min rejected with Retry-After', async () => {
  let t = 0;
  const { handle } = handlerWith(okRoutes, { now: () => t });
  for (let i = 0; i < 20; i++) {
    t = i * 100;
    assert.equal((await handle(makeRequest(), ENV)).status, 200);
  }
  t = 2_100;
  const res = await handle(makeRequest(), ENV);
  assert.equal(res.status, 429);
  const body = await res.json();
  assert.equal(body.error, 'RATE_LIMITED');
  assert.equal(res.headers.get('retry-after'), String(body.retry_after_s));
});

test('normal pace: 10 requests within 1 minute are never rejected', async () => {
  let t = 0;
  const { handle } = handlerWith(okRoutes, { now: () => t });
  for (let i = 0; i < 10; i++) {
    t = i * 6_000;
    assert.equal((await handle(makeRequest(), ENV)).status, 200);
  }
});

test('image field rejected, provider never called', async () => {
  const { handle, fetchImpl } = handlerWith(okRoutes);
  const res = await handle(makeRequest({ body: { v: 1, system: 's', user: 'u', schema: {}, image: 'iVBOR' } }), ENV);
  assert.equal(res.status, 400);
  assert.equal((await res.json()).error, 'INVALID_REQUEST');
  assert.equal(fetchImpl.calls.length, 0);
});

test('non-JSON content type (e.g. multipart image upload) -> 415', async () => {
  const { handle } = handlerWith(okRoutes);
  const res = await handle(makeRequest({ headers: { 'content-type': 'multipart/form-data; boundary=x' }, rawBody: '--x' }), ENV);
  assert.equal(res.status, 415);
});

test('oversized body -> 413', async () => {
  const { handle } = handlerWith(okRoutes);
  const res = await handle(makeRequest({ rawBody: `{"v":1,"user":"${'a '.repeat(20_000)}"}` }), ENV);
  assert.equal(res.status, 413);
});

test('malformed JSON -> 400', async () => {
  const { handle } = handlerWith(okRoutes);
  const res = await handle(makeRequest({ rawBody: '{nope' }), ENV);
  assert.equal(res.status, 400);
});

test('routing: wrong path 404, wrong method 405', async () => {
  const { handle } = handlerWith(okRoutes);
  assert.equal((await handle(makeRequest({ path: '/' }), ENV)).status, 404);
  assert.equal((await handle(makeRequest({ method: 'GET' }), ENV)).status, 405);
});

test('Gemini quota -> Groq answers', async () => {
  const { handle } = handlerWith({
    'gemini-3.5-flash-lite': () => status(429),
    'gemini-3.6-flash': () => status(500),
    'openai/gpt-oss-120b': () => groqOk({ geri_okuma: 'yedek' }),
  });
  const body = await (await handle(makeRequest(), ENV)).json();
  assert.equal(body.ok, true);
  assert.equal(body.provider, 'groq');
  assert.deepEqual(body.result, { geri_okuma: 'yedek' });
});

test('all providers out of quota -> distinguishable QUOTA_EXHAUSTED with on_device hint', async () => {
  const { handle } = handlerWith(Object.fromEntries(MODEL_CHAIN.map((s) => [s.model, () => status(429)])));
  const res = await handle(makeRequest(), ENV);
  assert.equal(res.status, 503);
  assert.deepEqual(await res.json(), { v: 1, ok: false, error: 'QUOTA_EXHAUSTED', on_device: true });
});

test('all providers failing (not quota) -> PROVIDERS_UNAVAILABLE', async () => {
  const { handle } = handlerWith({});
  const body = await (await handle(makeRequest(), ENV)).json();
  assert.equal(body.error, 'PROVIDERS_UNAVAILABLE');
  assert.equal(body.on_device, true);
});

test('provider error text, keys and request text never reach the client', async () => {
  const handle = createHandler({ fetchImpl: fakeFetch({ 'gemini-3.5-flash-lite': () => status(400) }) });
  const text = await (await handle(makeRequest(), ENV)).text();
  for (const secret of ['SECRET_ECHO', ENV.GEMINI_API_KEY, ENV.GROQ_API_KEY, ACCESS_CODE, '[KISI_1]']) {
    assert.ok(!text.includes(secret), `leaked ${secret}`);
  }
  assert.equal(JSON.parse(text).error, 'UPSTREAM_REJECTED');
});
