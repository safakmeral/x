package tr.yasliasistan.core.masking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import tr.yasliasistan.core.masking.MaskCorpus.obj
import tr.yasliasistan.core.masking.MaskCorpus.str
import tr.yasliasistan.core.masking.MaskCorpus.strList
import tr.yasliasistan.core.masking.MaskCorpus.strMap

/**
 * PLAN G-32 acceptance on mask-corpus.json: every `ekran` and `birlesik` example gives exactly the
 * expected node texts and shared placeholder table (or the expected block), the result passes the
 * final audit, and every `son_denetim` example gets the expected verdict.
 */
@RunWith(Parameterized::class)
class ScreenCorpusTest(private val id: String, private val example: Map<String, Any?>) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = MaskCorpus.examples
            .filter { it["tur"] in setOf("ekran", "birlesik", "son_denetim") }
            .map { arrayOf(it.str("id"), it) }
    }

    private val expected = example.obj("beklenen")

    @Test
    fun matchesExpectedResult() {
        if (example["tur"] == "son_denetim") return checkAudit()
        val session = MaskingSession(MaskCorpus.contactIndex)
        val outgoing = ArrayList<String>()
        var command: MaskedText? = null
        if (example["tur"] == "birlesik") {
            when (val c = session.maskCommand(example.str("girdi"), MaskCorpus.source(example))) {
                is CommandMaskResult.Masked -> {
                    command = c.text
                    if (expected["sonuc"] == "GONDER") assertEquals(expected.str("cikti"), c.text.text)
                    outgoing += c.text.text
                }
                is CommandMaskResult.Blocked -> {
                    assertEquals(expected.str("neden"), c.reason.name)
                    return
                }
            }
        }
        val result = session.maskParts(nodes())
        if (expected["sonuc"] == "ENGELLE") {
            assertEquals(MaskingSession.PartsResult.Block(BlockReason.valueOf(expected.str("neden"))), result)
            assertEquals("nothing may be numbered for a blocked screen", 0, session.table.size - tableSizeOf(command))
            return
        }
        val texts = (result as? MaskingSession.PartsResult.Texts ?: return fail("$id: $result")).texts
        assertEquals(expected.strList("dugumler"), texts)
        assertEquals(expected.strMap("esleme"), session.table.snapshot())
        outgoing += texts
        assertEquals("$id final audit", null, FinalAudit(dictionary, MaskCorpus.contactIndex).check(outgoing, session.table))
    }

    private fun tableSizeOf(command: MaskedText?) =
        command?.let { MaskCorpus.placeholderRegex.findAll(it.text).map { m -> m.value }.toSet().size } ?: 0

    /** Corpus roles map one to one onto the policy classes of §6. */
    private fun nodes(): List<List<ScreenPart>> {
        @Suppress("UNCHECKED_CAST")
        val raw = example["dugumler"] as List<Map<String, Any?>>
        return raw.map { n ->
            val cls = when (n.str("rol")) {
                "ARAYUZ" -> ScreenTextClass.UI
                "LISTE", "BILDIRIM", "OZET" -> ScreenTextClass.CONTENT
                "DUZENLENEBILIR" -> ScreenTextClass.INPUT
                "PAROLA" -> ScreenTextClass.SECRET
                else -> error("$id: unknown role ${n["rol"]}")
            }
            listOf(ScreenPart(cls, n.str("metin")))
        }
    }

    private fun checkAudit() {
        val table = PlaceholderTable.of(example.strMap("esleme"))
        val verdict = FinalAudit(dictionary, MaskCorpus.contactIndex).check(example.strList("giden"), table)
        if (expected["sonuc"] == "GONDER") assertEquals(null, verdict)
        else assertEquals(BlockReason.valueOf(expected.str("neden")), verdict)
    }

    @Test
    fun outputHasOnlyKnownPlaceholders() {
        if (example["tur"] == "son_denetim" || expected["sonuc"] != "GONDER") return
        val session = MaskingSession(MaskCorpus.contactIndex)
        if (example["tur"] == "birlesik") session.maskCommand(example.str("girdi"), MaskCorpus.source(example))
        val texts = (session.maskParts(nodes()) as MaskingSession.PartsResult.Texts).texts
        texts.forEach { t ->
            MaskCorpus.placeholderRegex.findAll(t).forEach {
                assertTrue("$id: ${it.value}", session.table.valueOf(it.value) != null)
            }
        }
    }

    private val dictionary get() = MaskingDictionary(MaskingLexicon.default())
}
