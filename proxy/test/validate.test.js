import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateRequestBody } from '../src/validate.js';
import { LIMITS } from '../src/config.js';
import { validBody } from './helpers.js';

const check = (body) => validateRequestBody(body, LIMITS);

test('valid body passes and keeps only the expected fields', () => {
  const r = check(validBody());
  assert.equal(r.ok, true);
  assert.deepEqual(Object.keys(r.value).sort(), ['schema', 'system', 'user']);
});

test('image or any unexpected field is rejected', () => {
  for (const extra of [{ image: 'abc' }, { inline_data: {} }, { model: 'x' }, { temperature: 1 }]) {
    const r = check(validBody(extra));
    assert.equal(r.ok, false);
    assert.equal(r.code, 'INVALID_REQUEST');
    assert.equal(r.field, 'unexpected_field');
  }
});

test('base64 data URI or long base64 run in text is rejected', () => {
  const uri = check(validBody({ user: 'bak: data:image/png;base64,iVBORw0KGgo=' }));
  assert.equal(uri.code, 'BINARY_NOT_ALLOWED');
  const blob = check(validBody({ system: 'A'.repeat(LIMITS.maxBase64Run + 1) }));
  assert.equal(blob.code, 'BINARY_NOT_ALLOWED');
  const inSchema = check(validBody({ schema: { type: 'object', description: 'Q'.repeat(500) } }));
  assert.equal(inSchema.code, 'BINARY_NOT_ALLOWED');
});

test('wrong version is rejected', () => {
  assert.equal(check(validBody({ v: 2 })).code, 'UNSUPPORTED_VERSION');
  const { v, ...noVersion } = validBody();
  assert.equal(check(noVersion).code, 'UNSUPPORTED_VERSION');
});

test('missing, empty, too long or non-string texts are rejected', () => {
  assert.equal(check(validBody({ user: '' })).field, 'user');
  assert.equal(check(validBody({ user: 42 })).field, 'user');
  assert.equal(check(validBody({ system: '   ' })).field, 'system');
  assert.equal(check(validBody({ user: 'a '.repeat(LIMITS.maxUserChars) })).field, 'user');
});

test('schema must be a small object', () => {
  assert.equal(check(validBody({ schema: [] })).field, 'schema');
  assert.equal(check(validBody({ schema: 'x' })).field, 'schema');
  const big = { type: 'object', properties: {} };
  for (let i = 0; i < 600; i++) big.properties[`alan_${i}`] = { type: 'string' };
  assert.equal(check(validBody({ schema: big })).field, 'schema');
});

test('non-object bodies are rejected', () => {
  for (const b of [null, [], 'x', 3]) assert.equal(check(b).code, 'INVALID_REQUEST');
});
