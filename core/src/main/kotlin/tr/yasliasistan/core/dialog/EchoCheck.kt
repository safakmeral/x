package tr.yasliasistan.core.dialog

import tr.yasliasistan.core.text.TurkishText

/**
 * Barge-in guard (PLAN G-16, FG-14): while the assistant speaks, the microphone may hear the
 * assistant's own voice through the loudspeaker. This decides whether recognized text is the user
 * talking or that echo, by comparing it with the sentence being spoken.
 *
 * Rules (pure, no time, unit-tested):
 * - A short reply word ("evet", "hayır", "dur", ...) that is NOT in the spoken sentence is the user.
 * - Two or more words of which more than [ECHO_SHARE] also occur in the spoken sentence are echo.
 * - A single word that occurs in the spoken sentence is echo (an echo must never confirm anything:
 *   "Evet mi, hayır mı?" heard back as "evet" is dropped; the safe side, NFG-07).
 * - Otherwise a single unknown word is [Verdict.UNSURE]: not enough to cut the speech, but kept as
 *   the user's words when it is the final result.
 * Never logs or stores the texts.
 */
object EchoCheck {

    enum class Verdict { USER, ECHO, UNSURE }

    /** Share of heard words found in the spoken sentence above which the text counts as echo. */
    const val ECHO_SHARE = 0.5

    /** A leading run of echoed words this long is removed from a final transcript. */
    private const val MIN_ECHO_RUN = 2

    // ASCII-folded, as produced by TurkishText.
    private val replyWords = setOf(
        "evet", "hayir", "dur", "durdur", "sus", "bekle", "tamam", "olur", "peki", "vazgec", "vazgectim",
        "iptal", "yok", "istemiyorum", "degil", "olmaz", "onayliyorum", "onayla", "yanlis",
        // G-17 fixed command words
        "yeter", "neden", "niye", "nicin", "tekrar", "tekrarla", "bastan", "hatirladim", "yavas", "anlamadim",
    )

    /** Live (partial) text while speaking: only [Verdict.USER] cuts the speech. */
    fun classify(heard: String, spoken: String): Verdict {
        val words = TurkishText.tokens(heard)
        if (words.isEmpty()) return Verdict.UNSURE
        val reference = TurkishText.tokens(spoken)
        val foreign = words.filter { word -> reference.none { sameWord(word, it) } }
        return when {
            foreign.any { it in replyWords } -> Verdict.USER
            words.size == 1 -> if (foreign.isEmpty()) Verdict.ECHO else Verdict.UNSURE
            (words.size - foreign.size).toDouble() / words.size > ECHO_SHARE -> Verdict.ECHO
            else -> Verdict.USER
        }
    }

    /**
     * Final transcript of a turn that ran while the assistant spoke: returns the user's part (a leading
     * echoed run removed), or null when the text is only echo and must not reach the dialog.
     */
    fun userPart(heard: String, spoken: String): String? {
        val rest = stripLeadingEcho(heard, spoken).trim()
        if (rest.isEmpty()) return null
        return rest.takeIf { classify(it, spoken) != Verdict.ECHO }
    }

    /** Removes a leading run of at least [MIN_ECHO_RUN] words that also occur in [spoken]. */
    fun stripLeadingEcho(heard: String, spoken: String): String {
        val reference = TurkishText.tokens(spoken)
        val raw = heard.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var run = 0
        while (run < raw.size && isEchoWord(raw[run], reference)) run++
        return if (run >= MIN_ECHO_RUN) raw.drop(run).joinToString(" ") else heard.trim()
    }

    private fun isEchoWord(rawWord: String, reference: List<String>): Boolean {
        val parts = TurkishText.tokens(rawWord)
        return parts.all { part -> reference.any { sameWord(part, it) } } // punctuation-only counts as echo
    }

    /** Same word, allowing a different ending the recognizer may produce ("haritalar" / "haritalari"). */
    internal fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val common = a.commonPrefixWith(b).length
        return common >= MIN_STEM && common >= STEM_SHARE * maxOf(a.length, b.length)
    }

    private const val MIN_STEM = 4
    private const val STEM_SHARE = 0.6
}
