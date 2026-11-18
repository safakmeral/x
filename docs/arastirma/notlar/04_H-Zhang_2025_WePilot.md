# 04 WePilot: Integrating Younger Family Members and Chatbot (Zhang 2025)
**Type:** system (with formative study and 12-pair evaluation) · **Relevance:** high

## Core contribution (2-3 lines)
A chatbot-mediated system in which a younger family member (YFM) and an AI agent jointly solve an older adult's (OA) smartphone problem. The agent turns YFM input into a confirmed solution stored in a reusable Function Library, then guides the OA on the phone via Android accessibility with step-by-step highlights or one-click-go execution, and reports progress back to the YFM.

## Key findings (bullets, with numbers)
- Formative study (11 OAs, 10 YFMs): four expectation gaps (detailed guidance vs quick resolution; immediate vs belated support; complex instructions vs weak comprehension; digital literacy mismatch).
- Evaluation: 12 OA-YFM pairs (OA 66-80, YFM 22-32), 6 tasks each, compared with phone-call support. Reported YFM time cost 34.34 (SD 10.87) to 20.00 (SD 6.57), about 42% lower (paired t = 7.73, p < 0.001, d = 1.82). OA time cost not significantly changed (34.34 to 28.54, p > 0.05), but more stable (SD 10.87 to 6.76).
- Operation accuracy 93.8% (361/385 operations). Explanation accuracy 4.9/5; reasonability 4.06/5 (82.5% rated 4 or 5). 33/36 trajectories (91.7%) completed.
- TAM: OA acceptance higher than phone-call baseline (median 35 vs 28, p < 0.01). YFM mental effort rose from 3.50 to 4.33 in the "less effort" direction (p < 0.05); willingness to help under time pressure 2.92 to 3.75 (p < 0.01).
- Preferences: 11 of 12 OAs favored step-by-step; 8 OAs preferred one-click-go. YFMs raised learning-effectiveness and "tool dependency" concerns about one-click-go, and security concerns (how to verify the solution came from the real family member).
- Errors: mainly icon-only controls (hamburger, three-dot menu); metaphors for the same concept were sometimes inconsistent across sessions.

## Implications for our app (bullets; tag each)
- [ELDERLY] Step-by-step mode with highlight plus voice, and a one-click-go mode for one-time setup tasks, maps directly to our Mode 2 and Mode 1. Their split (teach vs. do) is a design choice our app should expose to the user.
- [UX] Show the element the app will tap before tapping it, and let the user press it themselves instead (their DS2/DS4 pattern).
- [LATENCY] Wrong-tap feedback (vibration plus voice) was immediate; mirror this in our trail/feedback loop.
- [VOICE] Used iFLYTEK STT tuned for older speakers (silence interval 9 s, max 60 s) and Android native TTS. Turkish equivalents must be checked; the paper gives no Turkish results.
- [SAFETY] Privacy-related steps required the OA's own input in one-click-go; copy this rule for payments, passwords, and personal data.
- [MEMORY] Function Library (graph of confirmed paths, FAISS retrieval) and a history panel of past solutions displayed as question-style titles: direct analogue of our "learned tasks replayed without AI".
- [PRIVACY/KVKK] Identity of the helper was a security concern; any family-sharing feature needs an authentication step.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Confirmed-solution store: YFM (or user) confirms a path once; path is stored as a node graph with screen nodes and navigation edges; reused without an LLM call.
- Embedding retrieval over question-form titles (bge-large + FAISS) to match new requests to stored solutions.
- Dual guidance modes selected by problem type: "operation" (teach) vs "configuration" (one-click).
- Per-session metrics for the helper: completion time, click accuracy, attempts.
- Metaphor library for ICT terms, stored per user to keep wording consistent across sessions (their own proposed fix).

## Risks / warnings the paper raises
- Inconsistent metaphors confuse OAs; the authors recommend a per-user metaphor library.
- Indirect identity verification when a bot relays help; the paper calls for supporter-authentication strategies.
- One-click-go may reduce learning and cause tool dependency.
- Reduced direct family contact ("I miss the detailed explanations my daughter used to give").
- Small, single-culture lab study (12 pairs, short period); no long-term data; OA-side time gain not significant.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Two-stage evaluation: intrinsic (operation accuracy, explanation accuracy and reasonability on 5-point scale, solution-path alignment) and user study (TAM, time cost for both user and helper).
- Stability metric: SD of task time across tasks, not only the mean.
- Baseline comparison against a phone-call or video-call alternative.
- Count of wrong taps per task and recovery time after an error.

## One-line citation
Zhang, H., Zhang, P., Chen, Y., Guo, M., Gu, H., Lu, T., & Gu, N. (2025). WePilot: Integrating Younger Family Members and Chatbot to Support Older Adults Learning Smartphone Usage. Proc. ACM HCI 9(7), CSCW522. https://doi.org/10.1145/3757703
