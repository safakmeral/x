// Strict validation of the v1 request body. Only the expected fields pass;
// images, files or any other field are rejected.

import { API_VERSION } from './config.js';

const ALLOWED_KEYS = new Set(['v', 'system', 'user', 'schema']);
const DATA_URI = /data:[a-z]+\/[a-z0-9.+-]+;base64,/i;

function isPlainObject(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function fail(code, field) {
  return field === undefined ? { ok: false, code } : { ok: false, code, field };
}

function looksBinary(text, maxRun) {
  if (DATA_URI.test(text)) return true;
  const longRun = new RegExp(`[A-Za-z0-9+/=_-]{${maxRun + 1},}`);
  return longRun.test(text);
}

/** Walks a JSON value; returns false if too deep or any string looks binary. */
function schemaIsSafe(value, depth, limits) {
  if (depth > limits.maxSchemaDepth) return false;
  if (typeof value === 'string') return !looksBinary(value, limits.maxBase64Run);
  if (Array.isArray(value)) return value.every((v) => schemaIsSafe(v, depth + 1, limits));
  if (isPlainObject(value)) {
    return Object.entries(value).every(
      ([k, v]) => !looksBinary(k, limits.maxBase64Run) && schemaIsSafe(v, depth + 1, limits),
    );
  }
  return true;
}

function checkText(body, field, maxChars, limits) {
  const value = body[field];
  if (typeof value !== 'string' || value.trim().length === 0 || value.length > maxChars) {
    return fail('INVALID_REQUEST', field);
  }
  if (looksBinary(value, limits.maxBase64Run)) return fail('BINARY_NOT_ALLOWED', field);
  return null;
}

/**
 * Validates a parsed body. Returns { ok:true, value:{system,user,schema} }
 * or { ok:false, code, field? } where field is one of our own field names.
 */
export function validateRequestBody(body, limits) {
  if (!isPlainObject(body)) return fail('INVALID_REQUEST');
  for (const key of Object.keys(body)) {
    if (!ALLOWED_KEYS.has(key)) return fail('INVALID_REQUEST', 'unexpected_field');
  }
  if (body.v !== API_VERSION) return fail('UNSUPPORTED_VERSION', 'v');

  const textError =
    checkText(body, 'system', limits.maxSystemChars, limits) ??
    checkText(body, 'user', limits.maxUserChars, limits);
  if (textError) return textError;

  const { schema } = body;
  if (!isPlainObject(schema)) return fail('INVALID_REQUEST', 'schema');
  if (new TextEncoder().encode(JSON.stringify(schema)).length > limits.maxSchemaBytes) {
    return fail('INVALID_REQUEST', 'schema');
  }
  if (!schemaIsSafe(schema, 0, limits)) return fail('BINARY_NOT_ALLOWED', 'schema');

  return { ok: true, value: { system: body.system, user: body.user, schema } };
}
