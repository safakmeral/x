package tr.yasliasistan.core.embedding

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * G-43 acceptance: the Kotlin tokenizer gives exactly the token ids of Hugging Face `tokenizers`
 * for multilingual-e5-small. Reference ids were produced once with the free reference tool
 * (tools/gomme-referans.py) and stored in src/test/resources/embedding/reference-ids.tsv.
 *
 * Needs the official sentencepiece.bpe.model (5 MB, git-ignored); run tools/gomme-model-indir.ps1
 * first. Without it the test is skipped and says so.
 */
class XlmRobertaReferenceTest {

    companion object {
        private var tokenizer: XlmRobertaTokenizer? = null

        @BeforeClass
        @JvmStatic
        fun load() {
            val file = TokenizerTestSupport.realModel
            if (!file.isFile) return
            val rt = Runtime.getRuntime()
            System.gc()
            val heapBefore = rt.totalMemory() - rt.freeMemory()
            val start = System.nanoTime()
            tokenizer = XlmRobertaTokenizer.load(file)
            val loadMs = (System.nanoTime() - start) / 1_000_000
            System.gc()
            val heapMb = (rt.totalMemory() - rt.freeMemory() - heapBefore) / 1_048_576
            // Desktop numbers for docs/olcum/gomme.md (only sizes and times).
            println("G-43 JVM tokenizer: load=$loadMs ms, heap=$heapMb MB")
        }
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                append(
                    when (s[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> s[i + 1]
                    },
                )
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    private fun referenceRows(): List<kotlin.Pair<String, List<Long>>> {
        val text = javaClass.getResource("/embedding/reference-ids.tsv")!!.readText(Charsets.UTF_8)
        return text.lines().filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
            val tab = line.lastIndexOf('\t')
            unescape(line.substring(0, tab)) to line.substring(tab + 1).split(' ').map { it.toLong() }
        }
    }

    @Test
    fun idsMatchTheReferenceTokenizer() {
        val t = tokenizer
        assumeTrue("models/gomme/sentencepiece.bpe.model yok: tools/gomme-model-indir.ps1 çalıştırın", t != null)
        val rows = referenceRows()
        assertEquals(77, rows.size)
        val mismatches = rows.filter { (raw, ids) -> t!!.encode(raw).toList() != ids }
            .map { (raw, ids) -> "'$raw': expected $ids, got ${t!!.encode(raw).toList()}" }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun longInputIsCutToTheTokenLimit() {
        val t = tokenizer
        assumeTrue(t != null)
        val ids = t!!.encode("query: " + "WhatsApp'ı aç ".repeat(40), E5TextEmbedder.MAX_TOKENS)
        assertEquals(E5TextEmbedder.MAX_TOKENS, ids.size)
        assertEquals(0L, ids.first())
        assertEquals(2L, ids.last())
    }
}
