// Privacy guards: no logging, logs disabled in config, no extra identifiers
// forwarded, secrets kept out of the repository.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ENV, geminiOk, groqOk, handlerWith, makeRequest, status } from './helpers.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? sourceFiles(p) : p.endsWith('.js') ? [p] : [];
  });
}

test('source code never calls console or debugger', () => {
  const files = sourceFiles(join(ROOT, 'src'));
  assert.ok(files.length >= 8);
  for (const f of files) {
    const code = readFileSync(f, 'utf8');
    assert.ok(!/\bconsole\s*[.[]/.test(code), `console used in ${f}`);
    assert.ok(!/\bdebugger\b/.test(code), `debugger in ${f}`);
  }
});

test('nothing is printed during success and failure flows', async (t) => {
  const printed = [];
  for (const m of ['log', 'info', 'warn', 'error', 'debug', 'trace']) {
    t.mock.method(console, m, (...args) => printed.push(args));
  }
  const { handle } = handlerWith({
    'gemini-3.5-flash-lite': () => status(429),
    'gemini-3.6-flash': () => 'throw',
    'openai/gpt-oss-120b': () => groqOk({ geri_okuma: 'x' }),
  });
  await handle(makeRequest(), ENV);
  await handle(makeRequest({ code: 'YanlisKod123456' }), ENV);
  await handle(makeRequest({ rawBody: '{bad' }), ENV);
  assert.equal(printed.length, 0);
});

test('wrangler.toml disables observability and invocation logs, holds no secrets', () => {
  const toml = readFileSync(join(ROOT, 'wrangler.toml'), 'utf8');
  const obs = toml.split('[observability]')[1].split('[observability.logs]')[0];
  assert.match(obs, /^enabled\s*=\s*false$/m);
  const logs = toml.split('[observability.logs]')[1];
  assert.match(logs, /^enabled\s*=\s*false$/m);
  assert.match(logs, /^invocation_logs\s*=\s*false$/m);
  assert.ok(!/^\s*(GEMINI_API_KEY|GROQ_API_KEY|ACCESS_CODES)\s*=/m.test(toml));
  assert.ok(!/^\s*\[vars\]/m.test(toml));
});

test('.dev.vars is git-ignored, only the example is kept', () => {
  const ignore = readFileSync(join(ROOT, '.gitignore'), 'utf8').split(/\r?\n/);
  assert.ok(ignore.includes('.dev.vars'));
  assert.ok(ignore.includes('!.dev.vars.example'));
});

test('client IP and headers are not forwarded; only validated fields are sent', async () => {
  const ip = '203.0.113.77';
  const { handle, fetchImpl } = handlerWith({
    'gemini-3.5-flash-lite': () => status(503),
    'gemini-3.6-flash': () => status(503),
    'openai/gpt-oss-120b': () => groqOk({ geri_okuma: 'x' }),
  });
  await handle(makeRequest({
    headers: { 'cf-connecting-ip': ip, 'x-forwarded-for': ip, 'user-agent': 'cihaz-123', 'x-device-id': 'abc' },
  }), ENV);
  for (const { url, init } of fetchImpl.calls) {
    const keys = Object.keys(init.headers).sort();
    const expected = url.includes('generativelanguage')
      ? ['content-type', 'x-goog-api-key']
      : ['authorization', 'content-type'];
    assert.deepEqual(keys, expected);
    const sent = url + init.body + JSON.stringify(init.headers);
    for (const v of [ip, 'cihaz-123', 'abc"']) assert.ok(!sent.includes(v));
  }
  const groqBody = JSON.parse(fetchImpl.calls[2].init.body);
  assert.deepEqual(Object.keys(groqBody).sort(),
    ['include_reasoning', 'messages', 'model', 'reasoning_effort', 'response_format', 'temperature']);
  assert.ok(!('user' in groqBody));
});

test('success response carries no reasoning or thought text', async () => {
  const { handle } = handlerWith({ 'gemini-3.5-flash-lite': () => geminiOk({ geri_okuma: 'x' }) });
  const text = await (await handle(makeRequest(), ENV)).text();
  assert.ok(!text.includes('dusunce'));
});
