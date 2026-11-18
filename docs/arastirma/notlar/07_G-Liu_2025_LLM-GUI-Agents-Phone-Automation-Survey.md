# 07 LLM-Powered GUI Agents in Phone Automation: Surveying Progress and Prospects (Liu 2025)
**Type:** survey · **Relevance:** high

## Core contribution (2-3 lines)
Phone-specific survey of LLM-driven phone GUI agents. Taxonomy covers frameworks (single-agent, multi-agent, plan-then-act), modeling (prompt engineering vs. SFT/RL), datasets and benchmarks, plus open challenges (on-device cost, personalization, security). Frames the field as a "performance trilemma" of speed, memory and cost.

## Key findings (bullets, with numbers)
- Google Play has ~1.68M apps (cited); pre-LLM automation was scripts/RPA that break on UI change and need expert upkeep.
- Single-step execution "can take several seconds to tens of seconds" and is "not viable for real-world user interaction" (Sec. 4.6).
- Perception: UI-tree (AutoDroid HTML-ification, DroidBot-GPT NL conversion) vs. screenshot (Auto-GUI, AppAgent, Mobile-Agent-v2). Screenshot + UI tree fusion is rich but high-latency.
- Multi-agent (Mobile-Agent-v2 planner/decision/reflection; Mobile-Agent-E with Self-Evolution Module AES/AET) gives higher success but adds communication latency and multiple model calls.
- Benchmark gap: UI-TARS drops from >90% on AndroidControl to 46.6% on AndroidWorld; best AndroidWorld result (Mobile-Agent-v3, 73.3%) is prompt/ensemble-based, not RL.
- ScreenSpot: UI-R1 reaches 90.2%; RL methods reach up to 93.5% on AndroidControl and 88.6% on GUI-Odyssey.
- AndroidWorld: 116 tasks over 20 apps, success rules hand-scripted per task; LLM-as-judge is cheaper but hallucinates on long trajectories.
- B-MoCA randomizes UI layout and language (131 tasks) and is cited as the rare robustness test.
- Security: Active Environmental Injection Attacks (notification manipulation) reach up to 93% success in benchmarks (Chen 2025c). Backdoor VisualTrap is persistent after clean fine-tuning.
- Commercial precedents: Apple Intelligence (on-device, privacy), vivo PhoneGPT, Honor YOYO, Zhipu AutoGLM.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [LATENCY] Speed is the main barrier to real use; cache and replay learned tasks so the common path never calls the LLM.
- [MEMORY] Paper calls out lack of persistent long-term memory of user habits as the blocker for personalization; supports our "learned tasks replayed without AI" idea.
- [AGENT] Plan-then-act (separate plan from grounding) fits Guide mode: the LLM states the next instruction, a grounding step finds the element on screen.
- [SAFETY] Popup/notification injection is a documented threat; our Do-it mode must not blindly follow on-screen text.
- [PRIVACY/KVKK] Survey notes on-device deployment as the privacy path; cloud screenshots are the thing to minimize.
- [EVAL] Benchmarks are mostly success-rate only; we should also measure step efficiency and redundancy.
- [UX] Paper stresses user-centric adaptation as an open problem for elderly and non-technical users.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Set-of-Mark: overlay numbered labels on UI elements so the model picks an index, not coordinates (MM-Navigator, AppAgent).
- Plan-then-act split: high-level instruction first, then grounding model maps it to a tap target (SeeAct, UGround, ClickAgent).
- Verifier-driven selection (V-Droid): LLM scores a finite set of extracted candidate actions instead of generating free-form actions; reduces latency.
- Self-evolution memory (Mobile-Agent-E AET): store reusable operation sequences after each successful task.
- Reflection agent (Mobile-Agent-v2): compare before/after screenshots to detect failed steps.
- Ask-before-plan (Mobile-Bench-Ambiguous): ask a clarifying question when the instruction is underspecified.

## Risks / warnings the paper raises
- High latency and per-step API cost; hybrid single/multi-agent routing is suggested but not solved.
- Brittle UI-tree parsers and non-standard controls.
- Benchmark success rates overstate real-world utility; datasets are "sterile" (no pop-ups, notifications, permission dialogs).
- Prompt injection, environmental injection and backdoor attacks on GUI agents.
- LLM-as-judge hallucination on long screenshot sequences.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Step efficiency vs. optimal path and Reversed Redundancy Ratio (AndroidArena, AndroidLab).
- Completion awareness: does the agent stop at the right moment.
- Robustness protocol: inject pop-ups, notifications and UI-layout randomization (B-MoCA style) and measure recovery.
- Format compliance and invalid-action rate for LLM output.
- Hybrid evaluation: rule-based checks for factual state plus a judge for subjective success.

## One-line citation
Liu G, Zhao P, Liang Y, et al. "LLM-Powered GUI Agents in Phone Automation: Surveying Progress and Prospects." Transactions on Machine Learning Research, 11/2025 (arXiv:2504.19838v3).
