package tr.yasliasistan.core.masking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.masking.MaskCorpus.obj
import tr.yasliasistan.core.masking.MaskCorpus.str

/**
 * Leak test (PLAN G-31): no secret value of the corpus, nor a word or digit group of it, may appear
 * in a masked command unless the corpus itself expects that word to stay (e.g. "Merhaba").
 * Also checks that masking then decoding gives back every value (round trip).
 */
class MaskingLeakTest {

    private fun leaks(output: String, allowed: String) = CorpusLeaks.find(output, allowed)

    @Test
    fun noCorpusValueLeaksIntoMaskedCommands() {
        val failures = ArrayList<String>()
        for (e in MaskCorpus.commandCases()) {
            val expected = e.obj("beklenen")
            if (expected["sonuc"] != "GONDER") continue
            val session = MaskingSession(MaskCorpus.contactIndex)
            val result = session.maskCommand(e.str("girdi"), MaskCorpus.source(e))
            if (result !is CommandMaskResult.Masked) continue // blocked = nothing sent
            val found = leaks(result.text.text, expected.str("cikti"))
            if (found.isNotEmpty()) failures += "${e.str("id")}: $found"
        }
        assertTrue("Leaks: $failures", failures.isEmpty())
    }

    @Test
    fun leakDetectorItselfFindsAnUnmaskedName() {
        assertTrue(leaks("Ayşe Test'i ara", "[KISI_1]'i ara").isNotEmpty())
        assertTrue(leaks("0599 000 00 01 ara", "[TEL_1] ara").isNotEmpty())
    }

    @Test
    fun maskThenUnmaskRestoresEveryValue() {
        for (e in MaskCorpus.commandCases()) {
            val session = MaskingSession(MaskCorpus.contactIndex)
            val result = session.maskCommand(e.str("girdi"), MaskCorpus.source(e))
            if (result !is CommandMaskResult.Masked) continue
            val table = session.table.snapshot()
            for ((placeholder, value) in table) {
                val decoded = session.unmask("$placeholder ${result.text.text}")
                val text = (decoded as UnmaskResult.Restored).text
                assertTrue("${e.str("id")}: $placeholder", text.startsWith("$value "))
                assertTrue("${e.str("id")}: placeholder left", !MaskCorpus.placeholderRegex.containsMatchIn(text))
            }
        }
    }

    @Test
    fun clearForgetsValues() {
        val session = MaskingSession(MaskCorpus.contactIndex)
        session.maskCommand("ayşeyi ara", InputSource.SPEECH)
        session.clear()
        assertEquals(UnmaskResult.Rejected(BlockReason.BILINMEYEN_YER_TUTUCU), session.unmask("[KISI_1]'i arıyorum"))
    }
}
