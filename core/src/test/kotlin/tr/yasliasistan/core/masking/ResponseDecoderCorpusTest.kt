package tr.yasliasistan.core.masking

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import tr.yasliasistan.core.masking.MaskCorpus.obj
import tr.yasliasistan.core.masking.MaskCorpus.str
import tr.yasliasistan.core.masking.MaskCorpus.strList
import tr.yasliasistan.core.masking.MaskCorpus.strMap

/** `geri_cozme` examples of the corpus (docs/MASKELEME.md §8). */
@RunWith(Parameterized::class)
class ResponseDecoderCorpusTest(private val id: String, private val example: Map<String, Any?>) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            MaskCorpus.examples.filter { it["tur"] == "geri_cozme" }.map { arrayOf(it.str("id"), it) }
    }

    @Test
    fun decodesAsExpected() {
        val table = PlaceholderTable.of(example.strMap("esleme"), example.strList("ekli"))
        val result = ResponseDecoder.decode(example.str("yanit"), table, MaskingDictionary(MaskingLexicon.default()))
        val expected = example.obj("beklenen")
        if (expected["sonuc"] == "TAMAM") {
            assertEquals(UnmaskResult.Restored(expected.str("metin")), result)
        } else {
            assertEquals(UnmaskResult.Rejected(BlockReason.valueOf(expected.str("neden"))), result)
        }
    }
}
