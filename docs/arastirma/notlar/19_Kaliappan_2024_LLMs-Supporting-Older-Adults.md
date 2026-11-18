# 19 Exploring the Role of LLMs for Supporting Older Adults: Opportunities and Concerns (Kaliappan 2024)
**Type:** review (position paper, no experiments) · **Relevance:** medium

## Core contribution (2-3 lines)
Short CHI 2024 workshop position paper. Revisits HCI research on older-adult technology through the lens of LLMs and argues for their use in accessibility, health monitoring and digital literacy/security. It also lists accuracy and privacy concerns. It proposes no system and no evaluation.

## Key findings (bullets, with numbers)
- No quantitative results; claims are argued from prior literature.
- Barriers to adoption: lack of understanding of technical features, high cost, confusing instructions, privacy concerns.
- LLMs lower the threshold for use by replacing query phrasing with conversation; conversational continuity is treated as an accessibility gain.
- Digital literacy: LLMs can guide from sending email to online banking; voice commands and immediate feedback are suggested.
- Scam defense: LLM can flag phishing-style messages (requests for sensitive info posing as bank or government officials) and explain warning signs.
- Known concerns: hallucination, no confidence signal (true and false answers look equally sure), "lost in the middle" in long contexts, need for prompt engineering.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [ELDERLY] Conversational, voice-first help matches our concept; the paper confirms the value but offers no data for it.
- [SAFETY] Idea of an assistant that warns about scam messages is relevant. Our app could flag suspicious on-screen requests in Guide mode.
- [SAFETY] Confidence is not exposed by LLMs. In Do-it mode, do not act on low-confidence plans; ask the user to confirm.
- [PRIVACY/KVKK] Paper says sensitive data sent to an LLM needs encryption, access control and clear policy. Aligns with KVKK obligations.
- [VOICE] Voice-activated commands are named as an ease-of-use improvement.
- [UX] Immediate constructive feedback and personalization/adaptive learning are recommended for varied skill levels.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Scam/phishing flagging by LLM with an explanation of the warning signs (conversational), applied to notifications or on-screen text.
- Adaptive learning: adjust explanation depth to the user's skill level (same idea as AgeMate's granularity levels).
- Tiered caution: paper treats confidence as missing; we would need an explicit confidence or "ask first" rule of our own.

## Risks / warnings the paper raises
- Hallucinated or misleading instructions can cause real harm, especially in health contexts.
- Users may be unable to judge when the LLM is wrong ("how do we explain new threat models such as hallucinations to people who are not tech savvy?").
- Sensitive personal data leakage or misuse.
- Older adults with cognitive impairments are under-represented in the research.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- None proposed by the paper. The conclusion asks open questions: what scaffolding to provide when introducing LLMs, and how to explain threat models to non-tech-savvy users. These can become our user-test questions.

## One-line citation
Kaliappan S, Anand AS, Saha K, Karkar R. "Exploring the Role of LLMs for Supporting Older Adults: Opportunities and Concerns." CHI 2024 Workshop on HCI and Aging (arXiv:2411.08123).
