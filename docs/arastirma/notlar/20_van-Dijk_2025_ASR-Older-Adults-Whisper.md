# 20 Out of the Box, into the Clinic? ASR for Older Adults (van Dijk 2025)
**Type:** benchmark · **Relevance:** high

## Core contribution (2-3 lines)
Benchmarks generic multilingual ASR (Whisper, Voxtral) against Dutch and older-Dutch fine-tuned models on speech of 10 older adults (>=65) talking to a geriatric chatbot (Welzijn.AI), plus Common Voice older-speaker samples. Also measures processing time on a laptop, showing a truncated Whisper (large-v3-turbo) as the best accuracy/speed trade-off.

## Key findings (bullets, with numbers)
- Data: 199 segments, mean 3.4 s, 11 min 15 s total from 10 speakers; Common Voice: 200 samples, 17 min 37 s.
- Welzijn.AI WER, row-wise as read from Table 3 (orthographic / orth. clean / normalised): large-v3 .12 / .07 / .06; large-v3-turbo .16 / .10 / .08; large-v2 .19 / .12 / .10; medium .19 / .13 / .11; Voxtral-Mini-3B .17 / .11 / .09; small .26 / .18 / .17; Dutch fine-tuned Wav2Vec2 .49 / .37 / .36; older-Dutch fine-tuned Whisper .40 / .22 / .14. (Column mapping inferred from text; text confirms large-v3 .12 orth and .06 norm.)
- Generic models beat fine-tuned models on both datasets. Fine-tuned models were evaluated on normalised transcripts only in the method, but Table 3 reports all three.
- Processing time per input (s): large-v3 3.41; large-v3-turbo 1.43; small 1.11; Voxtral 3.75; wav2vec2 0.99. Turbo has about half the parameters of large-v3 (809M vs 1.54B) and roughly 2-3x faster.
- Common Voice (read speech): large-v3 .04 orth; Voxtral .05. Conversational speech is harder but not dramatically (.12 vs .04 for large-v3).
- Error analysis of the 50 worst predictions (WER >= 2): mishearings and hallucinations occur about equally; hallucinations (no phonetic or semantic link) judged more harmful to trust. Looping and non-speech background sounds also noted.

## Implications for our app (bullets; tag each: [UX] [ELDERLY] [AGENT] [MEMORY] [LATENCY] [VOICE] [SAFETY] [PRIVACY/KVKK] [EVAL])
- [VOICE] Results are for Dutch, not Turkish; treat them as a method, not a Turkish accuracy estimate. We must run our own Turkish test with older speakers.
- [VOICE][LATENCY] A turbo-size Whisper is a reasonable first candidate for a local, private ASR option; measure on target Android hardware (paper ran on Apple M1, MPS).
- [SAFETY] Hallucinated transcripts can trigger wrong actions. Echo the recognised command back and confirm before any state-changing step (send, delete, pay).
- [PRIVACY/KVKK] Authors used open-weight, locally runnable models for privacy reasons. Supports keeping audio on device.
- [ELDERLY] Speakers were volunteers from a geriatric outpatient clinic; accent and speech-impairment coverage is unknown.
- [UX] A chatbot that shows the transcript (as Welzijn.AI does) gives users a way to see mishearing.

## Techniques we could reuse (concrete: algorithm, prompt pattern, architecture, metric)
- Evaluation protocol: transcribe with a default model, correct by hand, then score three reference variants (orthographic, orthographic-clean, normalised) so the metric choice is explicit.
- Model truncation: use a large model with a shorter decoder for speed (turbo variant).
- Benchmark on a public older-speaker set (Common Voice subset) as sanity check next to own data.
- Error taxonomy: mishearing / hallucination / looping / noise-triggered.
- Segmentation: pydub + pyannote for turn-level segments.

## Risks / warnings the paper raises
- Hallucinations are more damaging to trust than mishearings.
- Small sample (10 speakers, 199 segments); authors say results need larger samples.
- Background non-speech can trigger wrong output; live settings need to account for this.
- Word error rate is sensitive to fillers, capitalisation and punctuation; choice of reference changes conclusions.
- Authors did not share recordings or transcripts due to privacy restrictions.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- WER with 2-3 normalisation levels for Turkish; report the normalisation used.
- Hallucination rate and looping rate as separate metrics.
- Real-time factor and seconds per utterance on our target devices.
- Command-level accuracy: did the intended app action get triggered after confirmation?
- Test with older Turkish speakers in realistic conditions (background noise, TV, phone call).

## One-line citation
van Dijk, B., Kuiper, T., Aoulad si Ahmed, S., Lefebvre, A., Johnson, J., Duin, J., Mooijaart, S., & Spruit, M. (2025). Out of the Box, into the Clinic? Evaluating State-of-the-Art ASR for Clinical Applications for Older Adults. HCI+NLP Workshop, ACL 2025, pp. 72-78.
