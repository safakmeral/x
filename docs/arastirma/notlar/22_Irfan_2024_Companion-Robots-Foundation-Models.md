# 22 Recommendations for designing conversational companion robots with older adults through foundation models (Irfan 2024)
**Type:** user study (participatory design, focus groups) · **Relevance:** high

## Core contribution (2-3 lines)
Four co-design workshops with 28 Swedish-speaking adults aged 65+ (mean 74.5) watched a demo of an LLM-powered robot (Furhat + GPT-3.5 text-davinci-003, zero-shot) and discussed six everyday scenarios. The paper derives older adults' expectations for a conversational companion and maps them to foundation-model design recommendations.

## Key findings (bullets, with numbers)
- Expectations (thematic analysis): active listening when alone (follow-up questions), passive listening in groups, personalization and memory, privacy and data control, information retrieval (reminders, news, weather, fact-checking), social connectedness, emotional expressiveness.
- Participants wanted to be able to say "forget it, delete it" and wanted the robot to identify who it talks to, so data is not shared with others.
- Participants found the robot's voice "insensitive" and lacking intonation; emotional expression was a main complaint.
- Participants doubted the robot could reduce loneliness ("I have a hard time seeing ... that you would choose the robot"); they saw value mainly for people who are completely alone or have dementia.
- Only a 2-minute autonomous demo; participants did not interact with the robot directly.
- Cited prior-work accuracy: current ASR 70-85% on adult speech, 60-80% on children's speech.

## Implications for our app (bullets; tag each)
- [ELDERLY] Older adults expect the assistant to take initiative (daily agenda, doctor and medication reminders) and to ask follow-up questions when they are alone.
- [PRIVACY/KVKK] Identify the speaker before sharing personal data; allow verbal deletion of stored information; be transparent about what is stored and who can access it; no surveillance by family members.
- [MEMORY] Learn user facts (family, routine, preferences) in conversation and refer back to them; users must be able to remove them.
- [VOICE] Tone and intonation matter to older adults; use slower speech (the study used 80% speech rate).
- [SAFETY] Medical and news answers must be factual; misinformation is critical in health queries.
- [UX] Avoid over-proactive behaviour; some users found proactivity intrusive (ElliQ findings).

## Techniques we could reuse (concrete)
- Passive fact extraction: prompt the LLM to "summarize what we know about the user" from conversation and store facts in a per-user knowledge base.
- Active-listening prompt: describe the agent as an active listener that uses shared history and follow-up questions.
- Retrieval-augmented personalization from stored facts.
- Open-world user recognition using age and gender cues to reduce bias (face recognition performs worse on older adults).
- Machine "unlearning" triggered by a spoken deletion request.

## Risks / warnings the paper raises
- Hallucination and outdated training data; fact-checking via knowledge bases or web retrieval is suggested.
- Anthropomorphism can be deceptive to users.
- Cloud LLMs can use data beyond consent or give third-party access; embedded or privacy-preserving pipelines are recommended.
- Toxic or adversarial user behaviour needs filtering; learning from users can drift (Tay example).
- Some users found proactive robot behaviour annoying or patronizing (ElliQ study).

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Loneliness scales: UCLA Loneliness Scale; Companion Robot Impact Scale, before and after use over time.
- Almere model items (usefulness, sociability, adaptiveness) and privacy-concern scale after scenarios.
- Scenario-based focus groups with older adults before live testing.
- Long-term deployment with a subjective-perception scale.

## One-line citation
Irfan, B., Kuoppamaki, S., & Skantze, G. (2024). Recommendations for designing conversational companion robots with older adults through foundation models. Frontiers in Robotics and AI, 11, 1363713. https://doi.org/10.3389/frobt.2024.1363713
