package tr.yasliasistan.speech.recognition

import java.util.Locale

/**
 * Decides whether recognized text can become a command (NFG-18): empty text, punctuation only,
 * or only hesitation sounds ("ııı", "eee", "hmm") never produce a command.
 */
object TranscriptFilter {

    private val turkish = Locale.forLanguageTag("tr-TR")
    private val whitespace = Regex("\\s+")

    // Hesitation sounds, any length: "ı", "ııı", "eee", "aaa", "hı", "hıı", "hm", "hmm", "mmm", "ah", "of", "öf".
    private val filler = Regex("^(?:ı+|i+|e+|a+|h+[ıi]+|h+m+|m+|a+h+|o+h+|ö+f+|o+f+)$")

    /** Returns the cleaned command text, or null if [raw] holds no usable words. */
    fun usableText(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.trim().replace(whitespace, " ")
        val words = cleaned.lowercase(turkish)
            .split(' ')
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        if (words.all { filler.matches(it) }) return null
        return cleaned
    }
}
