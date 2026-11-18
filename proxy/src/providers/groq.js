// Groq (OpenAI-compatible) chat completions adapter. Same schema as Gemini.
// Zero Data Retention is an organization setting in the Groq console (see docs).

export function buildGroqRequest(step, request, apiKey, baseUrl) {
  const body = {
    model: step.model,
    messages: [
      { role: 'system', content: request.system },
      { role: 'user', content: request.user },
    ],
    temperature: 0,
    reasoning_effort: step.reasoningEffort,
    include_reasoning: false,
    response_format: {
      type: 'json_schema',
      json_schema: { name: 'yanit', schema: request.schema, strict: true },
    },
  };
  return {
    url: baseUrl,
    init: {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${apiKey}` },
      body: JSON.stringify(body),
    },
  };
}

/** Returns the answer text (reasoning ignored) or null. */
export function extractGroqText(data) {
  const content = data?.choices?.[0]?.message?.content;
  return typeof content === 'string' && content.length > 0 ? content : null;
}
