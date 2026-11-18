package tr.yasliasistan.speech.whisper

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.speech.recognition.Cancellable
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.recognition.FailureReason
import tr.yasliasistan.speech.recognition.ListenConfig
import tr.yasliasistan.speech.recognition.RecognitionTurn
import tr.yasliasistan.speech.recognition.SpeechEngine
import tr.yasliasistan.speech.recognition.SpeechListener
import tr.yasliasistan.speech.recognition.SpeechOutcome
import tr.yasliasistan.speech.recognition.TurnScheduler
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * On-device recognition with whisper.cpp (G-12). Used where the system's offline recognizer is not
 * available, e.g. Android 10-11 (PLAN S-03); the choice is made by EnginePolicy (G-15).
 *
 * Privacy (NFG-16/48/49): the microphone is open only during a turn started by the caller; audio
 * stays in memory ([SpeechAudioBuffer]), is wiped after decoding and never leaves the device.
 * Only the detected speech part is decoded, so silence never reaches the model (fewer made-up texts).
 *
 * Memory (NFG-72): the model is loaded once (in parallel with recording, so the user does not wait
 * for it), kept between turns, and freed after [WhisperOptions.idleUnloadMs], on [release] or when
 * Android reports memory pressure.
 *
 * Main-thread API, like every [SpeechEngine]; turn timing is shared with G-07 via [RecognitionTurn].
 */
class WhisperEngine(
    context: Context,
    private val options: WhisperOptions = WhisperOptions(),
) : SpeechEngine {

    /** The model this engine loads (chosen by EnginePolicy, G-15). */
    val modelFileName: String get() = options.modelFileName

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val scheduler = TurnScheduler { delayMs, action ->
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        Cancellable { handler.removeCallbacks(runnable) }
    }
    private val models = WhisperModelStore.forContext(appContext)
    private val transcriber = WhisperTranscriber()

    /** One serial worker for load/decode/unload; its thread ends when idle. */
    private val worker = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "whisper-decode")
    }

    private var session: Session? = null
    private val idleUnload = Runnable { if (session == null) unloadModel("idle") }
    private var memoryWatchOn = false

    override fun checkAvailability(onResult: (EngineAvailability) -> Unit) {
        val availability = when {
            !WhisperNative.isAvailable -> EngineAvailability.NO_ON_DEVICE_RECOGNIZER
            models.find(options.modelFileName) == null -> EngineAvailability.LANGUAGE_NOT_INSTALLED
            else -> EngineAvailability.AVAILABLE
        }
        SafeLog.event(TAG, "availability=$availability lib=${WhisperNative.loadedLibrary}")
        handler.post { onResult(availability) }
    }

    override fun startListening(listener: SpeechListener, config: ListenConfig) {
        session?.turn?.cancelRequested()
        val model = models.find(options.modelFileName)
        val failure = when {
            !WhisperNative.isAvailable -> FailureReason.ENGINE_UNAVAILABLE
            model == null -> FailureReason.LANGUAGE_UNAVAILABLE
            appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                FailureReason.MIC_PERMISSION_MISSING
            else -> null
        }
        if (failure != null || model == null) {
            SafeLog.event(TAG, "cannot listen: $failure", SafeLog.Level.WARN)
            listener.onOutcome(SpeechOutcome.Failed(failure ?: FailureReason.OTHER))
            return
        }
        handler.removeCallbacks(idleUnload)
        watchMemory(true)
        session = Session(listener, config, model).also { it.start() }
    }

    override fun finishListening() {
        session?.turn?.finishRequested()
    }

    override fun cancel() {
        session?.turn?.cancelRequested()
    }

    override fun release() {
        session?.turn?.cancelRequested()
        session = null
        handler.removeCallbacks(idleUnload)
        unloadModel("release")
    }

    private fun unloadModel(reason: String) {
        watchMemory(false)
        worker.execute {
            if (transcriber.isLoaded) {
                transcriber.unload()
                SafeLog.event(TAG, "model unloaded ($reason)")
            }
        }
    }

    private fun watchMemory(on: Boolean) {
        if (on == memoryWatchOn) return
        memoryWatchOn = on
        if (on) appContext.registerComponentCallbacks(memoryCallbacks) else appContext.unregisterComponentCallbacks(memoryCallbacks)
    }

    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (MemoryPolicy.shouldUnload(level)) handler.post { if (session == null) unloadModel("memory $level") }
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Deprecated in Android", ReplaceWith("onTrimMemory"))
        override fun onLowMemory() {
            handler.post { if (session == null) unloadModel("low memory") }
        }
    }

    /** One listening turn: microphone -> VAD + buffer -> (on stop) Whisper -> text. */
    private inner class Session(
        private val listener: SpeechListener,
        config: ListenConfig,
        private val model: File,
    ) : MicRecorder.Callbacks {

        private val startedAt = SystemClock.elapsedRealtime()

        // G-16: while the assistant speaks, echo-cancelled source and a stricter detector.
        private val duringSpeech = config.duringSpeech
        private val vad = EnergyVad(if (duringSpeech) EnergyVad.Config.DURING_SPEECH else EnergyVad.Config())
        private val buffer = SpeechAudioBuffer(maxSamples = ((config.maxMs + 1_000) * MicRecorder.RATE / 1000).toInt())
        private val recorder = MicRecorder(this, echoCancel = duringSpeech)
        private var lastActivityPost = 0L
        private var userVoiceReported = false

        @Volatile
        private var mode = Mode.LISTENING

        val turn = RecognitionTurn(
            // Whisper decodes after the user stops; allow time for that before giving up.
            config = config.copy(finalResultGraceMs = maxOf(config.finalResultGraceMs, options.decodeTimeoutMs)),
            scheduler = scheduler,
            mic = object : RecognitionTurn.MicControl {
                override fun stop() = stopForResult()
                override fun abort() = discard()
            },
            listener = object : SpeechListener by listener {
                override fun onListening() {
                    SafeLog.event(TAG, "mic open after ${elapsed()} ms")
                    listener.onListening()
                }

                override fun onOutcome(outcome: SpeechOutcome) = finished(outcome)
            },
        )

        fun start() {
            worker.execute { preload() }
            recorder.start()
        }

        private fun isCurrent() = session === this && !turn.isFinished

        private fun preload() {
            if (mode == Mode.DISCARD) return
            if (!transcriber.load(model, options.flashAttention)) {
                SafeLog.event(TAG, "model load failed", SafeLog.Level.ERROR)
                handler.post { if (isCurrent()) turn.onError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) }
            } else if (transcriber.lastLoadMs > 0) {
                SafeLog.event(TAG, "model ready (${transcriber.lastLoadMs} ms load)")
            }
        }

        private fun stopForResult() {
            if (mode == Mode.LISTENING) mode = Mode.DECODE
            recorder.requestStop()
        }

        private fun discard() {
            mode = Mode.DISCARD
            recorder.requestStop()
            transcriber.abort()
        }

        private fun finished(outcome: SpeechOutcome) {
            discard()
            SafeLog.event(TAG, "turn over: ${outcome.kind()} after ${elapsed()} ms")
            if (outcome is SpeechOutcome.Heard) SafeLog.content(TAG) { "heard: ${outcome.text}" }
            if (session === this) {
                session = null
                handler.postDelayed(idleUnload, options.idleUnloadMs)
            }
            listener.onOutcome(outcome)
        }

        // --- recorder thread ---

        override fun onStarted() {
            handler.post { if (isCurrent()) turn.onReady() }
        }

        override fun onFrame(frame: ShortArray, length: Int) {
            if (mode != Mode.LISTENING) return
            val speech = vad.process(frame, length)
            buffer.append(frame, length, speech)
            if (duringSpeech && speech && !userVoiceReported) {
                userVoiceReported = true // posted at once, not throttled: the speech must stop fast
                handler.post { if (isCurrent()) listener.onUserVoice() }
            }
            val now = SystemClock.elapsedRealtime()
            if (speech && now - lastActivityPost >= ACTIVITY_POST_MS) {
                lastActivityPost = now
                handler.post { if (isCurrent()) turn.onSpeechActivity() }
            }
        }

        override fun onClosed(error: MicRecorder.MicError?) {
            if (error != null) {
                buffer.wipe()
                SafeLog.event(TAG, "microphone error: $error", SafeLog.Level.WARN)
                val code = if (error == MicRecorder.MicError.PERMISSION) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                } else {
                    SpeechRecognizer.ERROR_AUDIO
                }
                handler.post { if (isCurrent()) turn.onError(code) }
                return
            }
            if (mode != Mode.DECODE) {
                buffer.wipe()
                return
            }
            val clip = buffer.speechClip(padSamples = PAD_SAMPLES, minSamples = MIN_SAMPLES, onsetSamples = ONSET_SAMPLES)
            buffer.wipe()
            if (clip == null) {
                handler.post { if (isCurrent()) turn.onFinalText(null) }
            } else {
                worker.execute { decode(clip) }
            }
        }

        // --- worker thread ---

        private fun decode(clip: FloatArray) {
            try {
                if (mode == Mode.DISCARD) return
                if (!transcriber.load(model, options.flashAttention)) {
                    handler.post { if (isCurrent()) turn.onError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) }
                    return
                }
                val result = transcriber.transcribe(clip, options)
                if (result != null) {
                    SafeLog.event(
                        TAG,
                        "decoded ${result.audioMs} ms audio in ${result.decodeMs} ms " +
                            "(model=${model.name}, lib=${WhisperNative.loadedLibrary})",
                    )
                }
                handler.post {
                    if (!isCurrent()) return@post
                    if (result == null) turn.onError(SpeechRecognizer.ERROR_SERVER) else turn.onFinalText(result.text)
                }
            } finally {
                clip.fill(0f)
            }
        }

        private fun elapsed() = SystemClock.elapsedRealtime() - startedAt
    }

    private enum class Mode { LISTENING, DECODE, DISCARD }

    private fun SpeechOutcome.kind(): String = when (this) {
        is SpeechOutcome.Heard -> "heard"
        SpeechOutcome.NothingHeard -> "nothing"
        SpeechOutcome.Cancelled -> "cancelled"
        is SpeechOutcome.Failed -> "failed:$reason"
    }

    private companion object {
        const val TAG = "WhisperSpeech"
        const val ACTIVITY_POST_MS = 150L
        const val PAD_SAMPLES = MicRecorder.RATE * 300 / 1000
        const val MIN_SAMPLES = MicRecorder.RATE // 1 s
        val ONSET_SAMPLES = EnergyVad.Config().onsetFrames * MicRecorder.FRAME
    }
}

/** When Android asks to free memory, the idle Whisper model goes first (NFG-72). Pure logic. */
object MemoryPolicy {
    // Values of ComponentCallbacks2.TRIM_MEMORY_* (kept literal so this stays JVM-testable).
    private const val RUNNING_LOW = 10
    private const val RUNNING_CRITICAL = 15
    private const val BACKGROUND = 40

    fun shouldUnload(level: Int): Boolean =
        level == RUNNING_LOW || level == RUNNING_CRITICAL || level >= BACKGROUND
}
