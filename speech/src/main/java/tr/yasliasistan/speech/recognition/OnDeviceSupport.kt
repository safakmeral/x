package tr.yasliasistan.speech.recognition

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.annotation.TargetApi
import tr.yasliasistan.core.log.SafeLog

/**
 * Asks the on-device recognizer which languages it has (API 33+) and requests the Turkish model
 * download. Uses its own short-lived recognizer so a running listening turn is never disturbed.
 * Never opens the microphone.
 */
internal class OnDeviceSupport(private val context: Context, private val handler: Handler) {

    private var checker: SpeechRecognizer? = null
    private var downloader: SpeechRecognizer? = null

    fun check(language: String, onResult: (EngineAvailability) -> Unit) {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> onResult(EngineAvailability.ANDROID_TOO_OLD)
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                onResult(EngineAvailability.NO_ON_DEVICE_RECOGNIZER)
            // Android 12 cannot list languages; listening will tell (language errors are handled).
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> onResult(EngineAvailability.UNKNOWN)
            else -> checkLanguages(language, onResult)
        }
    }

    fun requestDownload(intent: Intent, onResult: (DownloadRequest) -> Unit) {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> onResult(DownloadRequest.NOT_POSSIBLE)
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> onResult(DownloadRequest.NOT_POSSIBLE)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                fireAndForgetDownload(intent)
                onResult(DownloadRequest.STARTED)
            }
            else -> downloadWithProgress(intent, onResult)
        }
    }

    fun release() {
        checker?.destroy()
        checker = null
        downloader?.destroy()
        downloader = null
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkLanguages(language: String, onResult: (EngineAvailability) -> Unit) {
        checker?.destroy()
        val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { checker = it }
        var delivered = false
        fun deliver(result: EngineAvailability) {
            if (delivered) return
            delivered = true
            handler.removeCallbacksAndMessages(CHECK_TOKEN)
            if (checker === rec) {
                rec.destroy()
                checker = null
            }
            onResult(result)
        }
        // Some services never answer; do not keep the user waiting.
        handler.postDelayed({ deliver(EngineAvailability.UNKNOWN) }, CHECK_TOKEN, CHECK_TIMEOUT_MS)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        rec.checkRecognitionSupport(
            intent,
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    SafeLog.event(
                        TAG,
                        "support installed=${support.installedOnDeviceLanguages.size} " +
                            "pending=${support.pendingOnDeviceLanguages.size} " +
                            "supported=${support.supportedOnDeviceLanguages.size}",
                    )
                    deliver(
                        LanguageSupport.classify(
                            installed = support.installedOnDeviceLanguages,
                            pending = support.pendingOnDeviceLanguages,
                            supported = support.supportedOnDeviceLanguages,
                            language = language,
                        ),
                    )
                }

                override fun onError(error: Int) {
                    SafeLog.event(TAG, "support check error=$error", SafeLog.Level.WARN)
                    deliver(EngineAvailability.UNKNOWN)
                }
            },
        )
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun fireAndForgetDownload(intent: Intent) {
        val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        rec.triggerModelDownload(intent)
        handler.postDelayed({ rec.destroy() }, DOWNLOAD_KEEPALIVE_MS)
    }

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun downloadWithProgress(intent: Intent, onResult: (DownloadRequest) -> Unit) {
        downloader?.destroy()
        val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { downloader = it }
        var startedReported = false
        fun end(result: DownloadRequest) {
            if (downloader === rec) {
                rec.destroy()
                downloader = null
            }
            onResult(result)
        }
        rec.triggerModelDownload(
            intent,
            context.mainExecutor,
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    if (!startedReported) {
                        startedReported = true
                        onResult(DownloadRequest.STARTED)
                    }
                }

                override fun onSuccess() = end(DownloadRequest.DONE)
                override fun onScheduled() = end(DownloadRequest.SCHEDULED)

                override fun onError(error: Int) {
                    SafeLog.event(TAG, "download error=$error", SafeLog.Level.WARN)
                    if (error == SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS) {
                        // The service downloads but cannot report progress.
                        end(DownloadRequest.STARTED)
                    } else {
                        end(DownloadRequest.FAILED)
                    }
                }
            },
        )
    }

    private companion object {
        const val TAG = "OnDeviceSupport"
        val CHECK_TOKEN = Any()
        const val CHECK_TIMEOUT_MS = 3_000L
        const val DOWNLOAD_KEEPALIVE_MS = 60_000L
    }
}
