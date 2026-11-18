// Gemini generateContent adapter. Temperature 0, minimal thinking, JSON schema enforced.

export function buildGeminiRequest(step, request, apiKey, baseUrl) {
  const body = {
    systemInstruction: { parts: [{ text: request.system }] },
    contents: [{ role: 'user', parts: [{ text: request.user }] }],
    generationConfig: {
      temperature: 0,
      responseMimeType: 'application/json',
      responseJsonSchema: request.schema,
      thinkingConfig: { thinkingLevel: step.thinkingLevel },
    },
  };
  return {
    url: `${baseUrl}/${encodeURIComponent(step.model)}:generateContent`,
    init: {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
      body: JSON.stringify(body),
    },
  };
}

/** Returns the answer text (thought parts dropped) or null. */
export function extractGeminiText(data) {
  const parts = data?.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) return null;
  const text = parts
    .filter((p) => p && p.thought !== true && typeof p.text === 'string')
    .map((p) => p.text)
    .join('');
  return text.length > 0 ? text : null;
}
