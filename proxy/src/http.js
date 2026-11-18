import { API_VERSION } from './config.js';

/** Error codes of the v1 contract and their HTTP status. */
export const ERROR_STATUS = Object.freeze({
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,
  UNAUTHORIZED: 401,
  RATE_LIMITED: 429,
  UNSUPPORTED_MEDIA_TYPE: 415,
  PAYLOAD_TOO_LARGE: 413,
  INVALID_REQUEST: 400,
  UNSUPPORTED_VERSION: 400,
  BINARY_NOT_ALLOWED: 400,
  QUOTA_EXHAUSTED: 503,
  PROVIDERS_UNAVAILABLE: 503,
  UPSTREAM_REJECTED: 502,
  SERVER_MISCONFIGURED: 500,
});

/** Codes after which the app should continue in on-device mode. */
export const ON_DEVICE_CODES = Object.freeze(new Set([
  'QUOTA_EXHAUSTED',
  'PROVIDERS_UNAVAILABLE',
  'UPSTREAM_REJECTED',
  'SERVER_MISCONFIGURED',
]));

const BASE_HEADERS = Object.freeze({
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
});

export function jsonResponse(status, body, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...BASE_HEADERS, ...extraHeaders },
  });
}

/**
 * Error body: { v, ok:false, error, on_device?, field?, retry_after_s? }.
 * Never contains request text, provider bodies or secrets.
 */
export function errorResponse(code, extra = {}, extraHeaders = {}) {
  const status = ERROR_STATUS[code];
  const body = { v: API_VERSION, ok: false, error: code };
  if (ON_DEVICE_CODES.has(code)) body.on_device = true;
  return jsonResponse(status, { ...body, ...extra }, extraHeaders);
}

export function successResponse({ result, provider, model, attempts }) {
  return jsonResponse(200, { v: API_VERSION, ok: true, result, provider, model, attempts });
}
