package tr.yasliasistan.core.asr

import java.util.Locale

/**
 * Machine-readable output of the measurement (one row per clip and setup). Debug/measurement
 * only; texts are the made-up test commands and what the recognizer heard for them.
 */
object AsrCsv {

    val HEADER = listOf(
        "run", "engine", "model", "settings", "source", "condition", "clip",
        "audio_ms", "process_ms", "rtf", "load_ms", "peak_pss_mb", "vad",
        "n_ham", "err_ham", "wer_ham",
        "n_noktalama", "err_noktalama", "wer_noktalama",
        "n_turkce", "err_turkce", "wer_turkce",
        "cer_turkce", "hallucination", "loop", "error", "reference", "hypothesis",
    )

    fun format(rows: List<AsrResult>): String = buildString {
        append(HEADER.joinToString(",")).append('\n')
        rows.forEach { append(line(it)).append('\n') }
    }

    fun line(r: AsrResult): String {
        val cells = mutableListOf<Any?>(
            r.run, r.setup.engine, r.setup.model, r.setup.settings, r.clip.source, r.clip.condition, r.clip.id,
            r.timing.audioMs, r.timing.processMs, num(r.timing.rtf), r.timing.loadMs, r.timing.peakPssMb, r.timing.vad,
        )
        for (level in NormalizationLevel.entries) {
            val c = r.words.getValue(level)
            cells += c.referenceLength
            cells += c.errors
            cells += c.rate?.let(::num)
        }
        cells += r.chars.rate?.let(::num)
        cells += if (r.hasSpeech) "" else r.hallucination.toInt()
        cells += r.loop.toInt()
        cells += r.error
        cells += r.clip.reference
        cells += r.hypothesis
        return cells.joinToString(",") { quote(it?.toString() ?: "") }
    }

    fun num(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

    /** RFC 4180 quoting: only when the cell has a comma, quote or line break. */
    fun quote(cell: String): String {
        val flat = cell.replace('\r', ' ').replace('\n', ' ')
        return if (flat.any { it == ',' || it == '"' }) "\"" + flat.replace("\"", "\"\"") + "\"" else flat
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}
