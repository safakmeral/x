package tr.yasliasistan.speech.recognition

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import tr.yasliasistan.core.log.SafeLog

/**
 * Android's built-in OFFLINE recognizer (SpeechRecognizer.createOnDeviceSpeechRecognizer, API 31+).
 *
 * NFG-16/17/48: only the on-device recognizer is ever created. This class never uses
 * the default (possibly online) recognizer service; on API 29-30 or when Turkish is missing it
 * reports "unavailable" instead of falling back. Audio stays inside the recognition service's
 * memory and is never written to a file by us (NFG-49).
 */
class OnDeviceSystemEngine(context: Context) : SpeechEngine, LanguagePackInstaller {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val scheduler = TurnScheduler { delayMs, action ->
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        Cancellable { handler.removeCallbacks(runnable) }
    }

    private var recognizer: SpeechRecognizer? = null
    private var turn: RecognitionTurn? = null
    private val support = OnDeviceSupport(appContext, handler)

    override fun checkAvailability(onResult: (EngineAvailability) -> Unit) {
        support.check(ListenConfig.TURKISH) { availability ->
            SafeLog.event(TAG, "availability=$availability")
            onResult(availability)
        }
    }

    override fun startListening(listener: SpeechListener, config: ListenConfig) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            listener.onOutcome(SpeechOutcome.Failed(FailureReason.ENGINE_UNAVAILABLE))
            return
        }
        turn?.cancelRequested()
        val rec = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            .also { recognizer = it }
        val startedAt = SystemClock.elapsedRealtime()
        val newTurn = RecognitionTurn(
            config = config,
            scheduler = scheduler,
            mic = object : RecognitionTurn.MicControl {
                override fun stop() = rec.stopListening()
                override fun abort() = rec.cancel()
            },
            listener = object : SpeechListener by listener {
                override fun onListening() {
                    SafeLog.event(TAG, "mic open after ${SystemClock.elapsedRealtime() - startedAt} ms")
                    listener.onListening()
                }

                override fun onOutcome(outcome: SpeechOutcome) {
                    SafeLog.event(TAG, "turn over: ${outcome.kind()} after ${SystemClock.elapsedRealtime() - startedAt} ms")
                    if (outcome is SpeechOutcome.Heard) SafeLog.content(TAG) { "heard: ${outcome.text}" }
                    listener.onOutcome(outcome)
                }
            },
        )
        turn = newTurn
        rec.setRecognitionListener(PlatformListener(newTurn))
        rec.startListening(recognizerIntent(config))
    }

    override fun finishListening() {
        turn?.finishRequested()
    }

    override fun cancel() {
        turn?.cancelRequested()
    }

    override fun release() {
        turn?.cancelRequested()
        turn = null
        recognizer?.destroy()
        recognizer = null
        support.release()
    }

    override fun requestLanguageDownload(onResult: (DownloadRequest) -> Unit) {
        support.requestDownload(recognizerIntent(ListenConfig())) { result ->
            SafeLog.event(TAG, "download request=$result")
            onResult(result)
        }
    }

    private fun recognizerIntent(config: ListenConfig) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        // Hints only; many recognizers ignore them, so RecognitionTurn enforces the timing itself.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, config.silenceMs)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, config.silenceMs)
    }

    /** Recreate the recognizer before the next turn (after client/busy/disconnect errors). */
    private fun dropRecognizerLater() {
        handler.post {
            if (turn?.isFinished != false) {
                recognizer?.destroy()
                recognizer = null
            }
        }
    }

    private inner class PlatformListener(private val turn: RecognitionTurn) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = turn.onReady()
        override fun onBeginningOfSpeech() = turn.onSpeechActivity()
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit // raw audio is never kept
        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstText(partialResults)?.let(turn::onPartialText)
        }

        override fun onResults(results: Bundle?) = turn.onFinalText(firstText(results))

        override fun onError(error: Int) {
            SafeLog.event(TAG, "recognizer error=$error", SafeLog.Level.WARN)
            turn.onError(error)
            if (RecognizerErrors.needsFreshRecognizer(error)) dropRecognizerLater()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun firstText(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }

    private fun SpeechOutcome.kind(): String = when (this) {
        is SpeechOutcome.Heard -> "heard"
        SpeechOutcome.NothingHeard -> "nothing"
        SpeechOutcome.Cancelled -> "cancelled"
        is SpeechOutcome.Failed -> "failed:$reason"
    }

    private companion object {
        const val TAG = "OnDeviceSpeech"
    }
}
