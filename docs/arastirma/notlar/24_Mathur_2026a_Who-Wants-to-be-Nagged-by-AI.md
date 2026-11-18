# 24 "Who wants to be nagged by AI?" Agreeableness and explanations (Mathur 2026)
**Type:** user study · **Relevance:** high

## Core contribution (2-3 lines)
Between-subjects storyboard study (N=70 US older adults, 60-83) of a fictional LLM voice assistant "Robin" with high vs low agreeableness, giving reminders and emergency alerts, each followed by a history-based (UH) or real-time environmental (ENV) explanation. Shows agreeableness changes trust, empathy and likeability but not perceived intelligence, and the benefit shrinks in emergencies.

## Key findings (bullets, with numbers)
- Manipulation check succeeded: HA 4.17 vs LA 3.23 (5-pt), p=.0001.
- HA vs LA (Kruskal-Wallis): empathy Mdn 59.32 vs 38.16 (PETS 101-pt), p=.006, r=.325; likeability 4.25 vs 3.40, p=.007, r=.320; intention to adopt 5.75 vs 5.25 (7-pt), p=.021, r=.275; trust 3.75 vs 3.35, p=.017; reliance and satisfaction small effects. Intelligence no difference (p=.715).
- Context: HA advantage on intention, empathy, trust, reliance, satisfaction, likeability in routine reminders; in emergencies only intention, empathy and likeability remained.
- Explanation type: ENV explanations rated higher than UH on reliance, satisfaction, likeability and intelligence (all p<.05). Authors read this as environment-grounded explanations supporting trust calibration.
- User agreeableness (Mini-IPIP) correlated negatively with LA ratings: intention r=-.42, trust -.36, likeability -.50 (p<.05). No correlation with HA ratings.
- Qualitative: LA explanations seen as "abrupt", "judgmental", with "unnecessary digs"; a participant wrote "who wants to be nagged by AI?"
- Participants attended more to tone than to factual content, especially in routine reminders.
- Sample: 53 female, 16 male, 1 other; 62 had more than a little VA experience.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [ELDERLY][VOICE] Keep a warm, non-judgmental tone in routine Guide narration (e.g., "tap the top-right button"), not curt commands or corrections that sound like criticism.
- [SAFETY] In emergency or time-critical prompts, switch to short, clear, factual phrasing; the study shows warmth gives less benefit there.
- [UX] Explanations of why the app acts should reference the current screen ("I can see the Send button now") rather than past sessions; ENV beat UH in this study.
- [UX] Tone in our app should not claim intelligence changes; warmth and competence are separable, so warmth need not be paid for with perceived competence.
- [AGENT] Avoid sycophancy in generative explanations (paper cites overreliance risk); keep explanations factual.
- [EVAL] Use the study's measures in our pilot: PETS-style empathy, Godspeed likeability and perceived intelligence, Trust in Automation, explanation satisfaction, reliance.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Trait Modulation Key (TMK) prompting to set agreeableness level; then manual review of generated explanations for consistency.
- Two explanation sources: conversation history and real-time sensor/screen state; label each explanation by source.
- Storyboard + audio prototype for cheap user testing before building the full voice loop.
- Mini-IPIP (4 domain items) to record user agreeableness as a covariate.
- Manipulation check item to confirm the tone manipulation was perceived.

## Risks / warnings the paper raises
- Low-agreeableness tone is penalised, especially by agreeable users; matching user personality may matter.
- Overly agreeable tone can reduce critical scrutiny and promote overreliance (cited in paper).
- Storyboard study: single synthetic voice; no real-world or longitudinal use; repeated failures not tested.
- Effects were tested with one personality trait only.
- Small sample per condition (35) and heavily female sample.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Between-subjects tone test (warm vs neutral) on Turkish older users, with routine vs emergency scenarios as within-subjects.
- Trust, empathy, likeability, intelligence, and reliance measured after each block.
- Ask about perceived agreeableness to confirm the manipulation.
- Compare ENV-style (screen-based) vs history-based explanations in Guide mode.
- Record whether users follow instructions (reliance) as a behavioural measure.

## One-line citation
Mathur, N., Rahman, H., & Desai, S. (2026). "Who wants to be nagged by AI?": Investigating the Effects of Agreeableness on Older Adults' Perception of LLM-Based Voice Assistants' Explanations. CHI '26. arXiv:2603.09012.
