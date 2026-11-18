# 05 ExplorAR: AR-powered Trial-and-Error with Interactive Guidance (Li 2025)
**Type:** system with user study (within-subjects, 18 older adults) · **Relevance:** high

## Core contribution (2-3 lines)
ExplorAR is an AR (HoloLens 2) trial-and-error learning system for older adults. It builds an ExplorTree of app screens and tappable elements, shows green or red borders on each tap, and gives a "Help" highlight of the correct next element. It is compared with a video tutorial and a step-by-step AR instruction mode.

## Key findings (bullets, with numbers)
- Task completion time after learning (independent test): ExplorAR 18.3 s (SD 4.23), video 27.7 s (SD 8.98), AR instruction 34.4 s (SD 9.62).
- Mistakes in the independent test (clicking the wrong icon): ExplorAR 7, video 13, AR instruction 18.
- Learning time: AR instruction 80.9 s (fastest), ExplorAR 109.2 s, video 112.9 s. Authors attribute ExplorAR's extra time to getting used to AR and the trial-and-error approach.
- UEQ-S pragmatic / hedonic quality: ExplorAR 1.31 / 1.59; AR instruction 1.16 / 1.21; video 0.62 / 0.28.
- Participant preference before study: 10 of 18 preferred asking family, 6 video, 3 self-exploration.
- Participants reported higher confidence and less fear of mistakes; concerns about AI accuracy and about visual discomfort with headsets (many reported visual impairment or 3D discomfort).
- Sample: 18 people aged 60+ (mean 65.6, SD 2.91; 13 F, 5 M); three apps (finance, shopping, transport), Latin-square order. The extracted text names significance tests but does not show p-values.

## Implications for our app (bullets; tag each)
- [ELDERLY] Error feedback as a colored border on the current screen (green correct, red wrong) is simple and suits elderly users; it can be done on a phone without AR.
- [UX] Give help only when asked or after inactivity (paper: prompt after 20 s idle), and highlight only the single next element.
- [UX] Enlarge the target and use high-contrast cues; participants asked for larger fonts and adjustable contrast (DI2).
- [MEMORY] ExplorTree (node = screen image + actions; edges = taps) is a concrete format for storing learned tasks for replay.
- [AGENT] The paper's tree was built by an LLM agent (AppAgent) and then checked by hand. This is a candidate offline pipeline for Turkish apps, with manual validation.
- [VOICE] Participants wanted salient audio cues and gentle sounds for errors (DI3).
- [SAFETY] Trial-and-error is safe only in apps where a wrong tap is reversible; the paper suggests extending to payments but gives no evidence for that.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- ExplorNode record: page ID, screenshot, list of (action ID, action name, area, direction, text input) mapping to the next node.
- Screen recognition by matching the current screen to stored node images (paper uses Vuforia; a phone build could use accessibility-tree hashes instead).
- Error recovery: on wrong screen, show the recovery path only after the user presses "Help".
- Mistake count and time-to-complete after learning as the main outcome measures.

## Risks / warnings the paper raises
- Users doubt AI-generated guidance accuracy and authority.
- Headset discomfort and visual impairment limit AR; the paper's AR may not transfer to phones.
- Longer learning time; some users preferred straightforward instruction or videos.
- Screenshot matching depends on the exact app version; frequently updated apps would need re-recording (paper mentions this as a future issue).

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Three-arm design: our Mode 2 vs video vs simplified instruction, Latin-square order, separate learn and recall sessions.
- Recall test after a delay: time and mistake count on similar but new tasks (their "similar task" session design).
- UEQ-S for user experience; add a fear-of-mistake item.
- Track help-button presses and idle-timeouts as signals of confusion.

## One-line citation
Li, J., Qiu, L., Wu, Z., Chen, Q., Wang, Z., & Fan, M. (2025). ExplorAR: Assisting Older Adults to Learn Smartphone Apps through AR-powered Trial-and-Error with Interactive Guidance. ACM MM '25, Dublin. https://doi.org/10.1145/3746027.3755578
