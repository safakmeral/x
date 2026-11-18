// Access code check. The code is a shared secret entered once on the device;
// it only keeps strangers from spending the free quota (see README limits).

const encoder = new TextEncoder();

async function sha256(text) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(text)));
}

/** Constant-time comparison of two equal-length digests. */
function equalBytes(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

/** Splits the ACCESS_CODES secret; codes shorter than minLength are ignored. */
export function parseAccessCodes(raw, minLength) {
  if (typeof raw !== 'string') return [];
  return raw.split(',').map((s) => s.trim()).filter((s) => s.length >= minLength);
}

/** Returns the token of an "Authorization: Bearer <code>" header, or null. */
export function readBearer(request) {
  const header = request.headers.get('authorization');
  if (!header) return null;
  const match = /^Bearer\s+(\S+)$/.exec(header.trim());
  return match ? match[1] : null;
}

/**
 * Returns the index of the matching configured code, or -1.
 * All codes are compared (no early exit) to keep timing uniform.
 */
export async function matchAccessCode(presented, codes) {
  if (!presented) return -1;
  const presentedHash = await sha256(presented);
  let found = -1;
  for (let i = 0; i < codes.length; i++) {
    const same = equalBytes(presentedHash, await sha256(codes[i]));
    if (same && found === -1) found = i;
  }
  return found;
}
