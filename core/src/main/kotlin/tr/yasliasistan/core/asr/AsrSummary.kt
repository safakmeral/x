package tr.yasliasistan.core.asr

import java.util.Locale

/** Totals of one setup on one condition (corpus-level WER: summed errors / summed reference words). */
data class AsrSummaryRow(
    val setup: AsrSetup,
    val condition: String,
    val clips: Int,
    val failures: Int,
    val words: Map<NormalizationLevel, EditCounts>,
    val chars: EditCounts,
    val meanProcessMs: Double,
    val medianProcessMs: Double,
    /** Total processing time / total audio time. */
    val rtf: Double,
    val maxLoadMs: Long,
    val peakPssMb: Int,
    /** Clips without speech, and how many of them produced text. */
    val silentClips: Int,
    val hallucinations: Int,
    val loops: Int,
)

object AsrSummary {

    fun summarize(results: List<AsrResult>): List<AsrSummaryRow> =
        results.groupBy { it.setup to it.clip.condition }.map { (key, rows) ->
            val times = rows.map { it.timing.processMs }.sorted()
            val audio = rows.sumOf { it.timing.audioMs }
            val silent = rows.filter { !it.hasSpeech }
            AsrSummaryRow(
                setup = key.first,
                condition = key.second,
                clips = rows.size,
                failures = rows.count { it.error.isNotEmpty() },
                words = NormalizationLevel.entries.associateWith { level ->
                    rows.fold(EditCounts.ZERO) { acc, r -> acc + r.words.getValue(level) }
                },
                chars = rows.fold(EditCounts.ZERO) { acc, r -> acc + r.chars },
                meanProcessMs = times.average(),
                medianProcessMs = median(times),
                rtf = if (audio == 0L) 0.0 else times.sum().toDouble() / audio,
                maxLoadMs = rows.maxOf { it.timing.loadMs },
                peakPssMb = rows.maxOf { it.timing.peakPssMb },
                silentClips = silent.size,
                hallucinations = silent.count { it.hallucination },
                loops = rows.count { it.loop },
            )
        }

    val CSV_HEADER = listOf(
        "engine", "model", "settings", "condition", "clips", "failures",
        "wer_ham", "wer_noktalama", "wer_turkce", "cer_turkce",
        "mean_process_ms", "median_process_ms", "rtf", "max_load_ms", "peak_pss_mb",
        "silent_clips", "hallucinations", "loops",
    )

    fun csv(rows: List<AsrSummaryRow>): String = buildString {
        append(CSV_HEADER.joinToString(",")).append('\n')
        for (r in rows) {
            val cells = listOf<Any>(
                r.setup.engine, r.setup.model, r.setup.settings, r.condition, r.clips, r.failures,
                rate(r.words.getValue(NormalizationLevel.RAW)),
                rate(r.words.getValue(NormalizationLevel.BASIC)),
                rate(r.words.getValue(NormalizationLevel.TURKISH)),
                rate(r.chars),
                AsrCsv.num(r.meanProcessMs), AsrCsv.num(r.medianProcessMs), AsrCsv.num(r.rtf),
                r.maxLoadMs, r.peakPssMb, r.silentClips, r.hallucinations, r.loops,
            )
            append(cells.joinToString(",") { AsrCsv.quote(it.toString()) }).append('\n')
        }
    }

    /** Turkish summary table for docs/olcum (percentages, seconds). */
    fun markdown(rows: List<AsrSummaryRow>): String = buildString {
        append("| Motor | Koşul | Klip | Hata | WER ham | WER noktalama | WER Türkçe | CER | Ort. süre (sn) | Medyan (sn) | RTF | Model yükleme (sn) | Tepe bellek (MB) | Halüsinasyon | Döngü |\n")
        append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        for (r in rows) {
            val cells = listOf(
                r.setup.label, r.condition, r.clips.toString(), r.failures.toString(),
                percent(r.words.getValue(NormalizationLevel.RAW)),
                percent(r.words.getValue(NormalizationLevel.BASIC)),
                percent(r.words.getValue(NormalizationLevel.TURKISH)),
                percent(r.chars),
                seconds(r.meanProcessMs), seconds(r.medianProcessMs), fmt("%.2f", r.rtf),
                if (r.maxLoadMs == 0L) "-" else seconds(r.maxLoadMs.toDouble()),
                if (r.peakPssMb == 0) "-" else r.peakPssMb.toString(),
                if (r.silentClips == 0) "-" else "${r.hallucinations}/${r.silentClips}",
                "${r.loops}/${r.clips}",
            )
            append("| ").append(cells.joinToString(" | ")).append(" |\n")
        }
    }

    private fun median(sorted: List<Long>): Double = when {
        sorted.isEmpty() -> 0.0
        sorted.size % 2 == 1 -> sorted[sorted.size / 2].toDouble()
        else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }

    private fun rate(c: EditCounts) = c.rate?.let(AsrCsv::num) ?: ""
    private fun percent(c: EditCounts) = c.rate?.let { fmt("%%%.1f", it * 100) } ?: "-"
    private fun seconds(ms: Double) = fmt("%.2f", ms / 1000)
    private fun fmt(pattern: String, value: Double) = String.format(Locale.ROOT, pattern, value)
}
