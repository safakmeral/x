# 08 AppAgent (C. Zhang 2025)
**Type:** system (with user study on image editing) · **Relevance:** high

## Core contribution (2-3 lines)
LLM-based multimodal agent that operates Android apps through a simplified action space (tap, long_press, swipe, text, back, exit) on numbered UI elements, using GPT-4V screenshots plus XML. It learns each app in an exploration phase (autonomous trial and error, or watching a human demonstration) that writes a per-app reference document, then uses that document at deployment without fine-tuning.

## Key findings (bullets, with numbers)
- On 45 tasks across 9 apps (Lightroom excluded), the GPT-4 raw-action baseline reached 2.2% SR (reward 0.6, 4.0 avg steps). Simplified action space with no document: 48.9% SR.
- With documents: auto exploration 73.3% SR; watching demos 84.4%; manually written document 95.6% (reward 5.5). Auto-generated docs approach manual ones.
- Task success: a task fails if not finished within 10 steps. Exploration capped at 40 steps.
- Lightroom image-editing user study (5 images): average rank (lower is better) 2.30 for GPT-4 baseline, 1.95 with demo-generated doc, 1.75 with manual doc. Tool use 2.4 vs 5.8 vs 4.0.
- Paper reports that LLMs struggle to output accurate xy coordinates; numbered element IDs remove that need.
- No latency or token cost is reported.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [AGENT] Use numbered element tags (from accessibility nodes) overlaid on the screen and passed to the model. Our Do-it mode can then issue "tap(id)" instead of coordinates, which avoids mis-taps.
- [MEMORY] Per-app knowledge documents built from exploration or demonstration map well to our "learned tasks" store: record what each element does and reuse it without AI calls.
- [UX] Guide mode can be built from "watching a demo": record the user's own taps (element and action only), then replay or narrate them.
- [LATENCY] Each step is a multimodal LLM call; the paper reports no timing. We must measure this ourselves and avoid calls when a learned replay exists.
- [SAFETY] Exploration on live apps can wander into ads or unrelated pages; the paper's rule (press Back on irrelevant pages) is a useful safety default. Do not run autonomous exploration on a real elderly user's phone.
- [PRIVACY/KVKK] Screenshots go to a cloud model in this design. Mask sensitive areas or send only the accessibility tree when possible.
- [ELDERLY] The paper's action set (tap, swipe, text, back) is simple, which suits our limited-gesture approach, but multi-touch and irregular gestures are unsupported (their stated limitation).

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Observe, Think, Act, Summarize loop per step; the summary of past actions is passed in the next prompt as short-term memory.
- Action space: tap(id), long_press(id), swipe(id, direction, distance short/medium/long), text(string), back(), exit().
- Element identifier: resource ID if present, else class name + size + content text; overlay as semi-transparent numbers.
- Document generation: after each action, compare before and after screens and write what the element does; merge with previous notes when an element is used again.
- Metrics: SR within step budget, reward (progress score on partially complete tasks), average steps, user ranking for open-ended tasks.

## Risks / warnings the paper raises
- Depends on XML/UI hierarchy for element IDs; not every app exposes useful nodes (their approach uses XML from ADB, not Accessibility Service).
- Tested with GPT-4V only; cost and latency under real use are not reported.
- Exploration can be long (up to 40 steps) and can be drawn off-task by ads.
- Only 45 tasks; manual document is the oracle benchmark, so quality depends on documentation effort.
- Advanced gestures (multi-touch, irregular swipes) are not supported.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- SR within a fixed step budget (e.g., 10) on our task set; reward (distance to goal state) for partial credit.
- Compare: no-doc, auto-exploration doc, demonstration doc, manual doc - our own variant of their Table 1.
- Average steps and per-step latency, reported separately.
- User-ranking study on open-ended tasks, as in their Lightroom study.

## One-line citation
Zhang, C., Yang, Z., Liu, J., Han, Y., Chen, X., Huang, Z., Fu, B., & Yu, G. (2025). AppAgent: Multimodal Agents as Smartphone Users. In Proceedings of CHI 2025. arXiv:2312.13771.
