// Request pipeline: route -> access code -> rate limit -> body checks -> chain.
// Nothing here logs; request/response bodies are never written anywhere.
// Only the validated fields are forwarded; client headers (IP etc.) are not.

import { ACCESS_CODES_NAME, DEFAULT_CONFIG, ENDPOINT_PATH } from './config.js';
import { matchAccessCode, parseAccessCodes, readBearer } from './auth.js';
import { SlidingWindowLimiter } from './rateLimit.js';
import { validateRequestBody } from './validate.js';
import { runChain } from './chain.js';
import { errorResponse, successResponse } from './http.js';

async function readBody(request, limits) {
  const declared = Number(request.headers.get('content-length'));
  if (Number.isFinite(declared) && declared > limits.maxBodyBytes) return { tooLarge: true };
  const text = await request.text();
  if (new TextEncoder().encode(text).length > limits.maxBodyBytes) return { tooLarge: true };
  try {
    return { parsed: JSON.parse(text) };
  } catch {
    return { invalid: true };
  }
}

export function createHandler({
  fetchImpl = (url, init) => fetch(url, init),
  now = () => Date.now(),
  config = DEFAULT_CONFIG,
} = {}) {
  const limiter = new SlidingWindowLimiter(config.rateLimit);
  const deps = { fetchImpl, now };

  return async function handle(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== ENDPOINT_PATH) return errorResponse('NOT_FOUND');
    if (request.method !== 'POST') return errorResponse('METHOD_NOT_ALLOWED', {}, { allow: 'POST' });

    const codes = parseAccessCodes(env?.[ACCESS_CODES_NAME], config.limits.minAccessCodeLength);
    if (codes.length === 0) return errorResponse('SERVER_MISCONFIGURED');
    const codeIndex = await matchAccessCode(readBearer(request), codes);
    if (codeIndex < 0) return errorResponse('UNAUTHORIZED');

    const gate = limiter.take(`code:${codeIndex}`, now());
    if (!gate.allowed) {
      return errorResponse(
        'RATE_LIMITED',
        { retry_after_s: gate.retryAfterSec },
        { 'retry-after': String(gate.retryAfterSec) },
      );
    }

    const contentType = request.headers.get('content-type') ?? '';
    if (!contentType.toLowerCase().startsWith('application/json')) {
      return errorResponse('UNSUPPORTED_MEDIA_TYPE');
    }
    const body = await readBody(request, config.limits);
    if (body.tooLarge) return errorResponse('PAYLOAD_TOO_LARGE');
    if (body.invalid) return errorResponse('INVALID_REQUEST');

    const checked = validateRequestBody(body.parsed, config.limits);
    if (!checked.ok) {
      return errorResponse(checked.code, checked.field ? { field: checked.field } : {});
    }

    const outcome = await runChain(checked.value, env, deps, config);
    if (!outcome.ok) return errorResponse(outcome.code);
    return successResponse(outcome);
  };
}
