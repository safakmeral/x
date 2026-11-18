package tr.yasliasistan.speech.recognition

import tr.yasliasistan.core.Defaults

/**
 * A speech-to-text engine that runs entirely on the device (NFG-16, NFG-48).
 * Implementations: [OnDeviceSystemEngine] (Android's offline recognizer, G-07) and later a
 * whisper.cpp engine (G-12). Audio is processed in memory and never written to a file (NFG-49).
 *
 * All methods must be called on the main thread; all callbacks arrive on the main thread.
 * The caller decides WHEN to listen (FG-02: only after a bubble tap); the engine never starts
 * the microphone on its own.
 */
interface SpeechEngine {

    /** Checks, without opening the microphone, whether Turkish can be recognized offline. */
    fun checkAvailability(onResult: (EngineAvailability) -> Unit)

    /**
     * Opens the microphone for one listening turn. [listener] gets exactly one
     * [SpeechListener.onOutcome]; after it the microphone is closed.
     * Starting while a turn is running cancels the running turn first.
     */
    fun startListening(listener: SpeechListener, config: ListenConfig = ListenConfig())

    /** The user is done talking: close the microphone and deliver what was heard. */
    fun finishListening()

    /** Abort ("Dur"): close the microphone now; the outcome is [SpeechOutcome.Cancelled]. */
    fun cancel()

    /** Free native resources (service destroyed / session over). The engine can be reused. */
    fun release()
}

/** Callbacks for one listening turn. User text arrives here only; never log it outside SafeLog.content. */
interface SpeechListener {
    /** The microphone is open and the user can talk: show the listening indicator now. */
    fun onListening()

    /** Text recognized so far (may change); useful for a live caption. */
    fun onPartialText(text: String) = Unit

    /**
     * Barge-in (G-16): in a [ListenConfig.duringSpeech] turn, an engine without live text (Whisper)
     * reports here, at most once, that its stricter voice detector heard the user over the assistant.
     * Engines with live text never call it; the caller checks their partial text against echo instead.
     */
    fun onUserVoice() = Unit

    /** The turn is over and the microphone is closed. */
    fun onOutcome(outcome: SpeechOutcome)
}

sealed interface SpeechOutcome {
    /**
     * Usable command text (already trimmed, not empty, not just noise). [partialOnly] is true when the
     * recognizer gave no final result and the last partial text was used; such text must not act or
     * confirm without a question (NFG-18).
     */
    data class Heard(val text: String, val partialOnly: Boolean = false) : SpeechOutcome {
        override fun toString() = "Heard(<${text.length} chars>, partialOnly=$partialOnly)"
    }

    /** Silence, noise, filler sounds or nothing recognized: produces no command (NFG-18). */
    data object NothingHeard : SpeechOutcome

    /** Stopped by [SpeechEngine.cancel]. */
    data object Cancelled : SpeechOutcome

    data class Failed(val reason: FailureReason) : SpeechOutcome
}

enum class FailureReason {
    /** RECORD_AUDIO is not granted. */
    MIC_PERMISSION_MISSING,

    /** Turkish on-device model is missing or was removed (see [EngineAvailability]). */
    LANGUAGE_UNAVAILABLE,

    /** Another app is using the microphone or recognizer. */
    BUSY,

    /** The audio could not be recorded (e.g. microphone blocked in the background). */
    AUDIO,

    /** The engine cannot run offline on this device (never falls back to online, NFG-17). */
    ENGINE_UNAVAILABLE,

    OTHER,
}

enum class EngineAvailability {
    /** Turkish on-device recognition is installed and ready. */
    AVAILABLE,

    /** The device cannot report language support; listening is attempted and errors are handled. */
    UNKNOWN,

    /** Android 10-11 (API 29-30): no on-device recognizer API. */
    ANDROID_TOO_OLD,

    /** No on-device recognition service on this phone. */
    NO_ON_DEVICE_RECOGNIZER,

    /** The recognizer supports Turkish offline but the language pack is not downloaded. */
    LANGUAGE_NOT_INSTALLED,

    /** The Turkish pack download is pending or in progress. */
    LANGUAGE_DOWNLOADING,

    /** The on-device recognizer does not offer Turkish at all. */
    LANGUAGE_NOT_SUPPORTED;

    /** Worth opening the microphone for. */
    val canListen: Boolean get() = this == AVAILABLE || this == UNKNOWN
}

/**
 * Timing of one listening turn. Defaults come from V-12 (core Defaults) plus [noSpeechMs],
 * the wait for the user to START talking (docs/SAPMALAR.md).
 */
data class ListenConfig(
    val languageTag: String = TURKISH,
    val silenceMs: Long = Defaults.LISTEN_SILENCE_MS,
    val maxMs: Long = Defaults.LISTEN_MAX_MS,
    val noSpeechMs: Long = NO_SPEECH_MS,
    /** After asking the recognizer to stop, how long to wait for its final text. */
    val finalResultGraceMs: Long = FINAL_GRACE_MS,
    /**
     * G-16: the assistant is speaking during this turn. Engines that own the microphone use an
     * echo-cancelling audio source and a stricter voice detector (Whisper); others ignore it.
     */
    val duringSpeech: Boolean = false,
) {
    companion object {
        const val TURKISH = "tr-TR"
        const val NO_SPEECH_MS = 8_000L
        const val FINAL_GRACE_MS = 1_500L
    }
}

/** Optional ability of engines whose model is managed by the system (G-07 on-device recognizer). */
interface LanguagePackInstaller {
    /** Asks the system to download the Turkish model. Only the model is downloaded; no user data is sent. */
    fun requestLanguageDownload(onResult: (DownloadRequest) -> Unit)
}

enum class DownloadRequest {
    /** Download finished; check availability again. */
    DONE,

    /** The system accepted the request and downloads in the background (or shows its own screen). */
    STARTED,

    /** The system downloads later (e.g. waiting for Wi-Fi). */
    SCHEDULED,

    /** This Android version cannot download from inside the app; use the settings screen. */
    NOT_POSSIBLE,

    FAILED,
}
