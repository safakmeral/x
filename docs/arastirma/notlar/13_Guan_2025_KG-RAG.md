# 13 KG-RAG: Knowledge Graph-driven RAG for GUI Agent Decision-Making (Guan 2025)
**Type:** system with benchmarks (EMNLP 2025) · **Relevance:** high

## Core contribution (2-3 lines)
Offline, an app's UI Transition Graph (UTG) is explored automatically, then an LLM searches it for paths that match likely user intents. The result is a per-app vector store of intent to trajectory pairs. At run time, a GUI agent retrieves the top-K matching trajectories by embedding similarity and follows them, without extra exploration. It works as a plug-in for existing agents.

## Key findings (bullets, with numbers)
- DroidTask (English, GPT-4 backend), average: SR 66.94% (AutoDroid) to 75.80% (KG-RAG); DA 76.53% to 84.55%; average steps 4.49 to 4.10.
- DroidTask (Qwen2-VL-72B backend): SR 62.8% to 70.5%; DA 71.6% to 80.2%; steps 8.7 to 7.9.
- Plug-in gains: MobileAgent-v2 and UI-TARS on Chinese apps (Table 5, values as extracted are partly garbled); on HarmonyOS (Table 6, as read): MobileAgent-v2 SR 41.9% to 55.4%; UI-TARS SR 61.4% to 71.0%.
- Non-mobile transfer without retraining: Weibo web SR 50% to 90% (+40 pp); QQ Music desktop SR 60% to 80% (+20 pp).
- UTG cost ablation (Table 11): SR rises with extraction time (1h 50%, 2h 60%, 4h 70%, 8h 70% on Weibo baseline 40%); saturates near 4 h.
- Best VLM+LLM for database construction: Qwen2-VL-72B + DeepSeek-14B, SR 78.33%. Best embedding model: doubao-embedding-text, SR 73.90%.
- New benchmarks: KG-Android-Bench and KG-Harmony-Bench (Chinese apps; Android and HarmonyOS).

## Implications for our app (bullets; tag each)
- [MEMORY] Intent-to-trajectory key-value store with embeddings is a close match for our "learned tasks replayed without AI" requirement.
- [LATENCY] Known intents are answered by a vector lookup, so the LLM is called for less steps (paper reports fewer steps, not latency; latency is untested).
- [AGENT] The plug-in design (retrieved path given as context to any agent) fits our architecture: the learned store can feed a Mode 1 planner.
- [EVAL] Use the paper's three metrics (SR, DA, average steps) on our Turkish app set.
- [PRIVACY/KVKK] The paper logs only logged-out flows and masks sensitive fields; we cannot adopt this for banking or messaging, where tasks need login. Stored trajectories must hold no personal content.
- [ELDERLY] Nothing in the paper addresses elderly users; the benchmarks are general.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Intent generation: VLM proposes user intents per screen; LLM splits each intent into ordered milestones.
- Milestone-based scoring: LLM gives a probability over "how many milestones completed"; softmax over logits; progress score = max probability; proximity score = alignment with a descending ideal order (Algorithms 1-2).
- Batched BFS with a progress threshold and early pruning (Algorithm 3), limited depth, top-K kept by score then shorter path.
- Stored value: a summarized action sequence per intent, retrieved by cosine similarity on the intent text.

## Risks / warnings the paper raises
- UTG extraction costs 1 to 8 hours per app; the authors call it a deployment cost.
- One knowledge database per app; no cross-app or vertical generalization yet.
- Logged-out evaluation only; authenticated actions were not tested.
- Task count is inconsistent in the text (Section 4 says 300 tasks; Section 5.1 says "over 200").
- Benchmarks are Chinese-app and English-app sets; no Turkish or elderly-user test.
- Step counts and success rates are reported; no wall-clock latency or device measurement.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Success rate (SR), decision accuracy (DA), and average steps (AS) on a Turkish app task set with the same definitions.
- Retrieval hit rate: share of user requests that match a stored intent above a threshold.
- Time-to-first-action with and without the store (our latency measure, not in the paper).
- Accuracy vs extraction time curve for our target apps, to set an offline budget.

## One-line citation
Guan, Z., Li, J. C. L., Hou, Z., et al. (2025). KG-RAG: Enhancing GUI Agent Decision-Making via Knowledge Graph-Driven Retrieval-Augmented Generation. Proceedings of EMNLP 2025, pp. 5396-5405. Association for Computational Linguistics.
