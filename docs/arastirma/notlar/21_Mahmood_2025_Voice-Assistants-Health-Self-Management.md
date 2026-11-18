# 21 Voice Assistants for Health Self-Management: Designing for and with Older Adults (Mahmood 2025)
**Type:** user study / system (5-stage co-design, LLM prototype) · **Relevance:** high

## Core contribution (2-3 lines)
Five-stage participatory design (in-home interviews N=17 data points / 21 people, co-design workshops N=10, in-home validation N=5) of an LLM-powered voice assistant (GPT-4 in a custom Alexa skill on an Echo Dot). It debriefs a doctor's after-visit summary (AVS) and creates tailored medication reminders. Core claim: older adults need awareness (understanding the AVS) and adherence (reminders) support, delivered through voice.

## Key findings (bullets, with numbers)
- Interviews (21 people aged 66-94): awareness breakdowns (not reading AVS, vision decline makes visual material hard) and adherence breakdowns (forgetting appointments, errors setting calendar reminders, missing doses outside morning/night routines).
- Co-design (N=10, aged 65-88): participants with many medications preferred the user-profile-first design (Design 2); those with few medications preferred the need-based design (Design 3). Majority favoured a hybrid.
- Participants wanted clustered reminders by intake time (mentioned 7 times), reminders that repeat until confirmed (e.g., hourly), and reminders that adapt to the daily wake-up time.
- Validation (N=5, aged 65+, 4 F / 1 M): all 5 navigated the debrief and profile creation; SUS mean 85 (SD 12.90); one participant scored 62.5 and saw no need for reminders; per-participant reminder success ranged from 92.86% (13/14) to 100%.
- Participants valued that the VA "always asks you to make the final decision", and wanted all non-critical VA behaviour to be modifiable.
- Single-question turns, stating the purpose before personal questions, and no speech right after a question reduced confusion.

## Implications for our app (bullets; tag each)
- [ELDERLY] Cluster reminders by intake time (morning, evening, bedtime) and match the user's pillbox habits; allow repeat-until-confirmed reminders.
- [UX] Ask one question per turn; state why before asking personal data; do not speak right after a question.
- [UX] Summary first, details on request (hierarchical structure); the user must be able to interrupt at any time.
- [SAFETY] If a user request conflicts with a medical requirement, warn and let the user decide; do not silently override.
- [VOICE] Use short, simple sentences; the paper reports confusion for non-native speakers.
- [PRIVACY/KVKK] Confidential activation phrase, voice profiles that limit medical data to the owner, opt-in for each data share, user-controlled deletion.
- [MEMORY] Store the user profile (routine, pillbox use) once and reuse it; ask only about changes.
- [LATENCY] Not measured in the paper; open question for us.

## Techniques we could reuse (concrete)
- Hybrid profile flow: one routine interview limited to what the AVS medication list requires (e.g., ask about meal times only if a drug must be taken with food).
- Prompt rules: group medications by time of day or event; generate reminders in chronological order; check grouped reminders against the pillbox contents.
- Voice edit operations on reminders (add, delete, change frequency) at any time.
- Mock AVS: user fills a medical form, an LLM converts it to a real-looking summary (used in studies to protect privacy).

## Risks / warnings the paper raises
- LLM hallucination and contradictory answers in a medical context; misinformation can cause harm.
- Over-reminding makes users abandon reminders; for people with MCI it may lead to over-medication.
- Generic answer when a user asked how to reach the assistant later (no cross-session guidance).
- Speech input can expose health data to visitors or strangers.
- Not tested with MCI participants; no participant reported memory issues.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- SUS per participant (threshold 70) and learnability for first-time users.
- Debrief coverage: % of AVS aspects covered; % of user questions answered correctly.
- Reminder accuracy: % of reminders matching medication requirements and the stated routine.
- Interruptions and repeated questions per session (confusion proxy).
- Repeat-question rate after reminder confirmation (over-reminding proxy).

## One-line citation
Mahmood, A., Cao, S., Stiber, M., Antony, V. N., & Huang, C.-M. (2025). Voice Assistants for Health Self-Management: Designing for and with Older Adults. CHI 2025, Yokohama. https://doi.org/10.1145/3706598.3713839
