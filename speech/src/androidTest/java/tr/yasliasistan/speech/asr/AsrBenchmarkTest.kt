package tr.yasliasistan.speech.asr

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tr.yasliasistan.core.asr.AsrCsv
import tr.yasliasistan.core.asr.AsrManifest
import tr.yasliasistan.core.asr.AsrResult
import tr.yasliasistan.core.asr.AsrRunConfig
import tr.yasliasistan.core.asr.AsrSetup
import tr.yasliasistan.core.asr.AsrSummary
import tr.yasliasistan.core.asr.AsrTiming
import tr.yasliasistan.core.asr.AudioMix
import tr.yasliasistan.speech.whisper.WhisperModelStore
import tr.yasliasistan.speech.whisper.WhisperTranscriber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Speech-recognition measurement tool (PLAN G-13; debug test APK only, never in the app).
 *
 * Runs every chosen engine over the same clips and writes, per run, into
 * `Android/data/tr.yasliasistan.speech.test/files/asr-olcum/<run>/`:
 * `sonuclar.csv` (one row per clip), `ozet.csv`, `ozet.md` and `bilgi.txt`.
 * Skipped unless started with `-e asr 1` (see tools/asr-olcum.ps1), so normal device test runs
 * stay short. Arguments: see [AsrRunConfig.parse].
 *
 * Data: made-up commands spoken by the phone's offline Turkish TTS voice (no recordings of people),
 * plus optional WAVs from `files/<dataDir>/manifest.tsv`. Texts are made up, so the CSV may contain them.
 */
@RunWith(AndroidJUnit4::class)
class AsrBenchmarkTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measure() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("ölçüm yalnız '-e asr 1' ile çalışır", args.getString("asr") == "1")
        val config = AsrRunConfig.parse(args.keySet().associateWith { args.getString(it).orEmpty() })
        val run = config.run.ifEmpty { SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) }
        val outDir = File(context.getExternalFilesDir(OUT_FOLDER), run).apply { mkdirs() }
        val problems = config.problems.toMutableList()
        val info = mutableListOf<String>()
        info += deviceInfo()

        val tts = if (config.useTts) TurkishTtsSynth.open(context) else null
        if (config.useTts && tts == null) problems += "çevrimdışı Türkçe TTS sesi yok: TTS klipleri üretilemedi"
        info += "tts=${tts?.voiceLabel ?: "-"}"
        val factory = ClipFactory(tts)
        val clips = try {
            buildList {
                if (config.useTts) addAll(factory.ttsClips(config))
                if (config.useFolder) addAll(factory.folderClips(dataDir(config), problems))
            }
        } finally {
            tts?.close()
        }
        info += "klip=${clips.size} (" + clips.groupingBy { it.clip.condition }.eachCount().entries.joinToString { "${it.key}=${it.value}" } + ")"
        Log.i(TAG, "run=$run clips=${clips.size} engines=${config.engines}")
        if (config.exportClips) exportClips(clips, dataDir(config))
        assertTrue("ölçülecek klip yok: ${problems.joinToString()}", clips.isNotEmpty())

        val rows = mutableListOf<AsrResult>()
        val store = WhisperModelStore.forContext(context)
        for (engine in config.engines) {
            if (engine == AsrRunConfig.SYSTEM) {
                measureSystem(run, clips, rows)
            } else {
                val model = store.find(engine) ?: copyFromTestAssets(engine)
                if (model == null || WhisperTranscriber.nativeVariant() == null) {
                    problems += "$engine: model dosyası ya da Whisper yerel kitaplığı yok, atlandı"
                    continue
                }
                info += "whisper lib=${WhisperTranscriber.nativeVariant()} ${model.name}=${model.length() / MB} MB"
                WhisperRunner(run, config.vad).measure(model, config.whisperSettings(), clips) { rows += it }
            }
            write(outDir, rows, info, problems, config) // after each engine, so a crash keeps earlier results
        }
        write(outDir, rows, info, problems, config)
        Log.i(TAG, "done run=$run rows=${rows.size} out=${outDir.absolutePath}")
        assertTrue("hiçbir motor ölçülemedi: ${problems.joinToString()}", rows.isNotEmpty())
    }

    private fun measureSystem(run: String, clips: List<ClipAudio>, rows: MutableList<AsrResult>) {
        val setup = AsrSetup(AsrRunConfig.SYSTEM, "android-ondevice", "-")
        val recognizer = SystemFileRecognizer(context)
        clips.forEachIndexed { i, c ->
            val sampler = PssSampler().also { it.start() }
            val outcome = recognizer.recognize(c.samples)
            val peak = sampler.stopMb()
            // The recognizer runs in another app's process: the PSS here is only our side.
            val timing = AsrTiming(c.durationMs, outcome.afterEndMs, peakPssMb = peak)
            val error = if (outcome.error == "no_match") "" else outcome.error
            rows += AsrResult(run, setup, c.clip, timing, outcome.text, error)
            Log.i(TAG, "system ${i + 1}/${clips.size} ${c.clip.id} after_end=${outcome.afterEndMs} ms total=${outcome.totalMs} ms ${outcome.error}")
            if (outcome.error == "unsupported" || outcome.error == "error_${ERROR_INSUFFICIENT_PERMISSIONS}") {
                Log.w(TAG, "system recognizer cannot take file audio here (${outcome.error}); stopping it")
                return
            }
        }
    }

    private fun write(dir: File, rows: List<AsrResult>, info: List<String>, problems: List<String>, config: AsrRunConfig) {
        File(dir, "sonuclar.csv").writeText(AsrCsv.format(rows))
        val summary = AsrSummary.summarize(rows)
        File(dir, "ozet.csv").writeText(AsrSummary.csv(summary))
        File(dir, "ozet.md").writeText(AsrSummary.markdown(summary))
        File(dir, "bilgi.txt").writeText(
            (info + settingsLine(config) + problems.map { "sorun: $it" })
                .joinToString("\n", postfix = "\n"),
        )
    }

    private fun settingsLine(c: AsrRunConfig) =
        "ayarlar: motorlar=${c.engines.joinToString("+")} tts=${c.useTts} klasor=${c.useFolder}(${c.dataDir}) " +
            "kosullar=${c.conditions.joinToString("+")} komut=${c.limit} sessiz=${c.silentClips} snr=${c.snrDb} dB " +
            "audioCtx=${c.audioCtx} threads=${c.threads} beam=${c.beams} vad=${c.vad}"

    private fun exportClips(clips: List<ClipAudio>, dir: File) {
        dir.mkdirs()
        val entries = clips.filter { it.clip.source == ClipFactory.SOURCE_TTS }.map { c ->
            val file = "${c.clip.id}.wav"
            File(dir, file).writeBytes(WavFile.encode(AudioMix.toPcm16(c.samples)))
            AsrManifest.Entry(c.clip.id, file, c.clip.condition, c.clip.reference)
        }
        File(dir, ClipFactory.MANIFEST).writeText(AsrManifest.format(entries))
        Log.i(TAG, "exported ${entries.size} clips to ${dir.name}")
    }

    /**
     * Models bundled into the test APK from the git-ignored `speech/src/androidTest/assets/whisper/`
     * are copied to the app-specific external folder (kept for later runs; removed on uninstall).
     */
    private fun copyFromTestAssets(name: String): File? {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        if (assets.list(WhisperModelStore.FOLDER)?.contains(name) != true) return null
        val dir = context.getExternalFilesDir(WhisperModelStore.FOLDER) ?: return null
        val target = File(dir, name)
        assets.open("${WhisperModelStore.FOLDER}/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
        Log.i(TAG, "model $name copied from test assets")
        return target
    }

    private fun dataDir(config: AsrRunConfig) = File(context.getExternalFilesDir(null), config.dataDir)

    private fun deviceInfo(): List<String> {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return listOf(
            "cihaz=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}",
            "cekirdek=${Runtime.getRuntime().availableProcessors()} ram=${mem.totalMem / MB} MB abi=${Build.SUPPORTED_ABIS.first()}",
            "whisper_sistem=${WhisperTranscriber.systemInfo()}",
        )
    }

    private companion object {
        const val TAG = "G13Asr"
        const val OUT_FOLDER = "asr-olcum"
        const val MB = 1024 * 1024
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
    }
}
