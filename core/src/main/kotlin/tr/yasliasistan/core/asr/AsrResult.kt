package tr.yasliasistan.core.asr

/** What was measured: one engine with one model and one group of settings. */
data class AsrSetup(
    /** "whisper" or "system". */
    val engine: String,
    /** Model file name for Whisper; "android-ondevice" for the system recognizer. */
    val model: String,
    /** Short settings label, e.g. "ctx=0 th=4 beam=1"; "-" if not applicable. */
    val settings: String,
) {
    val label: String get() = if (settings == "-") "$engine/$model" else "$engine/$model ($settings)"
}

/** Where a clip came from and what it contains. Text fields are made-up test commands only. */
data class AsrClip(
    val id: String,
    /** "tts" (phone's own voice) or "kayit" (a human recording from the data folder). */
    val source: String,
    /** "temiz", "gurultulu", "sessiz" or a label from the data folder's manifest. */
    val condition: String,
    /** Expected text; empty for clips without speech. */
    val reference: String,
)

/** Timing and memory of one recognition (no text). */
data class AsrTiming(
    val audioMs: Long,
    /** Time from handing over the audio to getting the text. */
    val processMs: Long,
    /** Model load time (Whisper), 0 when not applicable. */
    val loadMs: Long = 0,
    /** Highest process PSS seen while this clip was processed, MB; 0 if unknown. */
    val peakPssMb: Int = 0,
    /** "kesildi" (VAD cut), "ham" (fed as is), "reddetti" (VAD found no speech; fed as is). */
    val vad: String = "ham",
) {
    /** Real-time factor: processing time / audio length (below 1 = faster than real time). */
    val rtf: Double get() = if (audioMs <= 0) 0.0 else processMs.toDouble() / audioMs
}

/** One row of the measurement: a clip run through a setup. [error] is set when nothing came back. */
data class AsrResult(
    val run: String,
    val setup: AsrSetup,
    val clip: AsrClip,
    val timing: AsrTiming,
    val hypothesis: String,
    val error: String = "",
) {
    val words: Map<NormalizationLevel, EditCounts> by lazy {
        NormalizationLevel.entries.associateWith { ErrorRate.words(clip.reference, hypothesis, it) }
    }
    val chars: EditCounts by lazy { ErrorRate.chars(clip.reference, hypothesis, NormalizationLevel.TURKISH) }
    val hallucination: Boolean get() = error.isEmpty() && TranscriptChecks.isHallucination(clip.reference, hypothesis)
    val loop: Boolean get() = error.isEmpty() && TranscriptChecks.isLoop(clip.reference, hypothesis)
    val hasSpeech: Boolean get() = TranscriptNormalizer.tokens(clip.reference, NormalizationLevel.RAW).isNotEmpty()
}
