package tr.yasliasistan.core.embedding

import java.io.File
import java.text.Normalizer

/**
 * The XLM-RoBERTa tokenizer used by multilingual-e5-small (V-03), rebuilt in Kotlin from the
 * official `sentencepiece.bpe.model` so no tokenizer library is needed on the phone. Output ids
 * equal Hugging Face `tokenizers` for the same text (checked against reference ids in tests).
 *
 * Steps: NFKC normalization (the model's "nmt_nfkc" rules reduce to NFKC for ordinary text),
 * control characters dropped, whitespace trimmed and collapsed (commands carry no meaningful
 * spacing), each word prefixed with "▁" (Metaspace), Unigram per word, then `<s> ... </s>`.
 */
class XlmRobertaTokenizer(model: SentencePieceModel) {

    private val unigram: UnigramTokenizer

    init {
        val texts = ArrayList<String>(model.pieces.size)
        val scores = ArrayList<Float>(model.pieces.size)
        val ids = ArrayList<Int>(model.pieces.size)
        model.pieces.forEachIndexed { spmIndex, piece ->
            if (piece.type == SentencePieceModel.Type.NORMAL || piece.type == SentencePieceModel.Type.USER_DEFINED) {
                texts += piece.text
                scores += piece.score
                ids += spmIndex + FAIRSEQ_OFFSET
            }
        }
        unigram = UnigramTokenizer(texts, scores.toFloatArray(), ids.toIntArray(), UNK_ID)
    }

    /** Token ids including `<s>` and `</s>`, at most [maxTokens] long (longer text is cut at the end). */
    fun encode(text: String, maxTokens: Int = Int.MAX_VALUE): LongArray {
        require(maxTokens >= 2) { "maxTokens must leave room for <s> and </s>" }
        val body = ArrayList<Int>()
        for (word in words(text)) {
            unigram.encodeWord(WORD_PREFIX + word, body)
            if (body.size >= maxTokens - 2) break
        }
        val kept = minOf(body.size, maxTokens - 2)
        val out = LongArray(kept + 2)
        out[0] = BOS_ID.toLong()
        for (i in 0 until kept) out[i + 1] = body[i].toLong()
        out[kept + 1] = EOS_ID.toLong()
        return out
    }

    companion object {
        const val BOS_ID = 0
        const val PAD_ID = 1
        const val EOS_ID = 2
        const val UNK_ID = 3

        /** fairseq places <s>, <pad>, </s>, <unk> first, so SentencePiece piece i becomes id i + 1. */
        private const val FAIRSEQ_OFFSET = 1
        private const val WORD_PREFIX = "▁"

        fun load(file: File): XlmRobertaTokenizer = XlmRobertaTokenizer(SentencePieceModel.parse(file.readBytes()))

        /** The normalized words the tokenizer sees (also used to explain behaviour in tests). */
        fun words(text: String): List<String> {
            val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
            val cleaned = buildString(nfkc.length) {
                for (ch in nfkc) {
                    when {
                        ch.isWhitespace() -> append(' ')
                        Character.getType(ch) == Character.CONTROL.toInt() -> Unit
                        else -> append(ch)
                    }
                }
            }
            return cleaned.split(' ').filter { it.isNotEmpty() }
        }
    }
}
