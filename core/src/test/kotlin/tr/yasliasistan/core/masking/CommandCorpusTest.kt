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
 * PLAN G-31 acceptance: every command example of mask-corpus.json (and the command part of every
 * sendable `birlesik` example) gives exactly the expected output, mode and placeholder table.
 */
@RunWith(Parameterized::class)
class CommandCorpusTest(private val id: String, private val example: Map<String, Any?>) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = MaskCorpus.commandCases().map { arrayOf(it.str("id"), it) }
    }

    private val expected = example.obj("beklenen")
    private val session = MaskingSession(MaskCorpus.contactIndex)
    private val result = session.maskCommand(example.str("girdi"), MaskCorpus.source(example))

    @Test
    fun matchesExpectedOutput() {
        if (expected["sonuc"] == "ENGELLE") {
            assertEquals(CommandMaskResult.Blocked(BlockReason.valueOf(expected.str("neden"))), result)
            return
        }
        val masked = result as? CommandMaskResult.Masked ?: return fail("$id blocked: $result")
        val cikti = expected.str("cikti")
        assertEquals(cikti, masked.text.text)
        assertEquals(MaskMode.valueOf(if (expected["kip"] == "NIYET") "INTENT" else "FULL"), masked.mode)

        // birlesik: the table also lists screen values; compare only what the command produced.
        val inCommand = MaskCorpus.placeholderRegex.findAll(cikti).map { it.value }.toSet()
        val expectedTable = expected.strMap("esleme").filterKeys { it in inCommand }
        assertEquals(expectedTable, session.table.snapshot())
        assertEquals(expected.strList("ekli").filter { it in inCommand }.toSet(), session.table.suffixedPlaceholders())
    }

    @Test
    fun outputContainsOnlyKnownPlaceholders() {
        val masked = result as? CommandMaskResult.Masked ?: return
        MaskCorpus.placeholderRegex.findAll(masked.text.text).forEach {
            assertTrue("$id: ${it.value} not in table", session.table.valueOf(it.value) != null)
        }
    }
}
