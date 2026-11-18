# 10 Mobile-Agent-v2 (J. Wang 2024)
**Type:** system (with dynamic on-device evaluation) · **Relevance:** high

## Core contribution (2-3 lines)
Multi-agent architecture for phone operation: a planning agent (text-only GPT-4) condenses history into task progress, a decision agent (GPT-4V) picks the next operation and maintains a memory unit of focus content, and a reflection agent (GPT-4V) compares screens before and after each operation and reverts erroneous ones. Screen perception uses OCR, icon detection (GroundingDINO), and icon description (Qwen-VL). Evaluated by live ADB runs on HarmonyOS and Android.

## Key findings (bullets, with numbers)
- Abstract: over 30% improvement in task completion vs single-agent Mobile-Agent.
- 88 instructions (40 system-app, 40 external-app, 8 multi-app) in Chinese (HarmonyOS) and English (Android).
- Advanced instructions, non-English: Mobile-Agent-v2 reaches 55% SR vs 20% for Mobile-Agent (text summary). Basic system apps: 9/10 vs 5/10.
- English: Mobile-Agent-v2 on advanced system-app tasks 6/10 vs 4/10 for baseline; with injected knowledge 8/10. Multi-app basic 2/2 for v2 and baseline.
- Single-step decision accuracy: GPT-4V alone 2.7% (basic) / 0.9% (advanced) SR-DA, Gemini-1.5-Pro 38.2% / 29.8%, Qwen-VL-Max 42.1% / 33.6%, GPT-4V inside the agent framework 92.7% / 83.5%.
- Ablation: removing the planning agent hurts most; removing reflection and memory also lowers results. Memory is crucial for multi-app tasks.
- Errors vs. step position: baseline Mobile-Agent errors concentrate late in long tasks; Mobile-Agent-v2 shows no clear pattern.
- Manually injected operation knowledge raises completion, though decision accuracy stays below 100%.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [SAFETY] Reflection with revert: if the screen changes to an unrelated page after a tap, undo (Back) and do not keep the operation in history. Useful for our Do-it mode when a misclick happens.
- [AGENT][MEMORY] Planning agent with text-only progress: keep a short textual task-progress note for the model instead of the full screenshot history. Reduces tokens and context size.
- [MEMORY] Memory unit for focus content: store values the task needs later (a contact name, a photo's place, a number seen on screen).
- [LATENCY] Three model roles per step (plan, decide, reflect) multiply calls. Use the text-only planner and skip reflection when a learned replay already verified the step.
- [MEMORY] Injected operation knowledge = our learned-task replay: a saved, verified sequence that boosts the agent on the same task.
- [PRIVACY/KVKK] Two of three roles need screenshots sent to a vision model; the design needs KVKK review.
- [ELDERLY] Failed or ineffective operations should not be recorded, so the agent does not repeat them; a useful default for simple, repeated user confirmations.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Three-agent loop: planning (progress from history and last operation), decision (operation from progress, screen, reflection), reflection (classify result as erroneous / ineffective / correct).
- Reflection taxonomy and handling: erroneous -> revert to previous page; ineffective -> stay; correct -> commit to history.
- Operation space: Open app, Tap(x,y), Swipe, Type, Home, Stop. Small set simplifies planning.
- Perception: OCR + icon detection + icon captioning to produce text-and-coordinate list of the screen.
- Metrics: SR, completion rate (correct steps / ground-truth steps), decision accuracy, reflection accuracy.
- Evaluation protocol: dynamic on a real device with ADB; basic vs advanced instructions per app.

## Risks / warnings the paper raises
- Model calls per step are high (three roles, two of them vision), and latency and cost are not reported.
- Decision errors remain even with knowledge injection, so reflection is essential.
- Single-model vision baselines (GPT-4V alone) are nearly unusable, which shows dependence on the agent framework.
- Evaluation uses ADB, which needs developer mode; our Accessibility Service approach differs and needs its own validation.
- Only 88 instructions across 10 apps; success rates per cell are based on 2 to 10 runs.
- Authors note that automated testing via natural-language procedures could be a risk if not controlled.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Report SR, CR, decision accuracy, and reflection accuracy separately, as they do.
- Ablation over planner, reflection, and memory, using our own Turkish tasks.
- Error-position analysis: where in the sequence do failures happen?
- Basic vs advanced instruction split, plus multi-app tasks.
- Measure how much injected learned knowledge improves SR, to quantify the value of our replay store.

## One-line citation
Wang, J., Xu, H., Jia, H., Zhang, X., Yan, M., Shen, W., Zhang, J., Huang, F., & Sang, J. (2024). Mobile-Agent-v2: Mobile Device Operation Assistant with Effective Navigation via Multi-Agent Collaboration. arXiv:2406.01014.
