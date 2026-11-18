package tr.yasliasistan.speech.tts

/**
 * Spoken output of the assistant (G-08, NFG-20, TK-03).
 *
 * Utterance ids are the ones of `DialogEffect.Speak`; the host maps [Listener.onSpeechDone]
 * to `DialogEvent.SpeechFinished`. All calls and callbacks happen on the main thread.
 * Text may contain personal data: implementations never log it outside `SafeLog.content`.
 */
interface Speaker {

    /** Queues [text] after anything already being said. */
    fun speak(utteranceId: Int, text: String)

    /** Silences speech at once and drops the queue (Stop button, barge-in for G-16). */
    fun stop()

    /**
     * Like [stop], but a shown caption stays a while: the user cut a question short and still has
     * to answer it (G-16/G-17). Without captions it is just [stop].
     */
    fun stopKeepingText() = stop()

    fun setListener(listener: Listener?)

    interface Listener {
        fun onSpeechStarted(utteranceId: Int) = Unit

        /**
         * The utterance ended on its own. Also reported, without sound, when no offline
         * Turkish voice exists: the dialog must not hang, and the text is still shown (NFG-04).
         */
        fun onSpeechDone(utteranceId: Int)

        /** The utterance was cut off (or never started) because [stop] was called. */
        fun onSpeechInterrupted(utteranceId: Int) = Unit
    }
}
