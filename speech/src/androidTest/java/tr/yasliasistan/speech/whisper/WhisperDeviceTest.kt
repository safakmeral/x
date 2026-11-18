package tr.yasliasistan.speech.whisper

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import tr.yasliasistan.speech.recognition.EngineAvailability
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device check for G-12 without a microphone: Turkish sentences are synthesized with the phone's own
 * offline TTS (no recording of a person), fed through the VAD + buffer + whisper.cpp path, and the
 * text, speed and memory are logged under tag "G12Whisper".
 *
 * Model: `ggml-tiny.bin` pushed with
 *   adb push ggml-tiny.bin /sdcard/Android/data/tr.yasliasistan.speech.test/files/whisper/
 * or, for `connectedDebugAndroidTest` (which reinstalls the APK), placed in
 * `speech/src/androidTest/assets/whisper/` (git-ignored). Tests are skipped if neither exists.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WhisperDeviceTest {

    @Test
    fun a_nativeLibraryLoads() {
        val variant = WhisperTranscriber.nativeVariant()
        Log.i(TAG, "sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.first()} lib=$variant cores=${Runtime.getRuntime().availableProcessors()}")
        Log.i(TAG, "system_info=${WhisperTranscriber.systemInfo()}")
        assertNotNull("whisper native library did not load", variant)
    }

    @Test
    fun b_engineReportsModelAvailable() {
        assumeTrue("no tiny model on device", model != null)
        val latch = CountDownLatch(1)
        var result: EngineAvailability? = null
        Handler(Looper.getMainLooper()).post {
            WhisperEngine(context).checkAvailability {
                result = it
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        assertEquals(EngineAvailability.AVAILABLE, result)
    }

    @Test
    fun c_transcribesTurkishSpeechFromMemory() {
        val modelFile = model
        assumeTrue("no tiny model on device", modelFile != null)
        val tts = TurkishTts.open(context)
        assumeTrue("no offline Turkish TTS voice on this phone", tts != null)

        val transcriber = WhisperTranscriber()
        val heapBefore = Debug.getNativeHeapAllocatedSize()
        assertTrue("model failed to load", transcriber.load(modelFile!!))
        val heapAfter = Debug.getNativeHeapAllocatedSize()
        Log.i(TAG, "model=${modelFile.name} load=${transcriber.lastLoadMs} ms nativeHeap +${(heapAfter - heapBefore) / MB} MB")

        try {
            SENTENCES.forEachIndexed { i, sentence ->
                val pcm16k = tts!!.synthesize(sentence)
                val clip = clipThroughVad(pcm16k)
                assertNotNull("VAD found no speech in sentence $i", clip)
                val options = WhisperOptions()
                val result = transcriber.transcribe(clip!!, options)
                assertNotNull("decode failed for sentence $i", result)
                val rtf = result!!.decodeMs.toDouble() / result.audioMs
                // Synthetic test sentences only (no user data), so the text is logged for checking accuracy.
                Log.i(TAG, "s$i audio=${result.audioMs} ms decode=${result.decodeMs} ms rtf=${"%.2f".format(Locale.ROOT, rtf)} " +
                    "threads=${DeviceProfile.threadsFor(Runtime.getRuntime().availableProcessors())} expected='$sentence' got='${result.text}'")
                assertTrue("empty text for sentence $i", result.text.isNotBlank())
            }
            val pss = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
            Log.i(TAG, "process PSS with model loaded = ${pss / 1024} MB")
        } finally {
            tts?.close()
            transcriber.unload()
            Log.i(TAG, "after unload nativeHeap=${Debug.getNativeHeapAllocatedSize() / MB} MB")
        }
    }

    /** Adds 1 s of silence around the speech and runs it through the same VAD/buffer as the microphone path. */
    private fun clipThroughVad(speech: ShortArray): FloatArray? {
        val silence = ShortArray(WavAudio.WHISPER_RATE)
        val audio = silence + speech + silence
        val vad = EnergyVad()
        val buffer = SpeechAudioBuffer(maxSamples = audio.size)
        val frame = ShortArray(MicRecorder.FRAME)
        var pos = 0
        while (pos < audio.size) {
            val n = minOf(frame.size, audio.size - pos)
            System.arraycopy(audio, pos, frame, 0, n)
            buffer.append(frame, n, vad.process(frame, n))
            pos += n
        }
        val clip = buffer.speechClip(padSamples = 4_800, minSamples = WavAudio.WHISPER_RATE, onsetSamples = 3 * MicRecorder.FRAME)
        Log.i(TAG, "vad: speech=${speech.size * 1000 / WavAudio.WHISPER_RATE} ms clip=${(clip?.size ?: 0) * 1000 / WavAudio.WHISPER_RATE} ms")
        buffer.wipe()
        return clip
    }

    /** Offline Turkish TTS (Google engine first: the Samsung engine has no Turkish on the A15). */
    private class TurkishTts private constructor(private val tts: TextToSpeech, private val dir: File) {

        fun synthesize(text: String): ShortArray {
            val out = File(dir, "g12-tts.wav")
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = done.countDown()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = done.countDown()
            })
            tts.synthesizeToFile(text, null, out, "g12")
            done.await(20, TimeUnit.SECONDS)
            try {
                val pcm = WavAudio.parse(out.readBytes())
                val floats = WavAudio.toWhisperInput(pcm)
                return ShortArray(floats.size) { (floats[it] * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
            } finally {
                out.delete()
            }
        }

        fun close() = tts.shutdown()

        companion object {
            fun open(context: Context): TurkishTts? {
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
                            Log.i(TAG, "tts engine=${engine ?: "default"} voice=${offline.name}")
                            return TurkishTts(tts, context.cacheDir)
                        }
                    }
                    tts.shutdown()
                }
                return null
            }
        }
    }

    companion object {
        private const val TAG = "G12Whisper"
        private const val MB = 1024 * 1024
        private val SENTENCES = listOf(
            "Ayarları aç",
            "Kızıma mesaj gönder",
            "Ekranın parlaklığını biraz artır lütfen",
        )

        private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
        private var model: File? = null
        private var copiedFromAssets = false

        @BeforeClass
        @JvmStatic
        fun findModel() {
            val store = WhisperModelStore.forContext(context)
            model = store.find(WhisperModels.TINY)
            if (model != null) {
                Log.i(TAG, "model from device folder: ${model!!.parentFile?.name}")
                return
            }
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            val asset = "whisper/${WhisperModels.TINY}"
            if (assets.list("whisper")?.contains(WhisperModels.TINY) != true) return
            val target = File(File(context.filesDir, WhisperModelStore.FOLDER).apply { mkdirs() }, WhisperModels.TINY)
            assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
            copiedFromAssets = true
            model = target
            Log.i(TAG, "model copied from test assets")
        }

        @AfterClass
        @JvmStatic
        fun removeCopiedModel() {
            if (copiedFromAssets) model?.delete()
        }
    }
}
