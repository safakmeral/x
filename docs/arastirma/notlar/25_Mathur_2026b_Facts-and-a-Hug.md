# 25 Sometimes You Need Facts, and Sometimes a Hug (Mathur 2026)
**Type:** user study (exploratory Speed Dating with storyboards) · **Relevance:** high

## Core contribution (2-3 lines)
Speed Dating study with 23 older adults (11 spousal dyads + 1 individual, ages 65-78, most with recent MCI diagnosis) on how they judge LLM-style explanations for an AI robot in two smart-home contexts: routine reminders (low risk) vs. emergency alerts (high risk). Shows that explanation value depends on context and that explanations are read as turns in a conversation, not as final outputs.

## Key findings (bullets, with numbers)
- Routine reminders: warm, elaborated explanations grounded in conversation history were preferred; these made up the largest share of positive ratings in that context.
- Emergency alerts: direct, concise, task-focused explanations preferred. Chatty framing was seen as downplaying urgency ("If she's too sweet to me when my kitchen may be on fire, I'd be pretty mad", P04-A).
- Emergency context: environmental-data explanations (e.g., temperature, smoke detector) had the largest share of positive ratings.
- Activity-based inferences fell in the mid-range. Internal-logic explanations ("I'm 92% confident") were rated lowest in both contexts; participants questioned the basis for the number and were more likely to distrust it.
- Explanations expected to unfold gradually (short first statement, wait for follow-up, layered detail: "conversational flowchart").
- Explanations served three roles: calibrating urgency ("what happens if I wait"), guiding actionability (a poor explanation made some say they would not follow the suggestion), and giving a "window" into a partner's routine (adult children also wanted to see them).
- Privacy: main worry is uncertainty about data use and who can see it; participants proposed "no-go zones" and "explanations of the explanation".
- Caveats stated by authors: low-fidelity speculative storyboards, GPT-4-generated explanations (manually checked), small N, descriptive ratings only.

## Implications for our app (bullets; tag each)
- [ELDERLY] Default to short, task-first wording in Mode 2 and any alert; reserve warmer, longer phrasing for non-urgent prompts. Make risk level an input to explanation length and tone.
- [UX] Do not show raw confidence percentages to users. If uncertainty must be stated, name the observable reason (e.g., "the Settings screen is open").
- [VOICE] Speak a one-line first explanation, then wait; allow "why?" follow-up and interruption at any time.
- [AGENT] Before a Mode 1 automated step, give one line on what it does and what happens if it waits.
- [PRIVACY/KVKK] Let the user see and restrict which data categories explanations may use (e.g., past tasks, screen content). Avoid reading personal details aloud when others may be present.
- [MEMORY] Referring to past sessions ("you did this last week") was valued; use only with user awareness and consent.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Risk-tagged explanation templates: risk tier selects verbosity and tone.
- Layered explanation: L1 = one-line action; L2 = reason (on "why?"); L3 = details on request.
- Four-source taxonomy from the cited field study (conversation history, environment, activity inference, internal logic) as a label scheme for our explanation logs.
- 5-point "I find this explanation useful" item for in-app explanation feedback.

## Risks / warnings the paper raises
- Friendly or confident phrasing may hide uncertainty and reduce healthy skepticism.
- Verbose explanations in emergencies add cognitive load and can make users with cognitive decline feel "looked after" or diminished.
- Explanations built from prior interactions can reveal more about the user than they expected.
- Users asked whether sensors or wearables can be wrong; the AI should acknowledge its limits.
- Results come from speculative vignettes, not a working system; breakdown cases were not tested.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Usefulness rating (5-point) per explanation, split by risk tier, in a pilot with elderly users.
- Time from explanation to user action, routine vs. urgent prompts.
- Count of follow-up "why?" requests per task as a proxy for an insufficient first explanation.
- Privacy comprehension check: can the user correctly name what data the app used?

## One-line citation
Mathur, N., Zubatiy, T., Rozga, A., Forlizzi, J., & Mynatt, E. D. (2026). Sometimes You Need Facts, and Sometimes a Hug: Understanding Older Adults' Preferences for Explanations in LLM-Based Conversational AI Systems. CHI '26, Barcelona. https://doi.org/10.1145/3772318.3790812
