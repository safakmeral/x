package tr.yasliasistan.app.listen

import tr.yasliasistan.speech.recognition.EngineAvailability

/**
 * What the voice setup screen offers for the Turkish offline model. Pure logic, no Android.
 * The app never falls back to online recognition (NFG-17): if the model is missing, the user is
 * shown how to get it instead.
 */
enum class LanguageAdvice(
    val offerDownload: Boolean,
    val offerSettings: Boolean,
    val offerRecheck: Boolean,
) {
    CHECKING(offerDownload = false, offerSettings = false, offerRecheck = false),
    READY(offerDownload = false, offerSettings = false, offerRecheck = false),
    /** The phone cannot report; a try-out tells whether Turkish works offline. */
    UNKNOWN(offerDownload = true, offerSettings = true, offerRecheck = true),
    DOWNLOAD_NEEDED(offerDownload = true, offerSettings = true, offerRecheck = true),
    DOWNLOADING(offerDownload = false, offerSettings = true, offerRecheck = true),
    NOT_SUPPORTED(offerDownload = false, offerSettings = true, offerRecheck = true),
    ANDROID_TOO_OLD(offerDownload = false, offerSettings = false, offerRecheck = false);

    companion object {
        fun from(availability: EngineAvailability?): LanguageAdvice = when (availability) {
            null -> CHECKING
            EngineAvailability.AVAILABLE -> READY
            EngineAvailability.UNKNOWN -> UNKNOWN
            EngineAvailability.LANGUAGE_NOT_INSTALLED -> DOWNLOAD_NEEDED
            EngineAvailability.LANGUAGE_DOWNLOADING -> DOWNLOADING
            EngineAvailability.NO_ON_DEVICE_RECOGNIZER,
            EngineAvailability.LANGUAGE_NOT_SUPPORTED -> NOT_SUPPORTED
            EngineAvailability.ANDROID_TOO_OLD -> ANDROID_TOO_OLD
        }
    }
}

/** Microphone permission as the setup screen sees it. */
enum class MicAdvice {
    GRANTED,

    /** Not granted yet: ask with the system dialog after our explanation. */
    ASK,

    /** Denied with "don't ask again": only the app settings page can change it. */
    OPEN_APP_SETTINGS;

    companion object {
        /**
         * [askedBefore]: we already showed the system dialog in this screen.
         * [rationaleAllowed]: Activity.shouldShowRequestPermissionRationale.
         */
        fun from(granted: Boolean, askedBefore: Boolean, rationaleAllowed: Boolean): MicAdvice = when {
            granted -> GRANTED
            askedBefore && !rationaleAllowed -> OPEN_APP_SETTINGS
            else -> ASK
        }
    }
}

/** A try-out may open the microphone only when both are fine (FG-02: still only on a tap). */
fun canTryOut(mic: MicAdvice, language: LanguageAdvice): Boolean =
    mic == MicAdvice.GRANTED && (language == LanguageAdvice.READY || language == LanguageAdvice.UNKNOWN)
