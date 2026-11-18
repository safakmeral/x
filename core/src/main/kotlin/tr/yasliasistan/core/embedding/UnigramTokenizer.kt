package tr.yasliasistan.core.embedding

/**
 * SentencePiece Unigram segmentation (Viterbi best path), matching Hugging Face `tokenizers`
 * Unigram: the most probable split by piece scores; a character no piece covers becomes the
 * unknown token (score = lowest piece score - 10) and adjacent unknowns are fused into one.
 *
 * @param pieces piece texts that may appear in normal text
 * @param scores log-probability of each piece
 * @param outputIds model input id of each piece
 */
class UnigramTokenizer(
    pieces: List<String>,
    private val scores: FloatArray,
    private val outputIds: IntArray,
    private val unkId: Int,
) {
    /** Piece text -> index into [scores] / [outputIds]. One map keeps the 250k-piece vocab compact. */
    private val index = HashMap<String, Int>(pieces.size * 4 / 3 + 1)
    private val unkScore: Double
    private val maxPieceCodePoints: Int

    init {
        require(pieces.size == scores.size && pieces.size == outputIds.size) { "vocab arrays differ in size" }
        var maxLen = 1
        pieces.forEachIndexed { i, text ->
            index[text] = i
            maxLen = maxOf(maxLen, text.codePointCount(0, text.length))
        }
        unkScore = (scores.minOrNull()?.toDouble() ?: 0.0) - UNK_PENALTY
        maxPieceCodePoints = maxLen
    }

    /** Segments one pre-tokenized word (no spaces) into output ids, appended to [out]. */
    fun encodeWord(word: String, out: MutableList<Int>) {
        if (word.isEmpty()) return
        val offsets = codePointOffsets(word)
        val n = offsets.size - 1
        val best = DoubleArray(n + 1)
        val from = IntArray(n + 1) { -1 }
        val pieceId = IntArray(n + 1) { unkId }
        for (start in 0 until n) {
            val base = best[start]
            var hasSingle = false
            for (len in 1..minOf(maxPieceCodePoints, n - start)) {
                val i = index[word.substring(offsets[start], offsets[start + len])] ?: continue
                relax(best, from, pieceId, start, start + len, base + scores[i], outputIds[i])
                if (len == 1) hasSingle = true
            }
            if (!hasSingle) relax(best, from, pieceId, start, start + 1, base + unkScore, unkId)
        }
        val path = ArrayList<Int>()
        var pos = n
        while (pos > 0) {
            path += pieceId[pos]
            pos = from[pos]
        }
        var previousUnk = false
        for (i in path.indices.reversed()) {
            val isUnk = path[i] == unkId
            if (!(isUnk && previousUnk)) out += path[i]
            previousUnk = isUnk
        }
    }

    private fun relax(best: DoubleArray, from: IntArray, pieceId: IntArray, start: Int, end: Int, score: Double, id: Int) {
        if (from[end] == -1 || score > best[end]) {
            best[end] = score
            from[end] = start
            pieceId[end] = id
        }
    }

    private fun codePointOffsets(s: String): IntArray {
        val count = s.codePointCount(0, s.length)
        val offsets = IntArray(count + 1)
        var idx = 0
        for (i in 0 until count) {
            offsets[i] = idx
            idx = s.offsetByCodePoints(idx, 1)
        }
        offsets[count] = s.length
        return offsets
    }

    private companion object {
        /** Same penalty as SentencePiece / tokenizers (kUnkPenalty). */
        const val UNK_PENALTY = 10.0
    }
}
