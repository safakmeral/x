package tr.yasliasistan.core.dialog

import java.util.Locale

/**
 * Classifies a spoken or typed answer to a yes/no confirmation question (FG-07, V-12; UD-1 Ö-2).
 *
 * - [Answer.NO]: any "no" or negation word, wherever it is ("tamam ama hayır", "evet değil"),
 *   because the safe choice is not to act (NFG-07).
 * - [Answer.YES]: an explicit V-12 word ("evet", "onaylıyorum", "onayla") and no "but".
 * - [Answer.SOFT_YES]: only natural short assents ("tamam", "olur", "peki"); the dialog accepts them
 *   for Low risk only (docs/SAPMALAR.md), so Medium/High risk always needs an explicit "evet".
 * - [Answer.UNCLEAR]: everything else, including a yes with "ama" or with extra words ("peki nasıl").
 */
object ConfirmationParser {

    enum class Answer { YES, SOFT_YES, NO, UNCLEAR }

    private val turkish: Locale = Locale.forLanguageTag("tr")

    // All sets are stored in ASCII-folded form, so both "hayır" and the recognizer's "hayir" match.
    private val yesWords = setOf("evet", "onayliyorum", "onayla")
    private val softYesWords = setOf("tamam", "olur", "peki", "tabii", "tabi")

    /** Polite words that may accompany a soft yes without making it unclear. */
    private val softFillers = setOf("lutfen", "efendim")
    private val contrastWords = setOf("ama", "fakat", "ancak", "lakin")
    private val noWords = setOf(
        "hayir", "vazgec", "vazgectim", "vazgecin", "istemiyorum", "istemem", "istemez", "iptal",
        "yapma", "yapmayin", "yapmasin", "yapmayalim", "acma", "acmayin", "yok", "dur", "durdur",
        "degil", "olmaz", "yanlis",
    )

    fun parse(text: String): Answer {
        val words = text.lowercase(turkish)
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
            .map(::foldAscii)
        return when {
            words.isEmpty() -> Answer.UNCLEAR
            words.any { it in noWords } -> Answer.NO
            words.any { it in contrastWords } -> Answer.UNCLEAR
            words.any { it in yesWords } -> Answer.YES
            words.any { it in softYesWords } && words.all { it in softYesWords || it in softFillers } -> Answer.SOFT_YES
            else -> Answer.UNCLEAR
        }
    }

    private fun foldAscii(word: String): String = buildString(word.length) {
        for (c in word) {
            append(
                when (c) {
                    'ı' -> 'i'; 'ş' -> 's'; 'ç' -> 'c'; 'ğ' -> 'g'; 'ö' -> 'o'; 'ü' -> 'u'
                    'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                    else -> c
                },
            )
        }
    }
}
