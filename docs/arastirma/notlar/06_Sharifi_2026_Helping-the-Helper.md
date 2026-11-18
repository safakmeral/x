# 06 Helping the Helper (Sharifi 2026)
**Type:** user study (diary study + two controlled experiments) with an LLM pipeline · **Relevance:** high

## Core contribution (2-3 lines)
Diary study (27 older adults, 57 queries) identifies four articulation barriers in older adults' tech-support queries: verbosity, incompleteness, over-specification, under-specification. A few-shot prompt chain (GPT-4o; OS-ATLAS as local comparison) asks up to 3 contextual follow-up questions, paraphrases the query, and gives one confident solution. Also releases STAQ, a 514-item synthetic dataset of older-adult-style queries.

## Key findings (bullets, with numbers)
- Diary study: 57 queries from 27 older adults (median age 66, range 60-87), 34 apps. Categories (a query can have several): verbosity 9, over-specification 12, under-specification 13, incompleteness 24. Most frequent gap: missing device type, OS, or app name.
- Older adults often asked a helper first; they contacted the researchers only when unresolved. Only 9/57 queries included a screenshot; none used screen or video recording.
- Paraphrase quality (expert rated, 48 queries): GPT-4o correct 68.8%, partially 25%, incorrect 6.2%. OS-ATLAS correct 12.6%.
- Solution accuracy (Table 3): original query + Google = 35.4% correct; GPT-4o paraphrase + Google = 68.8%; GPT-4o paraphrase + GPT-4o solution = 68.8% correct, 14.6% partial, 16.7% incorrect.
- Younger helpers (n=48, 33 query pairs): "understood" 93.7% for rephrased vs 65.8% for original (McNemar p<.001). Confidence and ease also significantly higher (Wilcoxon p<.001). Lower digital-literacy helpers gained most.
- Older adults (n=34, median age 72, MDPQ-14 moderate-high): 79.8% answered "yes" that they could answer the generated follow-up questions (10.0% "maybe"); ease of answering moderate (Mdn 4/5). 81.6% could follow generated solutions (13.1% "maybe"); confidence high (Mdn 5/5), ease moderate (Mdn 4/5).
- Synthetic STAQ: 50 sampled items, 40 rated "likely" by two aging/tech experts (Cohen's kappa 0.83). Type-token ratio 0.858 (synthetic older-style) vs 0.842 (real).
- Model confidence thresholds used to reduce hallucination: 90% for GPT-4o, 80% for OS-ATLAS (OS-ATLAS over-triggered safety text at 90%).

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [ELDERLY][VOICE] Spoken problem statements from our users will show the same four barriers. Add a normalization step before planning: detect missing device/app context and ask at most ~3 short follow-up questions.
- [AGENT] Paraphrase the spoken request into one concise, complete task sentence, and show it back to the user for confirmation before Do-it or Guide mode starts.
- [UX] Authors observed that older adults prefer "do-it-with-me" help over full autonomy (their cited work [28]). Supports our Guide mode as a default and asking before Do-it on uncertain requests.
- [ELDERLY] Users rated solutions as actionable but ease of following only moderate. Keep steps few and single-option (the authors told the model to avoid multiple alternatives).
- [PRIVACY/KVKK] The pipeline sends personal queries to a cloud LLM API. The authors themselves flag this. Any cloud call in our app needs KVKK-compliant handling (consent, minimization, no raw screen data unless needed).
- [MEMORY] Store confirmed paraphrases of frequent requests so repeat requests skip the LLM (aligns with replay of learned tasks).
- [EVAL] Their evaluation design (paraphrase correctness, solution correctness, comprehension and ease ratings from a second group) can be reused for our app's request-understanding tests.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Prompt chain: (1) find missing context and generate up to 3 follow-up questions; (2) collect answers; (3) paraphrase into a concise, search-ready question; (4) produce one solution only if confidence is above a threshold, otherwise say "I do not know".
- Confidence gating: produce a solution only above a self-reported confidence threshold; tune per model (they used 90% for GPT-4o, 80% for OS-ATLAS).
- Few-shot exemplars: three example queries reused across steps, each exemplar annotated with the barrier type.
- Metrics: paraphrase rated correct / partially correct / incorrect; solution rated correct / partial / incorrect; Cohen's kappa for raters; McNemar for paired understood yes/no; TOST for equivalence between helper groups; SBERT + PCA overlap for synthetic-data fidelity.
- STAQ-style synthetic test set: generate older-adult-style Turkish requests from real collected examples (not from stereotypes), then validate with domain experts.

## Risks / warnings the paper raises
- Small, self-selected, English-speaking sample; all had prior smart-device experience. Results may not hold for true digital novices or people with cognitive impairment.
- Evaluation was static (rated queries), not live multi-turn conversation.
- Dependence on a proprietary model; output may change silently across API versions (they did not pin a snapshot).
- Synthetic data can produce stereotypes or hallucinations if not anchored to real data.
- Paraphrase and solution correctness are expert-judged; OS-ATLAS showed that a weak model can produce confident but wrong paraphrases.
- Older adults cannot reliably judge whether a paraphrase is correct, so the authors did not test that with them.
- Future work: on-device or local processing for privacy is explicitly suggested but not tested.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Paraphrase accuracy on a set of spoken Turkish elderly requests (correct / partial / incorrect), with two raters and kappa.
- End-to-end task success: correct solution rate with original vs normalized request, using our own pipeline.
- Helper-comprehension test: a second group (younger Turkish speakers) rates understanding of original vs rephrased requests (McNemar).
- Older-adult self-report: can you answer the follow-up question? (yes / maybe / no), confidence and ease (5-point), plus MDPQ-type proficiency measure.
- Count of follow-up questions needed before a solution (fewer is better, with a cap of 3).

## One-line citation
Sharifi, H., Shomee, H. H., Lamar, M., Medya, S., & Chattopadhyay, D. (2026). Helping the Helper: LLM-Assisted Problem Articulation for Older Adults Seeking Technology Support. ASSETS '26. arXiv:2601.10018.
