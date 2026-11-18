package tr.yasliasistan.speech.recognition

import android.speech.SpeechRecognizer

/** Maps android.speech.SpeechRecognizer error codes (compile-time constants) to our outcomes. */
internal object RecognizerErrors {

    /** null means "no speech / nothing recognized", which is not a failure. */
    fun failureFor(code: Int): FailureReason? = when (code) {
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_NO_MATCH -> null

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> FailureReason.MIC_PERMISSION_MISSING

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> FailureReason.LANGUAGE_UNAVAILABLE

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> FailureReason.BUSY

        SpeechRecognizer.ERROR_AUDIO -> FailureReason.AUDIO

        // An on-device recognizer must not need the network; if it reports network errors
        // it is not usable offline, and we do not switch to an online recognizer (NFG-17).
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> FailureReason.ENGINE_UNAVAILABLE

        else -> FailureReason.OTHER
    }

    /** After these errors the recognizer instance is recreated before the next turn. */
    fun needsFreshRecognizer(code: Int): Boolean = code == SpeechRecognizer.ERROR_CLIENT ||
        code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
        code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
}
