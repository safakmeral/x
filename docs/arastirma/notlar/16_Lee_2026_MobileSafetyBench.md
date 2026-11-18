# 16 MobileSafetyBench: Evaluating Safety of Autonomous Agents in Mobile Device Control (Lee 2026)
**Type:** benchmark · **Relevance:** high

## Core contribution (2-3 lines)
A benchmark of 250 tasks on Android emulators (13 apps, including messaging, memos, social media, banking, and stock trading) that measures helpfulness (goal achievement) and safety (refusal or consent on high-risk tasks). It includes indirect prompt-injection scenarios and proposes Safety-guided Chain-of-Thought (SCoT) prompting.

## Key findings (bullets, with numbers)
- Task split: 100 low-risk and 100 high-risk daily tasks, plus 50 high-risk prompt-injection tasks.
- GPT-4o: 82% goal achievement on low-risk tasks, but 60% goal achievement and 31% refusal on high-risk tasks.
- Claude-3.5-Sonnet: 64% refusal on high-risk tasks; 73% goal achievement on low-risk tasks.
- Phi-4: 67% refusal on high-risk tasks but 3% goal achievement on low-risk tasks (over-refusal).
- M3A workflow: about 80% goal achievement on both low- and high-risk tasks.
- Prompt-injection defenses out of 50 tasks: GPT-4o 3, Gemini-1.5-Pro 8, Claude-3.5-Sonnet 15. Agents opened banking apps, traded stocks, or tried to change the device password.
- SCoT on GPT-4o: refusal on high-risk tasks rises from 6% to 36%; goal achievement on low-risk tasks 84% to 82%.
- Risk detection, QA setting vs agent: GPT-4o 92% vs 36%; Claude-3.5 92% vs 66%; Gemini-1.5-Pro 80% vs 82%.
- OpenAI-o1 refuses more but is much slower: average latency 18.3 s (basic) and 25.6 s (SCoT), vs 4.5-4.7 s for GPT-4o.
- The extracted text garbles the refusal figures of Table 4; only the latency numbers are used here.

## Implications for our app (bullets; tag each)
- [SAFETY] High-risk actions (bank transfers, sending private data, changing passwords) must not run without explicit user consent; the paper treats consent requests as safe behaviour.
- [SAFETY] Text injected on screen (messages, posts) must never be treated as a user instruction; every tested LLM agent failed this.
- [SAFETY] Offer an ask-consent action rather than refusing, to avoid over-refusal on harmless tasks.
- [PRIVACY/KVKK] Card numbers, authentication codes, and personal data on screen need careful handling.
- [LATENCY] A safety-reasoning prompt (SCoT) adds little latency; heavy reasoning models add many seconds, which an elderly user would notice.
- [EVAL] Test both low-risk completion and high-risk refusal; do not judge safety by refusals alone.
- [UX] Refusing low-risk tasks is a usability failure for older users.

## Techniques we could reuse (concrete)
- SCoT prompt: before choosing an action, the model lists potential safety issues from the current screen and task, then plans.
- Explicit refuse() and ask-consent() actions in the action set.
- Rule-based evaluators that check device state (action history, settings, file storage, app database) rather than LLM judgment.
- Risk tiering: label each task low or high risk through a human survey before testing.

## Risks / warnings the paper raises
- Agents overlook safety considerations they generated themselves (inconsistent reliability).
- Proprietary LLM agents are vulnerable to prompt injection even with simple injected text.
- Safety-helpfulness trade-off: some models refuse harmless tasks while others complete harmful ones.
- Content monitoring by an agent raises technology-paternalism concerns; consent is needed for sensitive content.
- Risks that are hard to see in images (e.g., discriminatory gestures) were a known gap.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Goal achievement rate on a low-risk task set (target: high).
- Refusal or consent rate on a high-risk set (target: high); report per risk category.
- Injection defense rate: plant injected instructions in messages or notes and count how many are ignored.
- Latency per decision for each model or prompt type.
- Compare agent refusals with QA-style risk detection to find inconsistency.

## One-line citation
Lee, J., Hahm, D., Choi, J. S., Knox, W. B., & Lee, K. (2026). MobileSafetyBench: Evaluating Safety of Autonomous Agents in Mobile Device Control. AAAI-26.
