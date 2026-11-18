package tr.yasliasistan.speech.recognition

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.speech.whisper.WhisperEngine
import tr.yasliasistan.speech.whisper.WhisperNative
import tr.yasliasistan.speech.whisper.WhisperOptions

/**
 * The app's speech engine (G-15): asks [EnginePolicy] which recognizer to use on this phone and
 * delegates to it. Callers ([checkAvailability] first, as VoiceInputCoordinator does) need not know
 * which one runs. Both engines are on-device only (NFG-16/17/48). Main thread only.
 */
class SelectedSpeechEngine(
    context: Context,
    private val policy: EnginePolicy = EnginePolicy(),
) : SpeechEngine, LanguagePackInstaller {

    private val appContext = context.applicationContext
    private val systemHolder = lazy { OnDeviceSystemEngine(appContext) }
    private val system by systemHolder
    private var whisper: WhisperEngine? = null
    private var systemAvailability: EngineAvailability? = null
    private var systemFailedOffline = false

    /** The engine in use after the last check; shown on the voice settings screen. */
    var choice: EngineChoice = decide()
        private set

    override fun checkAvailability(onResult: (EngineAvailability) -> Unit) {
        if (Build.VERSION.SDK_INT < policy.minSystemSdk) {
            systemAvailability = EngineAvailability.ANDROID_TOO_OLD
            delegateCheck(onResult)
            return
        }
        system.checkAvailability { result ->
            systemAvailability = result
            delegateCheck(onResult)
        }
    }

    private fun delegateCheck(onResult: (EngineAvailability) -> Unit) {
        choice = decide()
        SafeLog.event(TAG, "engine=${choice.kind} model=${choice.whisperModel?.fileName}")
        active().checkAvailability(onResult)
    }

    override fun startListening(listener: SpeechListener, config: ListenConfig) {
        val engine = active()
        if (engine !== system) {
            engine.startListening(listener, config)
            return
        }
        val reportedUnknown = systemAvailability == EngineAvailability.UNKNOWN
        system.startListening(
            object : SpeechListener by listener {
                override fun onOutcome(outcome: SpeechOutcome) {
                    // Android 12 cannot list languages; if Turkish turns out to be missing offline,
                    // the next check moves to Whisper instead of failing every time.
                    if (reportedUnknown && outcome is SpeechOutcome.Failed && outcome.reason.meansNoOfflineTurkish()) {
                        systemFailedOffline = true
                    }
                    listener.onOutcome(outcome)
                }
            },
            config,
        )
    }

    override fun finishListening() = active().finishListening()

    override fun cancel() = active().cancel()

    override fun release() {
        whisper?.release()
        if (systemHolder.isInitialized()) system.release()
    }

    override fun requestLanguageDownload(onResult: (DownloadRequest) -> Unit) {
        if (choice.kind == EngineKind.SYSTEM) system.requestLanguageDownload(onResult) else onResult(DownloadRequest.NOT_POSSIBLE)
    }

    private fun active(): SpeechEngine {
        val model = choice.whisperModel
        if (choice.kind == EngineKind.SYSTEM || model == null) return system
        whisper?.let { if (it.modelFileName == model.fileName) return it else it.release() }
        return WhisperEngine(appContext, WhisperOptions(modelFileName = model.fileName)).also { whisper = it }
    }

    private fun decide(): EngineChoice = policy.choose(
        DeviceFacts(
            sdkInt = Build.VERSION.SDK_INT,
            systemAvailability = systemAvailability,
            systemFailedOffline = systemFailedOffline,
            whisperRuns = WhisperNative.isAvailable,
            totalRamBytes = totalRam(),
        ),
    )

    private fun totalRam(): Long {
        val manager = appContext.getSystemService(ActivityManager::class.java) ?: return 0L
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }

    private fun FailureReason.meansNoOfflineTurkish() =
        this == FailureReason.LANGUAGE_UNAVAILABLE || this == FailureReason.ENGINE_UNAVAILABLE

    private companion object {
        const val TAG = "SpeechEngineChoice"
    }
}
