package tr.yasliasistan.core.asr

import tr.yasliasistan.core.text.TurkishText

/**
 * Rewrites Turkish number words (already ASCII-folded by [TurkishText]) as digits, so that
 * "yirmi beş" and "25" count as the same word in WER level [NormalizationLevel.TURKISH].
 * A run that cannot be one number is split: "sıfır beş beş beş" -> "0", "5", "5", "5".
 */
internal object TurkishNumbers {

    private val units = mapOf(
        "bir" to 1, "iki" to 2, "uc" to 3, "dort" to 4, "bes" to 5,
        "alti" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9,
    )
    private val tens = mapOf(
        "on" to 10, "yirmi" to 20, "otuz" to 30, "kirk" to 40, "elli" to 50,
        "altmis" to 60, "yetmis" to 70, "seksen" to 80, "doksan" to 90,
    )

    /**
     * Words whose case-suffixed form ("beşte", "ikiye") is still read as a number. Words that are
     * also common non-number words with a suffix (on/ona, bir, üç/ucu, altı/altında) are left out.
     */
    private val suffixable = setOf(
        "iki", "dort", "bes", "yedi", "sekiz", "dokuz",
        "yirmi", "otuz", "kirk", "elli", "altmis", "yetmis", "seksen", "doksan",
    )

    fun toDigits(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size)
        val acc = Accumulator(out)
        for (word in words) {
            val base = numberWord(word)
            if (base == null) {
                acc.flush()
                out += word
            } else {
                acc.add(base)
            }
        }
        acc.flush()
        return out
    }

    private fun numberWord(word: String): String? {
        if (word in units || word in tens || word in SCALES) return word
        return suffixable.firstOrNull { TurkishText.isStemWithSuffix(word, it) }
    }

    private class Accumulator(private val out: MutableList<String>) {
        private var total = 0L
        private var current = 0L
        private var active = false

        fun add(word: String) {
            when (word) {
                ZERO -> { flush(); out += "0" }
                HUNDRED -> hundred()
                THOUSAND -> scale(1_000L)
                MILLION -> scale(1_000_000L)
                else -> units[word]?.let(::unit) ?: tens.getValue(word).let(::ten)
            }
        }

        private fun unit(value: Int) {
            if (current % 10 != 0L) flush()
            current += value
            active = true
        }

        private fun ten(value: Int) {
            if (current % 100 != 0L) flush()
            current += value
            active = true
        }

        private fun hundred() {
            if (current >= 100) flush()
            val multiplier = if (current == 0L) 1 else current
            current = multiplier * 100
            active = true
        }

        private fun scale(factor: Long) {
            if (total % (factor * 1000) != 0L) flush()
            val multiplier = if (current == 0L) 1 else current
            total += multiplier * factor
            current = 0
            active = true
        }

        fun flush() {
            if (active) out += (total + current).toString()
            total = 0
            current = 0
            active = false
        }
    }

    private const val ZERO = "sifir"
    private const val HUNDRED = "yuz"
    private const val THOUSAND = "bin"
    private const val MILLION = "milyon"
    private val SCALES = setOf(ZERO, HUNDRED, THOUSAND, MILLION)
}
