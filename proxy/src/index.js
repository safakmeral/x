// Cloudflare Worker entry point. One handler per isolate, so the in-memory
// rate limiter is shared by requests served by this isolate.

import { createHandler } from './handler.js';

const handle = createHandler();

export default {
  fetch(request, env) {
    return handle(request, env);
  },
};
