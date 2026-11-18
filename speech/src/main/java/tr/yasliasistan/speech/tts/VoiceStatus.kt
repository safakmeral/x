package tr.yasliasistan.speech.tts

/** Whether the assistant can speak Turkish offline, and what the user can do if not. */
sealed interface VoiceStatus {

    /** Engines are still starting; speech requested now is held until the check ends. */
    data object Checking : VoiceStatus

    /**
     * The installed, network-free Turkish voice [voiceName] is in use. There is no "language only"
     * readiness: without a named offline voice the engine might choose a network voice (UD-1 Ö-5).
     */
    data class Ready(val enginePackage: String, val voiceName: String) : VoiceStatus

    /** [enginePackage] can speak Turkish once its offline voice data is downloaded. */
    data class MissingData(val enginePackage: String) : VoiceStatus

    /** Text-to-speech works, but no installed engine offers Turkish. */
    data object NotSupported : VoiceStatus

    /** No text-to-speech engine could be started at all. */
    data object InitFailed : VoiceStatus
}
