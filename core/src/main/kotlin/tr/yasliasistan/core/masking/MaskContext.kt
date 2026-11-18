package tr.yasliasistan.core.masking

/** A number word inside one token: its value, where the stem ends, and a fused ending ("beşe" -> "e"). */
internal data class NumberWordHit(val value: Long, val stemEnd: Int, val fusedSuffix: String?)

/** Shared view of one text under masking: tokens, dictionary and accepted spans. */
internal class MaskContext(val tokenized: TokenizedText, val dict: MaskingDictionary) {
    val text: String get() = tokenized.text
    val tokens: List<Token> get() = tokenized.tokens
    val accepted = ArrayList<Span>()

    fun isCovered(t: Token) = accepted.any { it.overlaps(t.coreStart, t.coreEnd) }

    /**
     * Builds a span whose value ends at [valueEnd]. A written ending right after the value
     * ("0599 000 00 15'i", "Osman'a") or a [fusedSuffix] ("beşe") is replaced by its case class.
     */
    fun span(start: Int, valueEnd: Int, category: MaskCategory, value: String, key: String,
             fusedSuffix: String? = null, valueHasSuffix: Boolean = false): Span {
        if (fusedSuffix != null) {
            val cls = if (valueHasSuffix) "" else dict.caseClassOf(fusedSuffix) ?: ""
            return Span(start, valueEnd + fusedSuffix.length, category, value, key, cls, valueHasSuffix)
        }
        val tok = tokens.getOrNull(tokenized.tokenAt(valueEnd - 1))
        if (tok != null && valueEnd == tok.baseEnd && tok.baseEnd < tok.coreEnd) {
            val cls = dict.caseClassOf(tok.apostropheSuffix) ?: ""
            return Span(start, tok.coreEnd, category, value, key, cls, valueHasSuffix)
        }
        return Span(start, valueEnd, category, value, key, "", valueHasSuffix)
    }

    /**
     * "Bilinen sözcük" of §3.4. Tokens without letters (digits, punctuation) count as known.
     * [allowPrefix] = false skips the dictionary-prefix rule (used before honorifics, see SAPMALAR).
     */
    fun isKnown(t: Token, allowPrefix: Boolean = true): Boolean {
        val w = t.fold
        if (w.isEmpty() || !w[0].isLetter()) return true
        return dict.isCommandWord(t, allowPrefix) || dict.isKinshipOrHonorific(t) || numberWord(t) != null
    }

    /** Unknown word not yet covered by a span: a candidate for name / free-text rules. */
    fun isFreeUnknown(t: Token, allowPrefix: Boolean = true) = t.hasLetter && !isCovered(t) && !isKnown(t, allowPrefix)

    fun numberWord(t: Token): NumberWordHit? {
        val w = t.fold
        dict.numberWords[w]?.let { return NumberWordHit(it, t.baseEnd, null) }
        if (t.baseEnd < t.coreEnd) return null
        for ((word, value) in dict.numberWords) {
            // "ona"/"onu" are pronouns, not "on" + ending.
            if (word == "on" || !w.startsWith(word) || w.length == word.length) continue
            val rest = w.substring(word.length)
            if (rest in dict.suffixEndings) {
                val stemEnd = t.coreStart + word.length
                return NumberWordHit(value, stemEnd, text.substring(stemEnd, t.coreEnd))
            }
        }
        return null
    }

    /** Original text of tokens [from]..[to] (inclusive), from the first core to [endPos]. */
    fun surface(from: Int, endPos: Int) = text.substring(tokens[from].coreStart, endPos)

    /** Accepts candidates by "longest span wins, then category priority" (§4.9). */
    fun accept(candidates: List<Span>) {
        val ordered = (accepted + candidates).sortedWith(
            compareByDescending<Span> { it.length }.thenBy { it.category.tiePriority }.thenBy { it.start },
        )
        val chosen = ArrayList<Span>()
        for (s in ordered) if (chosen.none { it.overlaps(s) }) chosen += s
        accepted.clear()
        accepted += chosen.sortedBy { it.start }
    }
}
