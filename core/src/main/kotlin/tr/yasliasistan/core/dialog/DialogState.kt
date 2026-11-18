package tr.yasliasistan.core.dialog

/** States of one spoken interaction (PLAN G-09). */
sealed interface DialogState {
    /** True while a bubble-started session is active ("Dur" visible, microphone allowed). */
    val sessionOpen: Boolean get() = true

    data object Idle : DialogState {
        override val sessionOpen get() = false
    }

    data object Listening : DialogState

    /** Waiting for the decision engine. */
    data class Processing(val requestId: Int) : DialogState

    /** Speaking a decision-engine reply; listens again afterwards. */
    data class Replying(val requestId: Int, val utteranceId: Int) : DialogState

    /**
     * Speaking the one-sentence readback question. An answer is already accepted here.
     * [lowRisk]: soft assents ("olur", "tamam") also confirm (see [DialogEvent.Understood]).
     */
    data class ReadingBack(
        val requestId: Int,
        val utteranceId: Int,
        val unclearAnswers: Int,
        val lowRisk: Boolean = false,
    ) : DialogState

    /** Listening for yes/no; silence until the timeout means "no" (paused while the user types). */
    data class AwaitingConfirmation(val requestId: Int, val unclearAnswers: Int, val lowRisk: Boolean = false) : DialogState

    /** The confirmed action is running. */
    data class Working(val requestId: Int) : DialogState

    /** Speaking the closing sentence (done, cancelled, failed); session already ended. */
    data class Done(val utteranceId: Int) : DialogState {
        override val sessionOpen get() = false
    }
}
