# 23 Conversational Agents for Older Adults' Health: Systematic Review (An 2025)
**Type:** review · **Relevance:** high

## Core contribution (2-3 lines)
PRISMA-style systematic review of 72 papers (78 user studies) on conversational agents (CAs) for older adults' health, from an HCI perspective. Maps CA types and roles, older adults' experiences (effects, acceptance, concerns) and expectations, and proposes design directions.

## Key findings (bullets, with numbers)
- Types: four types (chatbot, voice assistant, graphical ECA, physical ECA). Nearly half of the 72 papers (N=39) studied graphical or physical ECAs; research shifted from ECAs toward chatbots and voice assistants after 2019.
- Roles (N): companion 17, coach 16, general health assistant 12, therapist 9, health information provider 9, monitor 6, disease management 3.
- Health domains: general 27, mental 24, physical 12, cognitive 9.
- Effects: 36 studies report effects: 26 positive, 8 neutral, 2 negative. Only 5 studies were longitudinal (>2 months). A walking-step gain in one coach study was no longer significant at 12 months (novelty decay).
- Acceptance: of 15 studies reporting acceptance, 11 found low acceptance. Stigma ("someone who needs help") and interaction fluency (breakdowns, accents, dialects) reduce acceptance.
- Concerns: 9 studies; all name privacy; dependence in 5; cost, stigmatisation, usability, information quality also raised.
- Expectations (23 studies): functionalities 13, natural conversation 9, personalisation 4, sense of control 4. Many want to turn proactive features off and approve data before it is shared with doctors.
- Sample bias: 65 of 78 studies had fewer than 50 older participants; average 66% female; mostly Global North, highly educated.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [PRIVACY/KVKK] Privacy was the single most-cited concern. Show clearly what is recorded or sent, ask consent, and display a visible signal when data is being used or shared. Paper suggests delivering this in natural speech.
- [ELDERLY] Stigma is a major adoption barrier. Frame the app as making the user capable ("someone who is capable"), not as a tool for people who need help.
- [UX] Give the user control: an off switch for proactive features, and review before sharing.
- [AGENT] The paper's AI-role taxonomy (supportive, delegated, cooptable, reciprocal, complementary) maps well onto our Guide mode (supportive/cooptable) and Do-it mode (delegated/complementary). Use this to justify mode boundaries.
- [SAFETY] CAs "are not fully capable of supporting emergency conditions"; our app must not be presented as an emergency tool.
- [LATENCY][MEMORY] Authors recommend offline functionality for internet or power outages; our replay of learned tasks without AI supports this.
- [VOICE] Dialect and accent failures reduced acceptance; plan Turkish regional accent testing.
- [MEMORY] Support long-term use: novelty fades, so design for sustained use, not first-session delight.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Aging-stage adaptation: Active coping (supportive), Adaptive coping (task delegation for targeted impairments), Passive coping (complementary) (Caldeira et al. via this review). Use as a basis for per-user mode defaults.
- Contextual privacy hints inside responses (e.g., reminder not to share sensitive identifiers).
- Modular multi-agent design: separate agents per domain with a coordinator (suggested, not tested).
- Tutorials for confident use, given as in-app onboarding.
- Use of "placeholder" data hints to reduce oversharing.

## Risks / warnings the paper raises
- Anthropomorphism can lead to unintended disclosure of sensitive data.
- Over-reliance: older adults fear losing cognitive and physical activity.
- Family conflict: an Echo disturbed other household members.
- Negative effect in one dementia study: constant reminder of "getting older".
- Authors note mixed and conflicting effects, novelty effects, and small or unrepresentative samples.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Acceptance rating (low / neutral / high) plus stigma items.
- Longitudinal evaluation beyond 2 months; track usage decay.
- Scales used in the reviewed studies: UCLA loneliness (6 studies), GDS (4), WHOQOL (3), SUS (4), PHQ-8 (2).
- Interaction fluency: count breakdowns per session (Desai and Chin definition).
- Privacy comprehension check: can the user state what is stored and where?

## One-line citation
An, J., Yi, S., Lyu, Y., Liu, H., & Zhang, Y. (2025). Conversational Agents for Older Adults' Health: A Systematic Literature Review. arXiv:2503.23153.
