# 03 Bridging the Digital Divide: AgeMate, an LLM Mobile Agent for Elderly Smartphone Users (Chen 2025)
**Type:** system (prototype; no quantitative evaluation reported) · **Relevance:** high

## Core contribution (2-3 lines)
AgeMate is a dual-mode (text and speech) GPT-4o mobile agent for older users. Auto-Execute mode performs the task, while Tutorial mode shows step-by-step guidance at three detail levels and checks the user's own taps against the target. It includes warnings for risky actions and a per-app local log that is fed back into prompts.

## Key findings (bullets, with numbers)
- No user study or metrics. The paper reports only design and qualitative validation.
- Latency of about 20 seconds between actions; the main source is the LLM API call.
- Each round sends a screenshot plus XML tree; numbered labels are overlaid on XML elements before sending.
- Tutorial granularity: 3 levels (Observation+Thought+Action / Thought+Action / Action only).
- Tap check: if a user's touch is farther than a preset threshold from the target, the tap is ignored and the box flashes red.
- Risk warning text: "Attention: This is a dangerous action, please be careful!" requires "I know" before continuing.
- Speech input lacks dialect support (paper's own limitation).
- Back navigation works in third-party apps; forward navigation does not, because there is no "forward stack" API.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [ELDERLY] Three guidance granularities by user expertise is a concrete UX pattern for Guide mode; add it to our onboarding.
- [SAFETY] Touch-target validation and an explicit danger warning before deletes or payments map directly to our safety requirement for Do-it mode.
- [SAFETY] Insist override: user can force a tap the system disagrees with. Keep a human override in our design.
- [LATENCY] 20 s per action is unacceptable for our target; the paper's remedies (distilled one-click actions, device-side models for simple steps, floating progress bubble during waits) are worth copying.
- [PRIVACY/KVKK] Paper admits screenshots and XML go to a public LLM API, possibly with private info. Must be redacted or kept on device for KVKK.
- [MEMORY] Per-app history log injected into the prompt improves accuracy; supports learned-task memory.
- [VOICE] Speech is important for seniors but dialect and accent support is weak.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Prompt output structure: Observation, Thought, Action, Summary (Summary kept internal, not shown to user).
- Numbered overlay from XML bounds, then send the labeled screenshot plus XML to the VLM; the model answers with a label index.
- Touch-distance check against target coordinate with a fixed threshold; on miss, flash warning and wait for confirm or insist.
- Danger keyword list (delete, pay) that forces a blocking "I know" dialog.
- Per-app task log (screenshot, XML, task interpretation, actions) in local storage, retrieved by app name for later prompts.
- One-click emergency stop, always visible.

## Risks / warnings the paper raises
- LLM output format errors and misinterpretations.
- User mistakes such as tapping the wrong location.
- Privacy: screenshots and XML uploaded to a public LLM API.
- Over-reliance on AI tools and cognitive offloading in older adults (cites Gerlich 2025); the paper plans to study this later.
- Long waits create user anxiety.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Time per action and total task time, with and without a waiting indicator.
- Tap-correctness rate in Tutorial mode, and how often the threshold check fires wrongly.
- Rate of user overrides ("Insist") as an indicator of trust.
- Comprehension check at each granularity level for novice vs. experienced users.
- Long-term study: does the user become less able to act without the assistant (AgeMate's own planned study).

## One-line citation
Chen L, Mu Y. "Bridging the Digital Divide: Empowering Elderly Smartphone Users with Intelligent and Human-Centered Design in AgeMate." Proceedings of REALM 2025 (1st Workshop for Research on Agent Language Models), pp. 327-333, July 2025.
