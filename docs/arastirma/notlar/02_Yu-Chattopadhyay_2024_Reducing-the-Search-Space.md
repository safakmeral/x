# 02 Reducing the Search Space on demand (Yu 2024)
**Type:** user study + system · **Relevance:** high

## Core contribution (2-3 lines)
Shows that visually reducing a feature-rich mobile UI to 3 candidate elements helps older adults find a feature as fast as younger adults. Presents Nav Nudge: voice query -> LLM keyword extraction -> semantic matching against the current screen's UI labels -> visual emphasis of the top matches.

## Key findings (bullets, with numbers)
- Controlled study, n=60 (30 older 60+, 30 younger 20-29); 56 analysed (28/28). Online, screenshots rendered as mobile screens.
- Full search space (no cue): older adults 6.24 s vs younger 4.15 s, t(54)=8.91, p<.001; error median .125 vs .04 (U=259, p=.006).
- Reduced search space (3 features): older adults 4.49 s vs 6.24 s without cue (p<.001); errors median 0 vs .125 (p=.001). Age difference not significant (pairwise p=.41 time, p=.32 errors); TOST equivalence at d=.55 significant.
- Phase 1 cue comparison (older adults): highlight with context (HC) 4.2 s and weighted zoom (WZ) 4.08 s, both faster than zoom (4.74 s) and highlight (4.97 s), ps<.001 for WZ. Error rates did not differ across cues. Text cue type (content label vs manual instruction) had no significant effect.
- Preference: HC and WZ ranked highest (median 3) vs highlight (1) and zoom (2).
- Selection search-space size: 3 chosen by "elbow" in accuracy curve; Universal Sentence Encoder v4 gave 87.2% matching accuracy at size >=3.
- Keyword extraction in Nav Nudge: switched from APE to a GPT-3-family prompt ("Extract a keyword...") giving 94% (queries) and 93.4% (UI labels).
- Field study: 10 older adults 60+, Organic Maps on Samsung Galaxy S23 with speech input. 8/10 used Nav Nudge; 19 uses; 18/19 had the target in the reduced set. Participants got lost 20 times; used Nav Nudge in 15; recovered in all 15 cases.
- Older participants reported mixed preference: HC "easy to see at a glance" vs WZ "HC blocks the view".

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [UX][ELDERLY] In Guide mode, emphasise at most 3 candidate on-screen controls for a spoken "where is X" question, instead of marking the whole screen.
- [UX] Prefer a dimmed-overlay-with-context or zoom-on-target cue over a bare red rectangle; keep the spatial neighbourhood visible.
- [AGENT] Use the Android accessibility tree (node text, content description, bounds) as the candidate list, as this paper did via the Assist API. Hidden or scroll-only nodes are included in their list; decide whether we filter them.
- [VOICE] Voice recogniser timeout (1.5 s) caused one failed invocation; give the user a longer listening window and an easy retry.
- [LATENCY] Their matching ran on a server over REST. Decide the on-device vs cloud split before committing; the paper does not report latency numbers.
- [PRIVACY/KVKK] Sending screen labels and voice queries to a server raises KVKK questions; the paper does not discuss privacy. Minimise what leaves the device.
- [SAFETY] Emphasis is a suggestion only; it does not act. This fits the interruptible Guide-mode design.
- [ELDERLY] Paper argues older adults who prefer self-exploration benefit most; not all older adults want this kind of help.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Pipeline: (1) keyword extraction from user query; (2) extract UI elements + labels from current view; (3) rank by sentence-embedding cosine similarity; keep top-k with score > 0.3, k=3.
- Visual cue set: HC (dim overlay with a circle of 3x the element's longest side kept clear), WZ (magnify top-3 by rank: 1.6x, 1.4x, 1.2x), Z (1.3x all).
- Per-user cue selection: Phase 1 picked the two cues with fewest errors for each participant; reuse as an adaptive cue choice.
- Timeout rule: show the cue after 13 s without a correct tap (2.5 x pilot mean of 5.2 s).
- Prompt: "Extract a keyword from the following sentence: <INPUT>, Keyword:".

## Risks / warnings the paper raises
- Assumes the user knows some words for the feature they want; no solution for unknown features.
- Works only within the current screen, single step; no multi-step or multi-app support.
- Visual-only output; authors note this excludes users with visual impairment.
- Overlays can obscure neighbouring controls (HC and WZ both cover parts of the UI).
- Small field sample (n=10); first study used screenshots, not live apps; online sample from university mailing lists.
- Older adults who prefer social or external help would not benefit.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Time to correct tap and error rate (incorrect taps / total taps) with and without cue, per age group.
- Give-up rate and time-to-give-up; 13 s cut-off protocol.
- Top-k containment rate: share of queries where the target is in the emphasised set (their 18/19).
- Equivalence testing (TOST) for older vs younger performance, with SESOI set a priori.
- Mobile device proficiency screen: MDPQ-14.
- Recovery rate after "getting lost" episodes (consecutive off-task or repeated taps) and whether the assistant was used.

## One-line citation
Yu, J. E., & Chattopadhyay, D. (2024). Reducing the Search Space on demand helps Older Adults find Mobile UI Features quickly, on par with Younger Adults. CHI '24. https://doi.org/10.1145/3613904.3642796
