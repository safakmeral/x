package tr.yasliasistan.speech.whisper

/**
 * Settings of the Whisper engine. Defaults favour short waits (PLAN priority principle);
 * G-13/G-14 compare models and settings with these knobs.
 */
data class WhisperOptions(
    /** Model file name inside the model folders (see [WhisperModelStore]); chosen at run time. */
    val modelFileName: String = WhisperModels.TINY,
    /** Whisper language code; fixed to Turkish (G-12). */
    val language: String = TURKISH,
    /** Decoding threads; 0 = choose by device ([DeviceProfile.threadsFor]). */
    val threads: Int = 0,
    /** Encoder context (0 = full 30 s window). Smaller is faster for short commands but may cost accuracy. */
    val audioCtx: Int = 0,
    /** 1 = greedy (fastest); >1 = beam search. */
    val beamSize: Int = 1,
    val flashAttention: Boolean = false,
    /** Longest wait for the text after the user stops talking; the slow "small" model needs seconds. */
    val decodeTimeoutMs: Long = 30_000L,
    /** The model is freed after this much idle time (low RAM, NFG-72). */
    val idleUnloadMs: Long = 120_000L,
) {
    companion object {
        const val TURKISH = "tr"
    }
}
