package tr.yasliasistan.core.dialog

/** Outputs of [DialogMachine]; the host (app) performs them. toString never prints user text. */
sealed interface DialogEffect {
    /** A bubble-started session begins: show "Dur", microphone may be used until [SessionEnded]. */
    data object SessionStarted : DialogEffect

    /** Session over: hide "Dur" and release the microphone (FG-02: no listening outside a session). */
    data object SessionEnded : DialogEffect

    data object StartListening : DialogEffect
    data object StopListening : DialogEffect

    /**
     * Barge-in (G-16, FG-14): listen while utterance [utteranceId] (a question) is spoken, inside the
     * running session only (FG-02). If the user talks, the host sends [DialogEvent.UserInterrupted].
     * Optional for the host: when it cannot listen during speech, the dialog works as before.
     */
    data class ListenWhileSpeaking(val utteranceId: Int) : DialogEffect

    /**
     * G-17: listen for fixed commands ("dur", "tekrar söyle", ...) while the assistant works or speaks
     * an information sentence, inside the running session only (FG-02; mic indicator visible).
     * Only a recognized command comes back as [DialogEvent.Heard]; anything else as
     * [DialogEvent.ListenFailed], after which the machine asks again. Optional for the host.
     */
    data object ListenForCommands : DialogEffect

    /** G-17 "neden": get the G-39 explanation of the last answer; reply with [DialogEvent.Explained]. */
    data class Explain(val token: Int) : DialogEffect

    /** G-17 "yavaş konuş": switch the speech rate to slow (G-08 setting) before the repeated sentence. */
    data object SlowDown : DialogEffect

    /**
     * G-17 "baştan anlat" while [requestId] runs: a step-by-step guidance or execution starts again
     * from its first step (FG-12). Hosts without step-by-step work ignore it.
     */
    data class RestartFromFirstStep(val requestId: Int) : DialogEffect

    /**
     * Say [utterance]; report [DialogEvent.SpeechFinished] with [utteranceId] when done.
     * Utterances are queued in order (a low-risk announcement may still be playing when the
     * completion sentence arrives); only [CancelSpeech] cuts speech short.
     */
    data class Speak(val utteranceId: Int, val utterance: Utterance) : DialogEffect

    data object CancelSpeech : DialogEffect

    /**
     * Ask the decision engine (G-10); answer with Understood or Replied for [requestId].
     * [partialOnly]: the transcript is an unconfirmed partial result; it must not act without a question (NFG-18).
     */
    data class Decide(val requestId: Int, val transcript: String, val partialOnly: Boolean = false) : DialogEffect {
        override fun toString() = "Decide(requestId=$requestId, partialOnly=$partialOnly)"
    }

    /** Run the confirmed action; answer with ExecutionSucceeded/Failed for [requestId]. */
    data class Execute(val requestId: Int) : DialogEffect

    /** Abort decision or execution work of [requestId] (Stop pressed). */
    data class CancelWork(val requestId: Int) : DialogEffect

    /** Timer requests; [DialogSession] handles these itself and does not forward them. */
    data class ScheduleTimer(val token: Int, val delayMs: Long) : DialogEffect
    data object CancelTimer : DialogEffect
}
