package tr.yasliasistan.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrReportTest {

    private val tiny = AsrSetup("whisper", "ggml-tiny.bin", "ctx=0 th=4 beam=1")
    private val system = AsrSetup("system", "android-ondevice", "-")

    private fun result(setup: AsrSetup, condition: String, ref: String, hyp: String, processMs: Long, audioMs: Long = 2_000, error: String = "") =
        AsrResult("r1", setup, AsrClip("k01", "tts", condition, ref), AsrTiming(audioMs, processMs, loadMs = 300, peakPssMb = 120), hyp, error)

    @Test
    fun csvHasOneLinePerResultAndQuotesCommas() {
        val csv = AsrCsv.format(listOf(result(tiny, "temiz", "Tamam, hatırladım", "tamam hatırladım", 1_000)))
        val lines = csv.trimEnd().lines()
        assertEquals(2, lines.size)
        assertEquals(AsrCsv.HEADER.joinToString(","), lines[0])
        assertTrue(lines[1].contains("\"Tamam, hatırladım\""))
        assertTrue(lines[1].contains(",0.5000,")) // rtf 1000/2000
    }

    @Test
    fun quoteEscapesQuotesAndLineBreaks() {
        assertEquals("\"a \"\"b\"\" c\"", AsrCsv.quote("a \"b\" c"))
        assertEquals("a b", AsrCsv.quote("a\nb"))
    }

    @Test
    fun summaryUsesCorpusLevelWerAndCountsHallucinations() {
        val rows = listOf(
            result(tiny, "temiz", "ayarları aç", "ayarları aç", 1_000),
            result(tiny, "temiz", "sesi kıs biraz", "sesi kış", 3_000),
            result(tiny, "sessiz", "", "Altyazı M.K.", 2_000),
            result(tiny, "sessiz", "", "", 1_000),
            result(system, "temiz", "dur", "", 500, error = "no_match"),
        )
        val summary = AsrSummary.summarize(rows)
        assertEquals(3, summary.size)

        val clean = summary.single { it.setup == tiny && it.condition == "temiz" }
        // 2 errors (1 substitution at BASIC, 1 deletion) over 5 reference words
        assertEquals(0.4, clean.words.getValue(NormalizationLevel.BASIC).rate!!, 1e-9)
        assertEquals(2_000.0, clean.meanProcessMs, 0.0)
        assertEquals(1.0, clean.rtf, 1e-9)

        val silent = summary.single { it.condition == "sessiz" }
        assertEquals(2, silent.silentClips)
        assertEquals(1, silent.hallucinations)

        val sys = summary.single { it.setup == system }
        assertEquals(1, sys.failures)
        assertEquals(1.0, sys.words.getValue(NormalizationLevel.RAW).rate!!, 0.0)

        val md = AsrSummary.markdown(summary)
        assertTrue(md.contains("| whisper/ggml-tiny.bin (ctx=0 th=4 beam=1) | sessiz | 2 | 0 | - |"))
        assertTrue(md.contains("| 1/2 |"))
        assertEquals(summary.size + 1, AsrSummary.csv(summary).trimEnd().lines().size)
    }

    @Test
    fun manifestRoundTripsAndRejectsBadLines() {
        val entries = listOf(
            AsrManifest.Entry("k01", "k01.wav", "temiz", "Ayarları aç"),
            AsrManifest.Entry("s01", "s01.wav", "sessiz", ""),
        )
        val (parsed, problems) = AsrManifest.parse(AsrManifest.format(entries))
        assertEquals(entries, parsed)
        assertTrue(problems.isEmpty())

        val (bad, badProblems) = AsrManifest.parse("x\t../gizli.wav\ttemiz\tmetin\nk01\tk01.wav\nk02\tk02.mp3\ttemiz\ta\n")
        assertTrue(bad.isEmpty())
        assertEquals(3, badProblems.size)
    }
}
