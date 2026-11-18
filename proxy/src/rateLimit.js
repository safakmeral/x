// In-memory sliding-window limiter. Lives in one Worker isolate, so it is an
// approximate abuse guard, not an exact quota: Cloudflare may run several
// isolates (and locations), each with its own counter. No KV / Durable Object
// is used (KV free tier: 1,000 writes/day would run out; nothing is persisted).

export class SlidingWindowLimiter {
  constructor({ maxRequests, windowMs, maxTrackedKeys }) {
    this.maxRequests = maxRequests;
    this.windowMs = windowMs;
    this.maxTrackedKeys = maxTrackedKeys;
    /** key -> ascending timestamps (ms) of accepted requests */
    this.hits = new Map();
  }

  /** Records a request for key at time now; returns { allowed, retryAfterSec? }. */
  take(key, now) {
    const cutoff = now - this.windowMs;
    const recent = (this.hits.get(key) ?? []).filter((t) => t > cutoff);
    this.hits.delete(key);
    if (recent.length >= this.maxRequests) {
      this.hits.set(key, recent);
      const retryAfterSec = Math.max(1, Math.ceil((recent[0] + this.windowMs - now) / 1000));
      return { allowed: false, retryAfterSec };
    }
    recent.push(now);
    this.hits.set(key, recent);
    this.#evictOldest();
    return { allowed: true };
  }

  #evictOldest() {
    while (this.hits.size > this.maxTrackedKeys) {
      const oldestKey = this.hits.keys().next().value;
      this.hits.delete(oldestKey);
    }
  }
}
