package tr.yasliasistan.core.asr

import tr.yasliasistan.core.text.TurkishText
import java.util.Locale

/**
 * The three normalization levels used for WER/CER (PLAN G-13, DEG-14 "2-3 normalizasyon düzeyi").
 * The same level is always applied to both the reference and the recognizer output.
 */
enum class NormalizationLevel(val label: String) {
    /** Text as written: split on whitespace only (case and punctuation count as errors). */
    RAW("ham"),

    /** Turkish lower case, punctuation removed, apostrophes joined ("Ayşe'yi" = "ayşeyi"). */
    BASIC("noktalama+harf"),

    /**
     * BASIC plus: Turkish letters folded to ASCII (ş = s), number words written as digits
     * ("beş" = "5"), suffix after a digit dropped ("5'te" = "5"), neighbouring digit groups joined
     * ("0555 000 00 01" = "05550000001"). Lenient on purpose: it measures what a command needs.
     */
    TURKISH("turkce"),
}

object TranscriptNormalizer {

    private val turkish: Locale = Locale.forLanguageTag("tr")
    private val apostrophes = Regex("['’‘`´]")
    private val digitSuffix = Regex("(?<=\\d)['’‘`´]\\p{L}+")
    private val thousandsDot = Regex("(?<=\\d)\\.(?=\\d{3}(?!\\d))")
    private val punctuation = Regex("[^\\p{L}\\p{N}\\s]+")
    private val digits = Regex("\\d+")

    fun tokens(text: String, level: NormalizationLevel): List<String> = when (level) {
        NormalizationLevel.RAW -> split(text)
        NormalizationLevel.BASIC -> split(basic(text))
        NormalizationLevel.TURKISH -> turkishTokens(text)
    }

    /** Normalized text joined by single spaces (used for CER). */
    fun text(text: String, level: NormalizationLevel): String = tokens(text, level).joinToString(" ")

    private fun basic(text: String): String =
        text.lowercase(turkish).replace(apostrophes, "").replace(punctuation, " ")

    private fun turkishTokens(text: String): List<String> {
        val prepared = text.replace(thousandsDot, "").replace(digitSuffix, "").replace(apostrophes, "")
        val words = TurkishText.tokens(prepared)
        return joinDigitGroups(TurkishNumbers.toDigits(words))
    }

    private fun joinDigitGroups(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size)
        for (w in words) {
            if (digits.matches(w) && out.isNotEmpty() && digits.matches(out.last())) {
                out[out.size - 1] = out.last() + w
            } else {
                out += w
            }
        }
        return out
    }

    private fun split(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
