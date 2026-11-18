# 12 ElderBench (Zhan 2026)
**Type:** benchmark (with linguistic and failure analysis) · **Relevance:** high

## Core contribution (2-3 lines)
ElderBench: 249 smartphone GUI instructions naturally elicited from 28 older adults (59-84, China) across 20 Android apps, evaluated online (130 tasks, live ADB device) and offline (119 tasks, human-demonstration task graphs). Shows that mainstream GUI agents and VLMs degrade on older-adult language, and identifies intent understanding and capability boundaries as the main failure source.

## Key findings (bullets, with numbers)
- Elderly instructions are very short: 78.31% under 20 characters (baseline MobileWorld avg 88 chars vs 15.9 chars). Average 1.26 verbs vs 2.82.
- Elliptical and narrative structures: 47.80% of elderly instructions (baseline almost none). 22.92% of Indirect-Speech items have no explicit verb.
- Semantic categories (elderly): under-specification 38.15%, indirect speech 19.28%, referential ambiguity 12.85% (e.g., "screenshot this page"), disfluency/correction 12.05%, clear and explicit 17.67%. Baseline has 0% disfluency, referential ambiguity, indirect speech.
- Overall success rate (SR): Gemini-3-Flash 49.80% (best VLM), Qwen3-VL-Plus 47.39%, GUI-Owl-1.5 (8B) 45.38% (best GUI agent), UI-Venus-1.5 41.37%, AutoGLM 36.55%, Doubao-1.5-UI-TARS 32.53%. No model exceeds 50%.
- Online SR is much higher than offline SR (e.g., GUI-Owl-1.5 68.46% online vs 20.17% offline; Gemini-3-Flash 27.73% offline). Authors caution the task-graph protocol is stricter and task sets differ.
- Latency (average seconds per turn, overall): Qwen3-VL-Flash 3.57 s (lowest); GUI agents 6.4-8.0 s; GLM-4.6V 7.40 s.
- Normalization test (100 tasks, rewritten into concise imperatives): AutoGLM 37 to 61 successes; Qwen3-VL-Flash 23 to 46 (McNemar p<0.001). Gains across all categories.
- AutoGLM failure analysis (63 failures on online subset): intent/capability boundary 57.1%; planning/state tracking 33.3%; environment awareness 6.4%; visual/action execution 3.2%.
- Feature attribution: information-inquiry tasks are easier; System Settings and Tool Assistance are harder. Elliptical structure is the strongest negative single feature (r = -0.15). Verbose descriptions help in information-inquiry tasks (+0.70 interaction).
- VLM-as-judge for online tasks agreed with humans 93.08%.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [ELDERLY][AGENT] Expect very short, indirect, referential spoken requests ("make it bigger", "the sound is too low"). Map states to settings actions (font size, volume, brightness) explicitly rather than expecting literal commands.
- [AGENT] Add proactive clarification: when a request is elliptical or refers to "this", ask one question before acting (paper's design insight 2).
- [AGENT] Use the screen to resolve referential requests ("this", "that") - our Accessibility tree gives the focused element.
- [LATENCY] Qwen-class small models gave the lowest latency; trade accuracy against wait time per turn. Our target: short first response, interruptible.
- [VOICE] Voice input will add disfluency and corrections; test our speech pipeline on these categories.
- [PRIVACY/KVKK] Several evaluated models are cloud APIs; any cloud call must follow KVKK rules.
- [EVAL] Reuse the normalization experiment (same task, original vs rewritten request) to isolate whether our failures come from language or from task difficulty.
- [EVAL] Track the three-way failure split (intent / planning / execution) in our own logs.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Instruction normalization as a pre-step: rewrite the user request into a concise action-focused form, show it to the user, then execute (their normalization gave +24 and +23 successes).
- Three design insights: state-aware intent understanding; proactive clarification for elliptical or disfluent input; context-aware parsing that adapts interpretation to scenario.
- Task-graph offline evaluation: merge multiple human demonstrations into a graph of UI states (signatures, anchor text, package), with a 50 px click tolerance and fuzzy text match; success only when the agent reaches DONE. Useful for reproducible tests of learned tasks.
- Normalized action space [0,1000] mapped to device pixels; Tap, Long Press, Swipe, Type, Home, Back.
- Failure taxonomy for manual trajectory review (intent, planning, environment, execution).
- Metrics: SR, average inference latency per turn, token cost per task.

## Risks / warnings the paper raises
- Users were Chinese-speaking; instruction style and app set (Alipay, WeChat, Meituan, Douyin) differ from Turkish and Play Store apps. Transfer is uncertain.
- Small participant pool (28); 249 tasks is still small per app.
- Single-turn tasks only; older adults often need multi-turn help.
- Offline protocol penalizes agents that take valid but unrecorded paths and depends on demonstrations collected on a physical device.
- Account states, dynamic content, and permissions can break tasks; the paper excluded payment-related apps from online runs.
- Agents "assume unavailable apps, accounts or permissions" - relevant to our Do-it mode's pre-checks.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Build a Turkish equivalent of the set: collect spoken requests from older users, label linguistic category, and measure SR per category.
- Normalization ablation: same task with raw vs rewritten request.
- Two-track protocol: live ADB/emulator runs for stable apps, offline task graphs for sensitive ones.
- Report SR, latency per step, and token/cost per task together.
- Failure split: share of failures by intent, planning, environment, and visual/action causes.

## One-line citation
Zhan, W., Zhang, P., Gu, N., Shaqu, Q., Liu, J., Hu, Z., Liu, Y., Lam, K. H., & Lu, T. (2026). ElderBench: Benchmarking Autonomous Mobile Agents for Older Adults. arXiv:2609.04850.
