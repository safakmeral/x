# 18 Leaky Thoughts: Large Reasoning Models Are Not Private Thinkers (Green 2025)
**Type:** benchmark + empirical study (probing and agentic settings, 17 models) · **Relevance:** high

## Core contribution (2-3 lines)
Shows that the reasoning trace (RT) of large reasoning models used as personal agents is a privacy risk: it contains user data the model was told not to output, it can leak into the final answer, and it can be extracted by prompt injection. Adds a benchmark (AirGapAgent-R) and a post-hoc mitigation (RANA) that trades utility for privacy.

## Key findings (bullets, with numbers)
- Reasoning models ignore anonymization instructions in the RT: placeholder use <1% for most models; best model (DeepSeek-R1) 5%. Private data appears in the RT in 19%-78% of traces.
- Leakage into answer: LRMs leak reasoning into the answer in 5.55% of cases on average; s1 26.4%; DeepSeek-R1 6.0%.
- Prompt-injection extraction of reasoning exposes more private fields than system-prompt extraction in 24.7% of cases on average; s1.1 49.5%.
- Test-time compute: CoT/reasoning raise utility on average but not privacy. Scaling the reasoning budget (175 to 1050 tokens) lowers utility (R1-Qwen-14B -7.8 pp; R1-Llama-70B -3.5 pp), raises answer privacy (+9.85 pp avg), and lowers RT privacy (+12.35 pp more RT use of private data).
- RANA (detect and replace leaks in the RT with placeholders before answering): utility -8.13 pp on average, privacy +3.13 pp; ineffective for QwQ and DeepSeek-R1.
- Annotation of 200 cases: 74.8% of RT leaks are simple RECOLLECTION; answer leaks mostly WRONG CONTEXT UNDERSTANDING (39.8%), RELATIVE SENSITIVITY (15.6%), GOOD FAITH (10.9%).
- Closed models (o4-mini, claude-4-sonnet): answer privacy 76.1 and 89.8; RT privacy lower (59.2, 66.5), though the traces are summaries.

## Implications for our app (bullets; tag each)
- [PRIVACY/KVKK] Assume any model reasoning can contain the user's personal data. Do not persist or log reasoning output, and do not forward it to third parties.
- [PRIVACY/KVKK] Minimize data sent to the model: send only the fields the current task needs (paper's privacy directive "share only the information necessary").
- [SAFETY] The paper's injection came from the user prompt. Text on the screen (popups, web pages, messages) is untrusted input and could carry the same instruction. This extension is our inference; the paper does not test on-screen injection.
- [AGENT] For Mode 1 actions, the answer channel should be parsed strictly (action JSON only), so that reasoning text cannot leak into a tap or spoken output.
- [LATENCY] Larger reasoning budgets increase both cost and leakage; a short-reasoning or non-reasoning model is safer for routine app steps.
- [EVAL] Use the paper's two-part privacy test (does the model share a field when it should not, and does it withhold when it should share).

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- RANA pattern: reason, run a PII detector on the RT, replace matches with placeholders, then answer. Detector used: gpt-4o-mini with structured outputs; a free-tier or on-device detector would be needed given our zero-budget rule.
- Reasoning-trigger detection: flag answers starting with "Okay,", "Alright,", "I need to" as leaked reasoning.
- Budget forcing / "NoThinking" as a control for latency tuning.
- Privacy scoring formula: privacy = Pr[does not share | sharing not appropriate]; utility = Pr[shares | sharing appropriate].

## Risks / warnings the paper raises
- Anonymizing reasoning hurts utility; it cannot be fully removed without cost.
- Some models ignore the placeholder instruction entirely (DeepSeek-R1, QwQ favor the prompt's data over the RT's).
- Longer reasoning increases exposure.
- Reasoning and answer are hard for models to separate; unfinished or repeated thinking appears in answers.
- Most experiments are open-weight models in a probing setup; agentic runs were costly (up to 18 h on 2 H100).

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Synthetic user profile set with appropriate/inappropriate sharing labels (their AirGapAgent-R construction method).
- AgentDAM-style task success vs. tasks with zero leakage (NS/NT and NP/NT) for our app's flows.
- Injection test: place instruction-like text in a mock screen and check whether personal fields appear in output.
- Leak rate in answers and in any logged reasoning, per model we choose.

## One-line citation
Green, T., Gubri, M., Puerto, H., Yun, S., & Oh, S. J. (2025). Leaky Thoughts: Large Reasoning Models Are Not Private Thinkers. arXiv:2506.15674v2.
