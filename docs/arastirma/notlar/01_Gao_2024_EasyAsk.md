# 01 EasyAsk: In-App Contextual Tutorial Search Assistant for Older Adults with Voice and Touch Inputs (Gao 2024)
**Type:** user study / system · **Relevance:** high

## Core contribution (2-3 lines)
An Android method where an older user asks a spoken question about the current screen, optionally touching UI elements to add context. An LLM formats the query, retrieves one matching interactive (masked-overlay, step-by-step) tutorial from a 578-tutorial DB, and returns exactly one result. Built from a 16-person formative study of older adults' help-seeking.

## Key findings (bullets, with numbers)
- Formative study (16 older adults, 240 queries, 14 apps): 93.75% ask people around them, 31.25% give up, 12.5% explore alone, 6.25% internet search. Users fear making mistakes and prefer immediate, step-by-step answers.
- Queries are incomplete, redundant, ambiguous and over-specific; participants found describing problems tiring.
- User experiment (18 older adults, 9F/9M, mean age 67): EasyAsk accuracy 98.94% (1 wrong of 94) vs. Baidu 59.57% and Mailing 36.17%.
- Ablation: raw voice 68.09%; formatting only 69.15%; context only 94.68%; context + formatting 98.94%.
- Prompted during tasks: EasyAsk 28.70%, Baidu 29.80%, Mailing 53.20%.
- Searching and browsing time: EasyAsk significantly lowest vs. both baselines (p < 0.001). Learning time not significantly different (Friedman).
- NASA-TLX-style 7-point ratings (lower = better): mental demand 1.78 vs. 3.39 (Mailing) / 3.22 (Baidu); frustration 1.24 vs. 2.22 (Mailing).
- Single-result design reduced fatigue; participants said they would use it daily.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [ELDERLY] Strong evidence that older users prefer asking "how do I do this?" on the current screen over typing or browsing results. Supports our floating bubble + voice entry point.
- [VOICE] Touching the confusing element as extra context ("how to delete this?" while tapping the floating window) is a cheap way to disambiguate vague speech. Adopt it.
- [UX] Return one answer, not a list of results; the paper ties result count to lower cognitive load.
- [AGENT] Interactive tutorials as masked overlays on the real UI match our Guide mode; reuse this instead of inventing a new format.
- [PRIVACY/KVKK] Paper's own limitation: cloud LLM calls and logs. Plan for on-device or minimal-context queries.
- [EVAL] Use the paper's measures: accuracy of top result, prompt count, search and browse time, NASA-TLX ratings.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Context injection: ASR text + current app package + interface info (via Accessibility) + touched element, merged into one query.
- Query formatting prompt (few-shot): keep app-specific terms unchanged; generalize specific wording ("return these shoes because I don't like them" -> "return this item"); drop redundancy. Give GPT-4 examples.
- Retrieval: tutorial names are unique IDs; knowledge doc of names + LLM-generated descriptions; FAISS similarity search over chunks; LLM must always pick one tutorial name, which maps one-to-one to a stored script.
- Tutorial format: recorded operation sequence (taps, swipes, control IDs or coordinates) saved as JSON; overlay masks guide each step.
- Auto-stop: detect when the user's speech halts before running search.

## Risks / warnings the paper raises
- Accuracy depends on query quality and the tutorial DB; DB of 578 tutorials across 14 apps is small, and the paper says it is "certainly insufficient".
- Single result means a wrong answer forces a new question (no multi-turn recovery yet).
- Subjective accuracy dropped when the right result was not first (users missed it).
- Older adults lack a clear model of mobile privacy; the app sends data to the cloud without explicit notice.
- Familiarity matters: two participants were uneasy with an unfamiliar method.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Top-1 accuracy on a set of real older-user queries per app; ablation with and without context and formatting.
- Count of prompts or help-interventions needed per task.
- Time to first correct step and total task time, split into search and browsing.
- NASA-TLX 7-point subjective load, compared against a baseline (e.g., asking a family member or searching Google).
- Mistaken-match cases logged for error analysis.

## One-line citation
Gao W, Du K, Luo Y, Shi W, Yu C, Shi Y. "EasyAsk: An In-App Contextual Tutorial Search Assistant for Older Adults with Voice and Touch Inputs." Proc. ACM IMWUT 8(3), Article 102, Sept. 2024.
