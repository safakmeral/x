package tr.yasliasistan.speech.tts

/** Speaking speeds (V-01, NFG-20: slowable down to 80 %, MAKALE-22). 1.0 = engine normal. */
enum class SpeechRate(val factor: Float) {
    NORMAL(1.0f),
    SLOW(0.8f),
}
