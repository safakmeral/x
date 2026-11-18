package tr.yasliasistan.core.embedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class UnigramTokenizerTest {

    private fun tokenizer(vararg pieces: kotlin.Pair<String, Float>) = UnigramTokenizer(
        pieces.map { it.first },
        pieces.map { it.second }.toFloatArray(),
        IntArray(pieces.size) { it + 10 },
        unkId = 3,
    )

    private fun UnigramTokenizer.ids(word: String) = mutableListOf<Int>().also { encodeWord(word, it) }

    @Test
    fun picksTheMostProbableSplit() {
        // "▁ara" as one piece (-2) beats "▁a" + "ra" (-1.5 + -1.5 = -3).
        val t = tokenizer("▁a" to -1.5f, "ra" to -1.5f, "▁ara" to -2f, "r" to -4f, "a" to -4f)
        assertEquals(listOf(12), t.ids("▁ara"))
    }

    @Test
    fun prefersSeveralPiecesWhenTheyAreMoreProbable() {
        val t = tokenizer("▁a" to -0.5f, "ra" to -0.5f, "▁ara" to -2f, "r" to -4f, "a" to -4f)
        assertEquals(listOf(10, 11), t.ids("▁ara"))
    }

    @Test
    fun unknownCharactersBecomeOneFusedUnknownToken() {
        val t = tokenizer("▁" to -1f, "a" to -1f)
        assertEquals(listOf(10, 3, 11), t.ids("▁xyza"))
    }

    @Test
    fun supplementaryCharactersAreSingleCharacters() {
        val t = tokenizer("▁" to -1f, "🙏" to -2f, "a" to -1f)
        assertEquals(listOf(10, 11, 12), t.ids("▁🙏a"))
        assertEquals(listOf(10, 3), t.ids("▁𓀀𓀁"))
    }

    @Test
    fun emptyWordGivesNothing() {
        assertEquals(emptyList<Int>(), tokenizer("a" to -1f).ids(""))
    }

    @Test
    fun parsesPiecesAndSkipsOtherFields() {
        val bytes = TokenizerTestSupport.model(
            Triple("<unk>", 0f, 2), Triple("<s>", 0f, 3), Triple("</s>", 0f, 3), Triple("▁ara", -2.5f, 1),
        )
        val model = SentencePieceModel.parse(bytes)
        assertEquals(4, model.pieces.size)
        assertEquals(SentencePieceModel.Piece("▁ara", -2.5f, SentencePieceModel.Type.NORMAL), model.pieces[3])
        assertEquals(SentencePieceModel.Type.CONTROL, model.pieces[1].type)
    }

    @Test(expected = IOException::class)
    fun truncatedModelIsRejected() {
        val bytes = TokenizerTestSupport.model(Triple("▁ara", -2.5f, 1))
        SentencePieceModel.parse(bytes.copyOf(bytes.size - 2))
    }

    @Test
    fun xlmRobertaIdsUseTheFairseqOffsetAndSpecialTokens() {
        val model = SentencePieceModel.parse(
            TokenizerTestSupport.model(
                Triple("<unk>", 0f, 2), Triple("<s>", 0f, 3), Triple("</s>", 0f, 3),
                Triple("▁", -3f, 1), Triple("ara", -1f, 1), Triple("▁ara", -1f, 1), Triple("a", -2f, 1),
            ),
        )
        val t = XlmRobertaTokenizer(model)
        // "▁ara" is SentencePiece piece 5 -> id 6; "<s>" text is not matched as a control token.
        assertArrayEquals(longArrayOf(0, 6, 6, 2), t.encode("  ara\tara "))
        assertArrayEquals(longArrayOf(0, 6, 2), t.encode("ara ara ara", maxTokens = 3))
        assertArrayEquals(longArrayOf(0, 2), t.encode(""))
    }
}
