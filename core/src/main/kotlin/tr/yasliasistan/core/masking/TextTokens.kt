package tr.yasliasistan.core.masking

import java.text.Normalizer
import java.util.Locale

/**
 * Per-character Turkish lower-casing and folding (docs/MASKELEME.md §3.3). Both keep the string
 * length, so an index in the folded form is the same index in the original text.
 */
internal object TurkishFold {
    val TURKISH: Locale = Locale.forLanguageTag("tr")

    fun lowerChar(c: Char): Char = if (c.code < TABLE_SIZE) LOWER[c.code] else computeLower(c)

    /** Lower case, then ş->s, ğ->g, ç->c, ö->o, ü->u, ı->i (and any other diacritic removed). */
    fun foldChar(c: Char): Char = if (c.code < TABLE_SIZE) FOLD[c.code] else computeFold(c)

    private fun computeLower(c: Char): Char {
        val lower = c.toString().lowercase(TURKISH)
        return if (lower.length == 1) lower[0] else lower.first()
    }

    private fun computeFold(c: Char): Char {
        val lower = computeLower(c)
        if (lower == 'ı') return 'i'
        if (lower.code < 128) return lower
        val decomposed = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)
        return decomposed[0]
    }

    // Lookup tables for Latin letters (Turkish included): masking runs per word on every screen.
    private const val TABLE_SIZE = 0x250
    private val LOWER = CharArray(TABLE_SIZE) { computeLower(it.toChar()) }
    private val FOLD = CharArray(TABLE_SIZE) { computeFold(it.toChar()) }

    fun lower(s: String): String = buildString(s.length) { s.forEach { append(lowerChar(it)) } }
    fun fold(s: String): String = buildString(s.length) { s.forEach { append(foldChar(it)) } }

    /** "gülten test" -> "Gülten Test" (Turkish rules: i -> İ). */
    fun titleCase(s: String): String = s.split(' ').joinToString(" ") { word ->
        val lower = lower(word)
        if (lower.isEmpty()) lower else lower.substring(0, 1).uppercase(TURKISH) + lower.substring(1)
    }
}

/**
 * One whitespace-separated word of the preprocessed text. [coreStart]..[coreEnd] drops leading
 * quotes/brackets and trailing sentence punctuation; [baseEnd] stops before an apostrophe suffix
 * ("Osman'a" -> base "Osman", suffix "a").
 */
internal class Token(
    val index: Int,
    val start: Int,
    val end: Int,
    val coreStart: Int,
    val coreEnd: Int,
    val baseEnd: Int,
    private val text: String,
) {
    val raw: String get() = text.substring(start, end)
    val core: String get() = text.substring(coreStart, coreEnd)
    val base: String get() = text.substring(coreStart, baseEnd)

    /** Ending written after the apostrophe, or "" when there is none. */
    val apostropheSuffix: String get() = if (baseEnd < coreEnd) text.substring(baseEnd + 1, coreEnd) else ""

    val fold: String = TurkishFold.fold(text.substring(coreStart, baseEnd))
    val lower: String = TurkishFold.lower(text.substring(coreStart, baseEnd))

    /** Folded core with the apostrophe removed: "Osman'a" -> "osmana". */
    val foldJoined: String = TurkishFold.fold(text.substring(coreStart, coreEnd).replace("'", ""))

    val hasTrailingPunctuation: Boolean get() = coreEnd < end && text[coreEnd] != ')'
    val endsSentence: Boolean get() = text.substring(coreEnd, end).any { it == '.' || it == '!' || it == '?' }
    val hasLetter: Boolean get() = (coreStart until coreEnd).any { text[it].isLetter() }
    val startsUpper: Boolean get() = coreStart < coreEnd && text[coreStart].isUpperCase()
}

/** Preprocessed text and its tokens (docs/MASKELEME.md §3). */
internal class TokenizedText(val text: String) {
    val tokens: List<Token>

    init {
        val list = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            if (text[i] == ' ') { i++; continue }
            var j = i
            while (j < text.length && text[j] != ' ') j++
            list += makeToken(list.size, i, j)
            i = j
        }
        tokens = list
    }

    private fun makeToken(index: Int, start: Int, end: Int): Token {
        var cs = start
        while (cs < end && text[cs] in LEADING) cs++
        var ce = end
        while (ce > cs && text[ce - 1] in TRAILING) ce--
        var be = ce
        val apostrophe = text.indexOf('\'', cs)
        if (apostrophe in (cs + 1) until ce - 1 && text[apostrophe + 1].isLetter() && text[apostrophe - 1].isLetterOrDigit()) {
            be = apostrophe
        }
        return Token(index, start, end, cs, ce, be, text)
    }

    /** Index of the token that contains character [pos], or -1. */
    fun tokenAt(pos: Int): Int = tokens.indexOfFirst { pos >= it.start && pos < it.end }

    companion object {
        private const val LEADING = "\"“‘(«"
        private const val TRAILING = ".,!?;:\"”’»)"

        private val apostrophes = Regex("[’‘`´]")
        private val spaces = Regex("\\s+")

        /** NFC, single spaces, one apostrophe form, and [ ] -> ( ) so input cannot fake a placeholder. */
        fun preprocess(input: String): String =
            Normalizer.normalize(input, Normalizer.Form.NFC)
                .replace('[', '(').replace(']', ')')
                .replace(apostrophes, "'")
                .replace(spaces, " ")
                .trim()
    }
}
