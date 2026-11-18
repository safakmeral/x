import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchAccessCode, parseAccessCodes, readBearer } from '../src/auth.js';
import { SlidingWindowLimiter } from '../src/rateLimit.js';
import { RATE_LIMIT } from '../src/config.js';

test('access codes: short ones ignored, rotation via comma list', () => {
  assert.deepEqual(parseAccessCodes(' kisa , UzunKodBurada123 ,IkinciKod99999 ', 12),
    ['UzunKodBurada123', 'IkinciKod99999']);
  assert.deepEqual(parseAccessCodes(undefined, 12), []);
});

test('bearer header parsing', () => {
  const r = (h) => new Request('https://x.test', { headers: h ? { authorization: h } : {} });
  assert.equal(readBearer(r('Bearer abcdefghijklmn')), 'abcdefghijklmn');
  assert.equal(readBearer(r('Basic abc')), null);
  assert.equal(readBearer(r(null)), null);
});

test('access code match is exact', async () => {
  const codes = ['UzunKodBurada123', 'IkinciKod99999'];
  assert.equal(await matchAccessCode('IkinciKod99999', codes), 1);
  assert.equal(await matchAccessCode('IkinciKod9999', codes), -1);
  assert.equal(await matchAccessCode(null, codes), -1);
});

test('limiter: 20 per minute allowed, 21st rejected with retry hint', () => {
  const l = new SlidingWindowLimiter(RATE_LIMIT);
  for (let i = 0; i < 20; i++) assert.equal(l.take('k', 1000 + i).allowed, true);
  const r = l.take('k', 1100);
  assert.equal(r.allowed, false);
  assert.ok(r.retryAfterSec >= 1 && r.retryAfterSec <= 60);
});

test('limiter: window slides, old requests expire', () => {
  const l = new SlidingWindowLimiter(RATE_LIMIT);
  for (let i = 0; i < 20; i++) l.take('k', i * 1000);
  assert.equal(l.take('k', 59_999).allowed, false);
  assert.equal(l.take('k', 60_001).allowed, true);
});

test('limiter: normal human pace (one request every 3 s for 10 min) is never blocked', () => {
  const l = new SlidingWindowLimiter(RATE_LIMIT);
  for (let t = 0; t < 600_000; t += 3_000) assert.equal(l.take('k', t).allowed, true);
});

test('limiter: memory bounded by maxTrackedKeys', () => {
  const l = new SlidingWindowLimiter({ ...RATE_LIMIT, maxTrackedKeys: 3 });
  for (let i = 0; i < 10; i++) l.take(`k${i}`, i);
  assert.equal(l.hits.size, 3);
});
