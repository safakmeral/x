package tr.yasliasistan.speech.asr

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import tr.yasliasistan.core.asr.AudioMix
import tr.yasliasistan.speech.whisper.WavAudio
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Feeds a clip to Android's ON-DEVICE recognizer through a pipe (`EXTRA_AUDIO_SOURCE`, API 33+)
 * instead of the microphone. Only `createOnDeviceSpeechRecognizer` is used (never the online one).
 * The audio goes through a pipe in memory; nothing is written to a file.
 */
class SystemFileRecognizer(private val context: Context) {

    class Outcome(
        val text: String,
        /** Empty on success; "no_match" (heard nothing) or "error_<code>" / "timeout" / "unsupported". */
        val error: String,
        /** Time from the last audio byte handed over to the final text. */
        val afterEndMs: Long,
        /** Time from start to the final text. */
        val totalMs: Long,
    )

    private val main = Handler(Looper.getMainLooper())

    fun recognize(samples: FloatArray): Outcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Outcome("", "unsupported", 0, 0)
        val pcm = toBytes(AudioMix.toPcm16(AudioMix.pad(samples, before = 0, after = TAIL_SILENCE)))
        val pipe = ParcelFileDescriptor.createPipe()
        val done = CountDownLatch(1)
        var text = ""
        var error = ""
        var finishedAt = 0L
        var audioEndAt = 0L
        var recognizer: SpeechRecognizer? = null
        val startedAt = SystemClock.elapsedRealtime()

        main.post {
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = rec
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    finishedAt = SystemClock.elapsedRealtime()
                    done.countDown()
                }

                override fun onError(code: Int) {
                    error = if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "no_match" else "error_$code"
                    finishedAt = SystemClock.elapsedRealtime()
                    done.countDown()
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            rec.startListening(intent(pipe[0]))
        }

        val writer = Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                    var pos = 0
                    while (pos < pcm.size) {
                        val n = minOf(CHUNK_BYTES, pcm.size - pos)
                        out.write(pcm, pos, n)
                        pos += n
                    }
                }
            } catch (_: IOException) {
                // recognizer closed the pipe early (error or early result)
            }
            audioEndAt = SystemClock.elapsedRealtime()
            // Some recognizers wait for an explicit end instead of the closed pipe.
            main.post { recognizer?.stopListening() }
        }, "asr-pipe")
        writer.start()

        val answered = done.await(TIMEOUT_S, TimeUnit.SECONDS)
        writer.join(2_000)
        val destroyed = CountDownLatch(1)
        main.post {
            recognizer?.cancel()
            recognizer?.destroy()
            destroyed.countDown()
        }
        destroyed.await(5, TimeUnit.SECONDS)
        pipe[0].close()
        pcm.fill(0)
        if (!answered) return Outcome("", "timeout", 0, SystemClock.elapsedRealtime() - startedAt)
        val afterEnd = if (audioEndAt in 1..finishedAt) finishedAt - audioEndAt else 0
        return Outcome(text, error, afterEnd, finishedAt - startedAt)
    }

    private fun intent(source: ParcelFileDescriptor) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, WavAudio.WHISPER_RATE)
    }

    private fun toBytes(samples: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putShort(it) }
        samples.fill(0)
        return buf.array()
    }

    private companion object {
        const val TAIL_SILENCE = WavAudio.WHISPER_RATE // 1 s so the endpointer can close the turn
        const val CHUNK_BYTES = 3_200 // 100 ms
        const val TIMEOUT_S = 30L
    }
}
