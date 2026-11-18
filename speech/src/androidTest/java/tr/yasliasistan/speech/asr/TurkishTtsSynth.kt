package tr.yasliasistan.speech.asr

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import tr.yasliasistan.speech.whisper.WavAudio
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Turns made-up Turkish test sentences into 16 kHz mono audio with the phone's own OFFLINE TTS
 * voice, so speech recognition can be measured without recording anyone (G-12, G-13).
 * Google's engine is tried first (the Samsung engine has no Turkish on the A15).
 * The temporary WAV file lives in the cache folder and is deleted right away.
 */
class TurkishTtsSynth private constructor(
    private val tts: TextToSpeech,
    private val dir: File,
    /** Engine package and voice name, for the run report. */
    val voiceLabel: String,
) {

    /** 16 kHz mono samples in [-1, 1]; empty if synthesis failed. */
    fun synthesize(text: String): FloatArray {
        val out = File(dir, "asr-tts.wav")
        val done = CountDownLatch(1)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = done.countDown()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = done.countDown()
        })
        tts.synthesizeToFile(text, null, out, "asr")
        done.await(SYNTH_TIMEOUT_S, TimeUnit.SECONDS)
        return try {
            if (!out.isFile) FloatArray(0) else WavAudio.toWhisperInput(WavAudio.parse(out.readBytes()))
        } catch (_: IllegalArgumentException) {
            FloatArray(0)
        } finally {
            out.delete()
        }
    }

    fun close() = tts.shutdown()

    companion object {
        private const val TAG = "AsrTts"
        private const val SYNTH_TIMEOUT_S = 20L

        /** Null when the phone has no offline Turkish voice. */
        fun open(context: Context): TurkishTtsSynth? {
            for (engine in listOf("com.google.android.tts", null)) {
                val ready = CountDownLatch(1)
                var status = TextToSpeech.ERROR
                val tts = if (engine == null) {
                    TextToSpeech(context) { status = it; ready.countDown() }
                } else {
                    TextToSpeech(context, { status = it; ready.countDown() }, engine)
                }
                ready.await(10, TimeUnit.SECONDS)
                val turkish = Locale.forLanguageTag("tr-TR")
                if (status == TextToSpeech.SUCCESS && tts.isLanguageAvailable(turkish) >= TextToSpeech.LANG_AVAILABLE) {
                    tts.language = turkish
                    val offline = tts.voices?.firstOrNull { it.locale.language == "tr" && !it.isNetworkConnectionRequired }
                    if (offline != null) {
                        tts.voice = offline
                        val label = "${engine ?: "default"}/${offline.name}"
                        Log.i(TAG, "tts voice=$label")
                        return TurkishTtsSynth(tts, context.cacheDir, label)
                    }
                }
                tts.shutdown()
            }
            return null
        }
    }
}
