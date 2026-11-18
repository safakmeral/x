package tr.yasliasistan.core.embedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedderTest {

    /** One real model output stored by tools/gomme-referans.py, with the sentence-transformers result. */
    private class PoolingFixture {
        val ids: LongArray
        val hidden: FloatArray
        val expected: FloatArray

        init {
            val lines = javaClass.getResource("/embedding/pooling-fixture.txt")!!.readText().lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
            ids = lines.first().split(' ').map { it.toLong() }.toLongArray()
            val rows = lines.subList(1, lines.size - 1)
            assertEquals(ids.size, rows.size)
            hidden = rows.flatMap { r -> r.split(' ').map { it.toFloat() } }.toFloatArray()
            expected = lines.last().split(' ').map { it.toFloat() }.toFloatArray()
        }
    }

    private class FixedEncoder(private val hidden: FloatArray) : TokenEncoder {
        override val dimension = E5TextEmbedder.DIMENSION
        var seen: LongArray? = null
        var closed = false
        override fun lastHiddenState(ids: LongArray): FloatArray {
            seen = ids
            return hidden
        }
        override fun close() {
            closed = true
        }
    }

    @Test
    fun meanPoolingAndNormalizationMatchTheReference() {
        val f = PoolingFixture()
        val pooled = VectorMath.l2Normalize(VectorMath.meanPool(f.hidden, f.ids.size, E5TextEmbedder.DIMENSION))
        assertArrayEquals(f.expected, pooled, 1e-5f)
        assertEquals(1.0, VectorMath.norm(pooled), 1e-5)
    }

    @Test
    fun e5EmbedderAddsTheQueryPrefixAndPools() {
        val f = PoolingFixture()
        val model = SentencePieceModel.parse(
            TokenizerTestSupport.model(Triple("<unk>", 0f, 2), Triple("<s>", 0f, 3), Triple("</s>", 0f, 3), Triple("▁query:", -1f, 1), Triple("▁x", -1f, 1)),
        )
        val encoder = FixedEncoder(FloatArray(4 * E5TextEmbedder.DIMENSION) { if (it % E5TextEmbedder.DIMENSION == 0) 2f else 0f })
        val embedder = E5TextEmbedder(XlmRobertaTokenizer(model), encoder)
        val v = embedder.embed("x")
        // "query: x" -> <s> ▁query: ▁x </s>
        assertArrayEquals(longArrayOf(0, 4, 5, 2), encoder.seen)
        assertEquals(1f, v[0], 1e-6f)
        assertEquals(E5TextEmbedder.DIMENSION, v.size)
        embedder.close()
        assertTrue(encoder.closed)
        assertEquals(E5TextEmbedder.DIMENSION, f.expected.size)
    }

    @Test
    fun cosineOfUnitVectors() {
        val a = VectorMath.l2Normalize(floatArrayOf(3f, 4f))
        assertEquals(1f, VectorMath.cosine(a, a), 1e-6f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(0f, 1f)), 0f)
        assertEquals(-1f, VectorMath.cosine(floatArrayOf(1f, 1f), floatArrayOf(-2f, -2f)), 1e-6f)
    }

    @Test
    fun lazyEmbedderLoadsOnceAndReleases() {
        var created = 0
        var closed = 0
        val lazy = LazyTextEmbedder(2) {
            created++
            object : TextEmbedder {
                override val dimension = 2
                override fun embed(text: String) = floatArrayOf(1f, 0f)
                override fun close() {
                    closed++
                }
            }
        }
        assertFalse(lazy.isLoaded)
        lazy.embed("a")
        lazy.embed("b")
        assertEquals(1, created)
        assertTrue(lazy.isLoaded)
        lazy.release()
        assertFalse(lazy.isLoaded)
        assertEquals(1, closed)
        lazy.release()
        lazy.embed("c")
        assertEquals(2, created)
        lazy.close()
        assertEquals(2, closed)
    }

    @Test
    fun probeFindsSafeAndBestThresholds() {
        val pairs = SimilarityProbe.parse(
            "# comment\nAYNI\ta\tb\nAYNI\tc\td\r\nFARKLI\ta\tc\nFARKLI\tb\td\n",
        )
        assertEquals(4, pairs.size)
        val sims = mapOf("a|b" to 0.97f, "c|d" to 0.90f, "a|c" to 0.93f, "b|d" to 0.85f)
        val scored = pairs.map { SimilarityProbe.Scored(it, sims.getValue("${it.a}|${it.b}")) }
        val s = SimilarityProbe.summarize(scored, defaultThreshold = 0.80f)
        assertEquals(0.5, s.accuracyAtDefault, 1e-9)
        assertEquals(0.90f, s.sameMin)
        assertEquals(0.93f, s.differentMax)
        assertTrue(s.safeThreshold > 0.93f && s.safeThreshold < 0.9301f)
        assertEquals(1, s.sameAcceptedAtSafe)
        assertEquals(0.75, s.bestAccuracy, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun probeRejectsUnknownLabels() {
        SimilarityProbe.parse("BELKI\ta\tb")
    }
}
