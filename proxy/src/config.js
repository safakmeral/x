// Single configuration point of the proxy (G-33). Model names live ONLY here,
// never in the Android app (V-22). Change the chain here and redeploy.

/** Version of the JSON contract between the app and the proxy. */
export const API_VERSION = 1;

/** The only endpoint. */
export const ENDPOINT_PATH = '/v1/generate';

/**
 * Fallback chain (docs/DOGRULAMALAR.md section 5). Each step is tried in order;
 * the next one is used only on 429 / 5xx / timeout / invalid JSON.
 * Gemini quota is per model, so chaining two Gemini models adds capacity.
 */
export const MODEL_CHAIN = Object.freeze([
  Object.freeze({ provider: 'gemini', model: 'gemini-3.5-flash-lite', thinkingLevel: 'minimal' }),
  Object.freeze({ provider: 'gemini', model: 'gemini-3.6-flash', thinkingLevel: 'minimal' }),
  Object.freeze({ provider: 'groq', model: 'openai/gpt-oss-120b', reasoningEffort: 'low' }),
  Object.freeze({ provider: 'groq', model: 'openai/gpt-oss-20b', reasoningEffort: 'low' }),
]);

/** Provider base URLs (official public APIs). */
export const PROVIDER_URLS = Object.freeze({
  gemini: 'https://generativelanguage.googleapis.com/v1beta/models',
  groq: 'https://api.groq.com/openai/v1/chat/completions',
});

/** Name of the secret (environment variable) that holds each provider key. */
export const PROVIDER_KEY_NAMES = Object.freeze({
  gemini: 'GEMINI_API_KEY',
  groq: 'GROQ_API_KEY',
});

/** Secret holding the access code(s), comma separated (to allow rotation). */
export const ACCESS_CODES_NAME = 'ACCESS_CODES';

export const LIMITS = Object.freeze({
  maxBodyBytes: 32 * 1024,
  maxSystemChars: 8000,
  maxUserChars: 16000,
  maxSchemaBytes: 8 * 1024,
  maxSchemaDepth: 24,
  minAccessCodeLength: 12,
  /** Longest run of base64-like characters allowed in any text (binary guard). */
  maxBase64Run: 200,
});

/**
 * Short-window abuse guard only (V-19): 20 requests per minute per access code.
 * A person talking to the assistant never reaches this.
 */
export const RATE_LIMIT = Object.freeze({
  maxRequests: 20,
  windowMs: 60_000,
  maxTrackedKeys: 100,
});

/** Short timeouts (speed principle, V-06). */
export const TIMEOUTS = Object.freeze({
  attemptMs: 6_000,
  totalMs: 15_000,
  /** Do not start a new attempt with less time than this left. */
  minAttemptMs: 1_000,
});

export const DEFAULT_CONFIG = Object.freeze({
  modelChain: MODEL_CHAIN,
  providerUrls: PROVIDER_URLS,
  limits: LIMITS,
  rateLimit: RATE_LIMIT,
  timeouts: TIMEOUTS,
});
