package tr.yasliasistan.core.dialog

/**
 * Fixed short sentences of the dialog (NFG-05: short, one question, no percentages).
 * `:core` has no Android resources; the app maps each value to a Turkish string in strings.xml.
 * Intended wording is given in the comment of each value.
 */
enum class Phrase {
    /** "Bakıyorum…" (FG-17, wait longer than 5 s). */
    PROGRESS,

    /** "Tamam, bitti." (FG-16, when the executor gives no specific sentence). */
    DONE,

    /** "Tamam, yapmıyorum." (answer "no" or 10 s silence). */
    CANCELLED,

    /** "Durdum." (Stop pressed). */
    STOPPED,

    /** "Evet mi, hayır mı?" (unclear answer to a confirmation, asked once). */
    CONFIRM_AGAIN,

    /** "Sizi duyamadım. Tekrar denemek için baloncuğa dokunun." */
    NOT_HEARD,

    /** "Bunu yapamadım." (execution failed without a specific sentence). */
    FAILED,

    /** "Tamam, kapatıyorum." ("tamam, hatırladım", FG-13). */
    CLOSING,
}

/** Something the assistant says; every spoken output is also shown as text (G-08). */
sealed interface Utterance {
    /** Sentence produced elsewhere (decision engine, executor). May contain personal data. */
    class Text(val text: String) : Utterance {
        override fun equals(other: Any?) = other is Text && other.text == text
        override fun hashCode() = text.hashCode()
        override fun toString() = "Text(<${text.length} chars>)"
    }

    data class Template(val phrase: Phrase) : Utterance
}
