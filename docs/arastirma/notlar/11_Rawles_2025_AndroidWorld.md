# 11 AndroidWorld: Dynamic Benchmarking Environment (Rawles 2025)
**Type:** benchmark · **Relevance:** high

## Core contribution (2-3 lines)
An Android emulator benchmark with 116 programmatic tasks across 20 real apps, plus 92 MobileMiniWoB++ web tasks. Each task has initialisation, parameter randomisation, success check via device/app state (adb), and teardown. Also releases M3A, a zero-shot GPT-4/Gemini agent, as the baseline.

## Key findings (bullets, with numbers)
- Best agent M3A (GPT-4 Turbo, accessibility-tree input) reaches 30.6% success on AndroidWorld; human success 80.0%. SoM (screenshot+tree) variant 25.4%; M3A-Simple (no guidelines or reflection) 19.8%; SeeAct adaptation 15.5%; Gemma 2 27B M3A 9.5%.
- On MobileMiniWoB++ (62-task subset): M3A with SoM best at 67.7%; human 100%.
- Robustness: same agent across 3 seeds gives 27.6%, 26.3%, 33.2% (mean 29.0%). Fixed seed still shows non-determinism. Pixel 5 (Android 12) gives 28.4% vs 30.6% on Pixel 6 (Android 13).
- Latency: M3A averages 3.9 min per task (2.5 min text-only); humans about 3x faster.
- Error types: perceptual (missed unchecked box), reasoning (typed note text into name field), missing knowledge (looked for "delete all" button), grounding (tapping text field places cursor at end).
- Parameter-dependent difficulty: a category only reachable by horizontal scroll caused consistent failure for that seed.
- Typical failures: slider and precise text manipulation, recovering from mistyping, memory across steps (transcription, arithmetic).
- Step budget per task set to about 2x human step count.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [EVAL] Agents reach about 30% on general tasks vs 80% for humans; our Do-it mode must assume frequent failure and always offer hand-back to the user.
- [AGENT] Accessibility-tree text-only input was as good as or better than screenshots for M3A. Supports our accessibility-service design; screenshots may add cost without gain.
- [LATENCY] M3A took 3.9 min per task. Learned or recorded flows that replay without AI (our MEMORY goal) would remove most of that cost.
- [AGENT] Scroll discovery failed; in Do-it we need explicit handling of horizontal scrolling and hidden elements.
- [SAFETY] Paper's ethics section flags malicious use and job disruption; our narration and red-dot feedback help the user see what the app is doing.
- [MEMORY] Memory-heavy tasks failed; store per-task notes or results outside the model's context.
- [EVAL] Adopt their validation pattern: check app state (databases, files) rather than UI text, where we can access it legitimately. Probably not available for third-party apps; use UI-state checks there.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Reward via system state: check database rows or file existence after the action, not screen text.
- Per-task lifecycle: initialize_task -> is_successful -> teardown, with seeded random parameters (their "millions of unique goals").
- Composite tasks: score sub-goals (partial reward) to measure hill climbing.
- M3A loop: ReAct + Reflexion. Action JSON with reason; after each action, a short summary of outcome becomes the history for the next step.
- Action space: click, long_press, scroll, input_text, navigate_home, navigate_back, keyboard_enter, open_app, status, wait, answer.
- Pinned environment: fixed device image, fixed clock (2023-10-15 15:34 UTC), F-Droid version-pinned apps, no login.
- Temperature 0 and fixed seed for reproducibility.
- Asynchronous observation: get_state with wait-to-stabilise heuristic instead of RL-style step coupling.

## Risks / warnings the paper raises
- Malicious use: bypassing CAPTCHAs, spam, altered screens.
- Societal effects: changed norms, disrupted work, behaviour change.
- Open-source apps are harder than popular ones; results may not transfer to top-downloaded apps (limitation).
- LLM-generated task templates need human review; issues common in complex templates.
- Agents are much slower than humans; cost and latency not addressed beyond timing.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Success rate across at least 3 seeds with mean and variance reported, not one seed.
- Human baseline on the same tasks for our Turkish elderly users (not only engineers).
- Per-task step count vs human step count; cap at 2x.
- Measure wall-clock per task and per step; compare text-only vs screenshot inputs.
- Device/OS variation test (Android version, screen size).
- Error taxonomy: perceptual, reasoning, missing knowledge, grounding.

## One-line citation
Rawles, C., Clinckemaillie, S., Chang, Y., et al. (2025). AndroidWorld: A Dynamic Benchmarking Environment for Autonomous Agents. ICLR 2025. arXiv:2405.14573.
