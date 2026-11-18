package tr.yasliasistan.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Turkish text normalization for matching speech-recognizer or typed output (PLAN G-10).
 *
 * The normalized form is lower case (Turkish rules: I -> ı, İ -> i), ASCII-folded (ı -> i, ş -> s, ...),
 * without punctuation, with single spaces. A suffix written after an apostrophe ("WhatsApp'ı")
 * is dropped, because Turkish spelling puts only case endings there.
 */
object TurkishText {

    private val turkish: Locale = Locale.forLanguageTag("tr")
    private val apostropheSuffix = Regex("(?<=[\\p{L}\\p{N}])['’‘`´]\\p{L}*")
    private val combiningMarks = Regex("\\p{M}+")
    private val nonWord = Regex("[^a-z0-9]+")

    /**
     * Case endings accepted after a name without an apostrophe (ASCII-folded), e.g. "ayarlar|ı",
     * "kamera|yı", "hava durumu|nu". Plural or possessive forms are deliberately not included,
     * so "saatler" does not match "Saat".
     */
    private val caseSuffixes = setOf(
        "", "i", "u", "yi", "yu", "ni", "nu",
        "a", "e", "ya", "ye", "na", "ne",
        "da", "de", "ta", "te", "nda", "nde",
        "dan", "den", "tan", "ten", "ndan", "nden",
        "in", "un", "nin", "nun",
        "la", "le", "yla", "yle",
    )

    fun normalize(text: String): String {
        val lower = text.lowercase(turkish).replace(apostropheSuffix, "")
        val folded = Normalizer.normalize(lower.replace('ı', 'i'), Normalizer.Form.NFD).replace(combiningMarks, "")
        return folded.replace(nonWord, " ").trim()
    }

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /** True when [word] is [stem] followed by nothing or a Turkish case ending; both normalized. */
    fun isStemWithSuffix(word: String, stem: String): Boolean =
        stem.isNotEmpty() && word.startsWith(stem) && word.substring(stem.length) in caseSuffixes
}
