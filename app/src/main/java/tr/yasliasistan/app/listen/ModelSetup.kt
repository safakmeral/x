package tr.yasliasistan.app.listen

import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.whisper.DownloadResult

/** Progress of the Whisper model download on the voice setup screen (G-15). */
sealed interface ModelDownloadUi {
    data object Idle : ModelDownloadUi
    data class Running(val doneBytes: Long, val totalBytes: Long) : ModelDownloadUi {
        val percent: Int get() = if (totalBytes <= 0) 0 else ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }
    data class Ended(val result: DownloadResult) : ModelDownloadUi
}

/**
 * What the setup screen offers when Whisper is the engine (G-15, V-21). Pure logic, no Android.
 * The model is downloaded only when the user taps the button that states its size (consent).
 * When it fails, the user is told plainly and offered "Yazarak söyle" (typing) instead.
 */
enum class ModelAdvice(val offerDownload: Boolean, val offerTyping: Boolean) {
    CHECKING(offerDownload = false, offerTyping = false),
    READY(offerDownload = false, offerTyping = false),
    OFFER_DOWNLOAD(offerDownload = true, offerTyping = false),
    DOWNLOADING(offerDownload = false, offerTyping = false),
    FAILED_NETWORK(offerDownload = true, offerTyping = true),
    FAILED_CHECKSUM(offerDownload = true, offerTyping = true),
    FAILED_SPACE(offerDownload = true, offerTyping = true),

    /** Whisper cannot run on this phone (no arm64 library). */
    UNAVAILABLE(offerDownload = false, offerTyping = true);

    companion object {
        fun from(availability: EngineAvailability?, download: ModelDownloadUi): ModelAdvice = when {
            download is ModelDownloadUi.Running -> DOWNLOADING
            availability == EngineAvailability.AVAILABLE -> READY
            availability == null -> CHECKING
            download is ModelDownloadUi.Ended -> when (download.result) {
                DownloadResult.NETWORK -> FAILED_NETWORK
                DownloadResult.CHECKSUM -> FAILED_CHECKSUM
                DownloadResult.NO_SPACE -> FAILED_SPACE
                // DONE but the engine still misses the model: treat as a broken download.
                DownloadResult.DONE -> FAILED_CHECKSUM
                DownloadResult.CANCELLED -> OFFER_DOWNLOAD
            }
            availability == EngineAvailability.LANGUAGE_NOT_INSTALLED -> OFFER_DOWNLOAD
            else -> UNAVAILABLE
        }
    }
}
