import { test } from 'node:test';
import assert from 'node:assert/strict';
import { runChain, classifyStatus } from '../src/chain.js';
import { DEFAULT_CONFIG, MODEL_CHAIN } from '../src/config.js';
import { ENV, SCHEMA, fakeFetch, geminiOk, groqOk, status } from './helpers.js';

const REQ = { system: 'sistem', user: '[KISI_1] ara', schema: SCHEMA };
const deps = (fetchImpl) => ({ fetchImpl, now: () => Date.now() });
const fastConfig = {
  ...DEFAULT_CONFIG,
  timeouts: { attemptMs: 50, totalMs: 1_000, minAttemptMs: 10 },
};

test('chain order and names come from the single config', () => {
  assert.deepEqual(MODEL_CHAIN.map((s) => s.model), [
    'gemini-3.5-flash-lite', 'gemini-3.6-flash', 'openai/gpt-oss-120b', 'openai/gpt-oss-20b',
  ]);
});

test('status classification', () => {
  assert.equal(classifyStatus(429), 'quota');
  assert.equal(classifyStatus(500), 'unavailable');
  assert.equal(classifyStatus(503), 'unavailable');
  assert.equal(classifyStatus(400), 'rejected');
  assert.equal(classifyStatus(403), 'rejected');
});

test('first model answers: thought parts dropped, one call', async () => {
  const f = fakeFetch({ 'gemini-3.5-flash-lite': () => geminiOk({ geri_okuma: 'tamam' }) });
  const r = await runChain(REQ, ENV, deps(f), DEFAULT_CONFIG);
  assert.deepEqual(r, {
    ok: true, result: { geri_okuma: 'tamam' }, provider: 'gemini', model: 'gemini-3.5-flash-lite', attempts: 1,
  });
});

test('Gemini request: temperature 0, minimal thinking, JSON schema, key only in header', async () => {
  const f = fakeFetch({ 'gemini-3.5-flash-lite': () => geminiOk({ geri_okuma: 'x' }) });
  await runChain(REQ, ENV, deps(f), DEFAULT_CONFIG);
  const { url, init } = f.calls[0];
  assert.ok(url.startsWith('https://generativelanguage.googleapis.com/'));
  assert.ok(!url.includes(ENV.GEMINI_API_KEY));
  const body = JSON.parse(init.body);
  assert.equal(body.generationConfig.temperature, 0);
  assert.equal(body.generationConfig.thinkingConfig.thinkingLevel, 'minimal');
  assert.equal(body.generationConfig.responseMimeType, 'application/json');
  assert.deepEqual(body.generationConfig.responseJsonSchema, SCHEMA);
  assert.equal(init.headers['x-goog-api-key'], ENV.GEMINI_API_KEY);
});

test('Gemini quota on both models -> Groq 120b with same schema', async () => {
  const f = fakeFetch({
    'gemini-3.5-flash-lite': () => status(429),
    'gemini-3.6-flash': () => status(429),
    'openai/gpt-oss-120b': () => groqOk({ geri_okuma: 'groq' }),
  });
  const r = await runChain(REQ, ENV, deps(f), DEFAULT_CONFIG);
  assert.equal(r.ok, true);
  assert.equal(r.provider, 'groq');
  assert.equal(r.attempts, 3);
  const body = JSON.parse(f.calls[2].init.body);
  assert.equal(body.temperature, 0);
  assert.equal(body.reasoning_effort, 'low');
  assert.equal(body.include_reasoning, false);
  assert.deepEqual(body.response_format.json_schema.schema, SCHEMA);
  assert.equal(body.response_format.json_schema.strict, true);
});

test('5xx, network error, timeout and invalid JSON each move to the next model', async () => {
  const f = fakeFetch({
    'gemini-3.5-flash-lite': () => status(503),
    'gemini-3.6-flash': () => 'throw',
    'openai/gpt-oss-120b': () => 'hang',
    'openai/gpt-oss-20b': () => new Response(JSON.stringify({ choices: [{ message: { content: 'not json' } }] })),
  });
  const r = await runChain(REQ, ENV, deps(f), fastConfig);
  assert.deepEqual(r, { ok: false, code: 'PROVIDERS_UNAVAILABLE' });
  assert.equal(f.calls.length, 4);
});

test('all models out of quota -> QUOTA_EXHAUSTED', async () => {
  const all429 = Object.fromEntries(MODEL_CHAIN.map((s) => [s.model, () => status(429)]));
  const f = fakeFetch(all429);
  assert.deepEqual(await runChain(REQ, ENV, deps(f), DEFAULT_CONFIG), { ok: false, code: 'QUOTA_EXHAUSTED' });
});

test('other provider 4xx stops the chain (V-22)', async () => {
  const f = fakeFetch({ 'gemini-3.5-flash-lite': () => status(400) });
  assert.deepEqual(await runChain(REQ, ENV, deps(f), DEFAULT_CONFIG), { ok: false, code: 'UPSTREAM_REJECTED' });
  assert.equal(f.calls.length, 1);
});

test('JSON array or empty output is invalid', async () => {
  const f = fakeFetch({
    'gemini-3.5-flash-lite': () => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: '[1]' }] } }] })),
    'gemini-3.6-flash': () => new Response(JSON.stringify({ candidates: [{ finishReason: 'SAFETY' }] })),
    'openai/gpt-oss-120b': () => groqOk({ geri_okuma: 'ok' }),
  });
  const r = await runChain(REQ, ENV, deps(f), DEFAULT_CONFIG);
  assert.equal(r.model, 'openai/gpt-oss-120b');
});

test('missing Groq key: only Gemini steps run', async () => {
  const f = fakeFetch({});
  const r = await runChain(REQ, { GEMINI_API_KEY: 'k' }, deps(f), DEFAULT_CONFIG);
  assert.equal(r.code, 'PROVIDERS_UNAVAILABLE');
  assert.deepEqual(f.calls.map((c) => c.model), ['gemini-3.5-flash-lite', 'gemini-3.6-flash']);
});

test('no provider keys -> SERVER_MISCONFIGURED, no calls', async () => {
  const f = fakeFetch({});
  assert.deepEqual(await runChain(REQ, {}, deps(f), DEFAULT_CONFIG), { ok: false, code: 'SERVER_MISCONFIGURED' });
  assert.equal(f.calls.length, 0);
});

test('total deadline stops further attempts', async () => {
  const hang = () => 'hang';
  const f = fakeFetch(Object.fromEntries(MODEL_CHAIN.map((s) => [s.model, hang])));
  const cfg = { ...DEFAULT_CONFIG, timeouts: { attemptMs: 60, totalMs: 100, minAttemptMs: 50 } };
  const started = Date.now();
  const r = await runChain(REQ, ENV, deps(f), cfg);
  assert.equal(r.code, 'PROVIDERS_UNAVAILABLE');
  assert.ok(f.calls.length < 4);
  assert.ok(Date.now() - started < 1_000);
});
