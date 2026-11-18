// Runs the model chain: first success wins; quota/5xx/timeout/invalid JSON
// moves to the next step; any other provider 4xx stops (V-22).

import { PROVIDER_KEY_NAMES } from './config.js';
import { buildGeminiRequest, extractGeminiText } from './providers/gemini.js';
import { buildGroqRequest, extractGroqText } from './providers/groq.js';

const ADAPTERS = Object.freeze({
  gemini: { build: buildGeminiRequest, extract: extractGeminiText },
  groq: { build: buildGroqRequest, extract: extractGroqText },
});

class TimeoutError extends Error {}

/** Maps a provider HTTP status to an outcome kind. */
export function classifyStatus(status) {
  if (status === 429) return 'quota';
  if (status >= 500) return 'unavailable';
  return 'rejected';
}

function parseObject(text) {
  try {
    const value = JSON.parse(text);
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
}

async function attempt(adapter, url, init, fetchImpl, signal) {
  const res = await fetchImpl(url, { ...init, signal });
  if (!res.ok) {
    try { await res.body?.cancel(); } catch { /* ignore */ }
    return { kind: classifyStatus(res.status) };
  }
  let data;
  try {
    data = await res.json();
  } catch {
    return { kind: 'unavailable' };
  }
  const text = adapter.extract(data);
  const value = text === null ? null : parseObject(text);
  return value === null ? { kind: 'unavailable' } : { kind: 'ok', value };
}

/** One provider call bounded by timeoutMs (covers headers and body). */
async function callStep(step, request, apiKey, deps, timeoutMs, config) {
  const adapter = ADAPTERS[step.provider];
  const { url, init } = adapter.build(step, request, apiKey, config.providerUrls[step.provider]);
  const controller = new AbortController();
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => {
      controller.abort();
      reject(new TimeoutError());
    }, timeoutMs);
  });
  try {
    return await Promise.race([
      attempt(adapter, url, init, deps.fetchImpl, controller.signal),
      timeout,
    ]);
  } catch {
    // Timeout or network error: never inspect or forward the error text.
    return { kind: 'unavailable' };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Returns { ok:true, result, provider, model, attempts }
 * or { ok:false, code } with code QUOTA_EXHAUSTED | PROVIDERS_UNAVAILABLE |
 * UPSTREAM_REJECTED | SERVER_MISCONFIGURED.
 */
export async function runChain(request, env, deps, config) {
  const deadline = deps.now() + config.timeouts.totalMs;
  const outcomes = [];
  let attempts = 0;
  for (const step of config.modelChain) {
    const apiKey = env?.[PROVIDER_KEY_NAMES[step.provider]];
    if (typeof apiKey !== 'string' || apiKey.length === 0) continue;
    const remaining = deadline - deps.now();
    if (remaining < config.timeouts.minAttemptMs) {
      outcomes.push('unavailable');
      break;
    }
    attempts += 1;
    const timeoutMs = Math.min(config.timeouts.attemptMs, remaining);
    const outcome = await callStep(step, request, apiKey, deps, timeoutMs, config);
    if (outcome.kind === 'ok') {
      return { ok: true, result: outcome.value, provider: step.provider, model: step.model, attempts };
    }
    if (outcome.kind === 'rejected') return { ok: false, code: 'UPSTREAM_REJECTED' };
    outcomes.push(outcome.kind);
  }
  if (attempts === 0) return { ok: false, code: 'SERVER_MISCONFIGURED' };
  const allQuota = outcomes.every((k) => k === 'quota');
  return { ok: false, code: allQuota ? 'QUOTA_EXHAUSTED' : 'PROVIDERS_UNAVAILABLE' };
}
