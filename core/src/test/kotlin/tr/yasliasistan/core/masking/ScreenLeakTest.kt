package tr.yasliasistan.core.masking

import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.masking.MaskCorpus.obj
import tr.yasliasistan.core.masking.MaskCorpus.str
import tr.yasliasistan.core.masking.MaskCorpus.strList

/** Leak tests of PLAN G-32 (acceptance: "Korpusun ekran örneklerinde sızıntı 0"). */
class ScreenLeakTest {

    private fun parts(e: Map<String, Any?>): List<List<ScreenPart>> {
        @Suppress("UNCHECKED_CAST")
        return (e["dugumler"] as List<Map<String, Any?>>).map { n ->
            val cls = when (n.str("rol")) {
                "ARAYUZ" -> ScreenTextClass.UI
                "DUZENLENEBILIR" -> ScreenTextClass.INPUT
                "PAROLA" -> ScreenTextClass.SECRET
                else -> ScreenTextClass.CONTENT
            }
            listOf(ScreenPart(cls, n.str("metin")))
        }
    }

    @Test
    fun noCorpusValueLeaksFromScreenExamples() {
        val failures = ArrayList<String>()
        for (e in MaskCorpus.examples.filter { it["tur"] == "ekran" || it["tur"] == "birlesik" }) {
            val expected = e.obj("beklenen")
            val session = MaskingSession(MaskCorpus.contactIndex)
            val out = ArrayList<String>()
            if (e["tur"] == "birlesik") {
                val c = session.maskCommand(e.str("girdi"), MaskCorpus.source(e))
                if (c !is CommandMaskResult.Masked) continue
                out += c.text.text
            }
            val r = session.maskParts(parts(e)) as? MaskingSession.PartsResult.Texts ?: continue // blocked = nothing sent
            out += r.texts
            val allowed = (expected.strList("dugumler") + listOfNotNull(expected["cikti"] as String?)).joinToString(" ")
            val found = CorpusLeaks.find(out.joinToString(" | "), allowed)
            if (found.isNotEmpty()) failures += "${e.str("id")}: $found"
        }
        assertTrue("Leaks: $failures", failures.isEmpty())
    }

    /**
     * Stress: every personal value of the whole corpus (commands included) shown as list content,
     * notification or typed text must be masked or block the screen; nothing may pass through.
     */
    @Test
    fun everyCorpusValueIsMaskedAsContentOrInput() {
        val failures = ArrayList<String>()
        for (value in CorpusLeaks.secrets) for (cls in listOf(ScreenTextClass.CONTENT, ScreenTextClass.INPUT)) {
            val session = MaskingSession(MaskCorpus.contactIndex)
            val r = session.maskParts(listOf(listOf(ScreenPart(cls, value)))) as? MaskingSession.PartsResult.Texts ?: continue
            val found = CorpusLeaks.find(r.texts.single(), allowed = "", secretValues = setOf(value))
            if (found.isNotEmpty()) failures += "$cls '${r.texts.single()}': $found"
            val audit = FinalAudit(MaskingDictionary(MaskingLexicon.default()), MaskCorpus.contactIndex)
                .check(r.texts, session.table)
            if (audit != null) failures += "$cls audit $audit on '${r.texts.single()}'"
        }
        assertTrue("Leaks: $failures", failures.isEmpty())
    }

    @Test
    fun leakFinderSeesAnUnmaskedScreenValue() {
        assertTrue(CorpusLeaks.find("[KISI_1], Kargonuz yola çıktı", allowed = "[KISI_1]").isNotEmpty())
    }
}
