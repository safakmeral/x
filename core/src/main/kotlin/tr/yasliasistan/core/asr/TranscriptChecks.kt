package tr.yasliasistan.core.asr

/**
 * Whisper-specific failure checks (DEG-14, MAKALE-20): text produced from silence
 * (hallucination) and the same words repeated over and over (loop).
 */
object TranscriptChecks {

    /** Longest repeated block that is checked (words). */
    const val MAX_LOOP_BLOCK = 6

    /** A block repeated this many times back to back counts as a loop. */
    const val LOOP_REPEATS = 3

    /** True when the clip has no speech (empty reference) but the recognizer returned words. */
    fun isHallucination(reference: String, hypothesis: String): Boolean =
        words(reference).isEmpty() && words(hypothesis).isNotEmpty()

    /**
     * True when the output repeats a block of 1..[MAX_LOOP_BLOCK] words at least [LOOP_REPEATS]
     * times in a row and the reference itself does not ("evet evet evet" said on purpose).
     */
    fun isLoop(reference: String, hypothesis: String): Boolean =
        maxRepeats(words(hypothesis)) >= LOOP_REPEATS && maxRepeats(words(reference)) < LOOP_REPEATS

    /** Largest number of back-to-back copies of any block of 1..[MAX_LOOP_BLOCK] words. */
    fun maxRepeats(words: List<String>): Int {
        var best = if (words.isEmpty()) 0 else 1
        for (size in 1..MAX_LOOP_BLOCK) {
            for (start in 0..words.size - 2 * size) {
                var copies = 1
                var next = start + size
                while (next + size <= words.size && same(words, start, next, size)) {
                    copies++
                    next += size
                }
                if (copies > best) best = copies
            }
        }
        return best
    }

    private fun same(words: List<String>, a: Int, b: Int, size: Int): Boolean =
        (0 until size).all { words[a + it] == words[b + it] }

    private fun words(text: String) = TranscriptNormalizer.tokens(text, NormalizationLevel.BASIC)
}
