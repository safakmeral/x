package tr.yasliasistan.core.asr

/**
 * Edit counts of one alignment (Levenshtein, equal costs). WER = (S + D + I) / N, where N is the
 * number of reference units. Counts add up over clips, so a corpus WER is sum(errors) / sum(N).
 */
data class EditCounts(
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val referenceLength: Int,
) {
    val errors: Int get() = substitutions + deletions + insertions

    /** Error rate, or null when the reference is empty (silent clips are judged by hallucination). */
    val rate: Double? get() = if (referenceLength == 0) null else errors.toDouble() / referenceLength

    operator fun plus(other: EditCounts) = EditCounts(
        substitutions + other.substitutions,
        deletions + other.deletions,
        insertions + other.insertions,
        referenceLength + other.referenceLength,
    )

    companion object {
        val ZERO = EditCounts(0, 0, 0, 0)
    }
}

object ErrorRate {

    /** Word-level counts after normalizing both texts with [level]. */
    fun words(reference: String, hypothesis: String, level: NormalizationLevel): EditCounts =
        align(TranscriptNormalizer.tokens(reference, level), TranscriptNormalizer.tokens(hypothesis, level))

    /** Character-level counts (spaces included as one character) after normalizing with [level]. */
    fun chars(reference: String, hypothesis: String, level: NormalizationLevel): EditCounts =
        align(
            TranscriptNormalizer.text(reference, level).map { it.toString() },
            TranscriptNormalizer.text(hypothesis, level).map { it.toString() },
        )

    /** Minimum-edit alignment; among equal-cost paths substitutions are preferred. */
    fun <T> align(reference: List<T>, hypothesis: List<T>): EditCounts {
        val n = reference.size
        val m = hypothesis.size
        // cost[i][j] for reference[0, i) vs hypothesis[0, j); ops kept to count S/D/I.
        val cost = Array(n + 1) { IntArray(m + 1) }
        val op = Array(n + 1) { ByteArray(m + 1) }
        for (i in 1..n) { cost[i][0] = i; op[i][0] = DEL }
        for (j in 1..m) { cost[0][j] = j; op[0][j] = INS }
        for (i in 1..n) {
            for (j in 1..m) {
                val same = reference[i - 1] == hypothesis[j - 1]
                val diag = cost[i - 1][j - 1] + if (same) 0 else 1
                val del = cost[i - 1][j] + 1
                val ins = cost[i][j - 1] + 1
                when {
                    diag <= del && diag <= ins -> { cost[i][j] = diag; op[i][j] = if (same) OK else SUB }
                    del <= ins -> { cost[i][j] = del; op[i][j] = DEL }
                    else -> { cost[i][j] = ins; op[i][j] = INS }
                }
            }
        }
        var s = 0
        var d = 0
        var ins = 0
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when (op[i][j]) {
                OK -> { i--; j-- }
                SUB -> { s++; i--; j-- }
                DEL -> { d++; i-- }
                else -> { ins++; j-- }
            }
        }
        return EditCounts(s, d, ins, n)
    }

    private const val OK: Byte = 0
    private const val SUB: Byte = 1
    private const val DEL: Byte = 2
    private const val INS: Byte = 3
}
