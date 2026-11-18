package tr.yasliasistan.core.decision

import tr.yasliasistan.core.text.TurkishText

/** One spoken or typed command; [raw] may contain personal data and is never logged. */
class Command(val raw: String) {
    val normalized: String = TurkishText.normalize(raw)
    val tokens: List<String> = if (normalized.isEmpty()) emptyList() else normalized.split(' ')

    override fun toString() = "Command(<${raw.length} chars>)"
}

/**
 * The open question of a conversation (G-37). [answer] reads the user's reply; null means the reply
 * is not an answer but a new command. An answer never yields another question (FG-06: one question).
 */
fun interface PendingQuestion {
    fun answer(command: Command): Decision?
}

/** Result of the decision engine (PLAN G-10, G-37). */
sealed interface Decision {
    /**
     * Do [action]. [certain] is false when the match was only partial (e.g. "haritalar" for
     * "Google Haritalar"); then the user is asked even for a low-risk action (safe default).
     * [readback] / [announcement] are the sentences for actions other than [Action.OpenApp]; the
     * rule that decides fills them in.
     */
    data class Act(
        val action: Action,
        val certain: Boolean,
        val readback: String? = null,
        val announcement: String? = null,
    ) : Decision {
        init {
            require(action is Action.OpenApp || (readback != null && announcement != null)) {
                "readback and announcement are required for ${action::class.simpleName}"
            }
        }

        override fun toString() = "Act(action=$action, certain=$certain)"
    }

    /** Several actions fit; the user picks one by name (FG-101: ask only on real ambiguity). */
    data class Choose(val options: List<Action>) : Decision

    /**
     * One clarification question (FG-06, FG-100). [candidates] is how many answers the device data
     * left open (0 when a slot is simply missing); a "which one?" question with one candidate would
     * be an unnecessary question (FG-102).
     */
    data class Ask(val text: String, val pending: PendingQuestion, val candidates: Int) : Decision {
        override fun toString() = "Ask(candidates=$candidates)"
    }

    /** A final sentence without an action (e.g. the one question did not resolve it). */
    data class Say(val text: String) : Decision {
        override fun toString() = "Say(<${text.length} chars>)"
    }

    /** No layer could handle the command: "Bunu henüz yapamıyorum." */
    data object CannotDo : Decision
}
