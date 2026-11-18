# 14 Retrieval-augmented GUI Agents with Generative Guidelines (Xu 2025)
**Type:** system (with benchmark evaluation) · **Relevance:** medium

## Core contribution (2-3 lines)
RAG-GUI is a lightweight VLM (Qwen2.5-VL-7B) that sits between a frozen GUI agent and web tutorials retrieved at inference time. For each retrieved tutorial it predicts a relevance label and writes task-aware guidance, which is added to the agent prompt. It is trained with SFT warm-up plus self-guided rejection-sampling finetuning (RSF), and it is model-agnostic.

## Key findings (bullets, with numbers)
- AndroidWorld success rate (online, out-of-distribution): Qwen2.5-VL-7B 22.0% with vanilla RAG, RAG-GUI-7B 35.3% (+13.3). 72B: vanilla RAG 35.0%, RAG-GUI-72B 45.7% (+10.7).
- Plain RAG over raw tutorials gives limited gains on smaller backbones; long noisy tutorial text hurts.
- Offline: +4.4% on Mind2Web and +6.3% on AndroidControl (as stated in text).
- Retrieving k = 3 tutorials was best; k = 1 misses information, larger k confuses the model.
- Two-stage training helps consistently (RSF over SFT); the paper says this avoids costly manual annotation.
- Tutorial corpus: ~2.6M GUI tutorials from MINT, OmniCorpus and WikiHow after FastText filtering, dedup and LLM labeling.
- Inference adds latency from one guidance call per retrieved tutorial; the paper does not quantify it.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [AGENT] For tasks never learned before, retrieving a how-to and condensing it into guidance is a route to Guide mode without fine-tuning.
- [MEMORY] Our learned-task store is the first lookup; this retrieval is only the fallback for unknown tasks.
- [LATENCY] Per-tutorial VLM calls will be slow on phone hardware; the paper's own limitation. Only use it for unknown tasks and cap k at 3.
- [SAFETY] Tutorials can be irrelevant or wrong; the relevance filter is important before showing steps to an elderly user.
- [EVAL] AndroidWorld success rate and step accuracy are the reference numbers for an agent evaluation.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Relevance-gated guidance: per tutorial, output (relevant label, summary); keep only relevant summaries and add them to the agent prompt.
- Retrieve top-3 tutorials with an embedding model (E5 in the paper), then summarize task-aware rather than chunking.
- Rejection-sampling self-training: keep only guidance that makes the agent choose the correct action, then fine-tune on those (could be applied to our own logged runs).
- Ablation habit: compare against vanilla RAG and frozen-generator baselines.

## Risks / warnings the paper raises
- Tutorial relevance is not guaranteed; irrelevant tutorials can mislead.
- Inference-time guidance adds latency; no measurement given.
- Only evaluated with Qwen-VL backbones; generalization to other models is untested.
- Inference-only; no model finetuning on our side in this setup.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Success rate on AndroidWorld-style tasks with and without retrieved guidance.
- Relevance-label precision: does the filter keep only tutorials that match the task.
- Latency per step with k = 1 vs. 3.
- Compare vanilla RAG (raw tutorial text) against our summarized guidance on the same Elderly-style task set.

## One-line citation
Xu R, Ma K, Yu W, Zhang H, Ho JC, Yang C, Yu D. "Retrieval-augmented GUI Agents with Generative Guidelines." arXiv:2509.24183 (2025).
