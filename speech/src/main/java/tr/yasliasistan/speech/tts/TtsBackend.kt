package tr.yasliasistan.speech.tts

/**
 * Thin seam over the platform engine so [QueuedSpeaker]'s queue/stop logic is unit tested
 * with a fake. Callbacks must arrive on the main thread.
 */
interface TtsBackend {

    /** Starts (and warms up) the engine; reports the outcome once via [Callbacks.onStatus]. */
    fun start(callbacks: Callbacks)

    /** Drops the current engine and checks all engines again (e.g. after a voice download). */
    fun restart()

    /** Adds [text] to the engine queue under [key]. */
    fun say(key: String, text: String)

    /** Stops current speech and clears the engine queue. */
    fun halt()

    fun setRate(factor: Float)

    fun shutdown()

    interface Callbacks {
        fun onStatus(status: VoiceStatus)
        fun onStart(key: String)
        fun onDone(key: String)
        fun onError(key: String)
    }
}
