package tr.yasliasistan.core.dialog

/**
 * Inputs to [DialogMachine]. Results carry the request id they belong to, so late results
 * from a stopped or older request are ignored. toString never prints user text (KVKK).
 */
sealed interface DialogEvent {
    /** The floating bubble was tapped: the only way a session (and the microphone) starts. */
    data object BubbleTapped : DialogEvent

    /** The on-screen "Dur" button (FG-11). Works in every state. */
    data object StopPressed : DialogEvent

    /**
     * Transcript of one listening turn. [partialOnly] is true when the recognizer gave no final result
     * and only its last partial text is left: such text never acts or confirms without a question
     * (NFG-18; UD-1 Ö-1).
     */
    data class Heard(val text: String, val partialOnly: Boolean = false) : DialogEvent {
        override fun toString() = "Heard(<${text.length} chars>, partialOnly=$partialOnly)"
    }

    /**
     * Barge-in (G-16, FG-14): the user started talking while a question was being spoken, or tapped
     * the bubble then. The speech stops; the answer itself comes later as [Heard].
     */
    data object UserInterrupted : DialogEvent

    /** The recognizer ended without a usable result (silence, noise, time limit). */
    data object ListenFailed : DialogEvent

    /**
     * A command typed instead of spoken (FG-04). From Idle (or while the closing sentence plays)
     * it starts a session without opening the microphone; during a session it counts as [Heard].
     */
    data class TextEntered(val text: String) : DialogEvent {
        override fun toString() = "TextEntered(<${text.length} chars>)"
    }

    /**
     * Listening is impossible right now (no permission, no Turkish pack, microphone busy).
     * While listening, the session ends and [reason] is said instead of "I did not hear you".
     */
    data class CannotListen(val reason: String) : DialogEvent {
        override fun toString() = "CannotListen"
    }

    /**
     * The decision engine understood the command; [readback] is the one-sentence question (FG-07).
     * [lowRisk]: the action is Low risk, so a soft assent ("olur", "tamam") also confirms it;
     * Medium/High risk needs an explicit "evet" (V-12; UD-1 Ö-2).
     */
    data class Understood(val requestId: Int, val readback: String, val lowRisk: Boolean = false) : DialogEvent {
        override fun toString() = "Understood(requestId=$requestId)"
    }

    /**
     * The decision engine understood a certain, Low-risk command (FG-71, Table 3: tell and do):
     * [announcement] is spoken ("Ayarlar uygulamasını açıyorum.") and the action runs at once,
     * without a yes/no question. Medium and High risk always use [Understood].
     */
    data class Proceeding(val requestId: Int, val announcement: String) : DialogEvent {
        override fun toString() = "Proceeding(requestId=$requestId)"
    }

    /**
     * The decision engine answers without an action: either a question that needs an answer
     * ([expectsAnswer], e.g. choosing between matches) or a final sentence ("Bunu henüz yapamıyorum").
     */
    data class Replied(val requestId: Int, val text: String, val expectsAnswer: Boolean) : DialogEvent {
        override fun toString() = "Replied(requestId=$requestId, expectsAnswer=$expectsAnswer)"
    }

    /** On-screen yes/no buttons of the confirmation question. */
    data class ConfirmTapped(val yes: Boolean) : DialogEvent

    /**
     * The typing screen opened (FG-04). While the user types, the confirmation timeout is paused,
     * because typing is not silence (UD-1 Ö-4). Typing ends with [TextEntered] or [StopPressed].
     */
    data object TypingStarted : DialogEvent

    data class ExecutionSucceeded(val requestId: Int, val doneText: String?) : DialogEvent {
        override fun toString() = "ExecutionSucceeded(requestId=$requestId)"
    }

    data class ExecutionFailed(val requestId: Int, val reasonText: String?) : DialogEvent {
        override fun toString() = "ExecutionFailed(requestId=$requestId)"
    }

    /**
     * Answer to [DialogEffect.Explain] (G-17 "neden", G-39 text); [text] null when there is none.
     * A [token] the machine no longer waits for is ignored.
     */
    data class Explained(val token: Int, val text: String?) : DialogEvent {
        override fun toString() = "Explained(token=$token, text=${text != null})"
    }

    /** Text-to-speech finished (or was interrupted) for the utterance with this id. */
    data class SpeechFinished(val utteranceId: Int) : DialogEvent

    /** A timer requested by [DialogEffect.ScheduleTimer] elapsed. */
    data class TimerFired(val token: Int) : DialogEvent
}
