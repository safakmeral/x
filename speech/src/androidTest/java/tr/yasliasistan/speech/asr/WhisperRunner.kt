package tr.yasliasistan.speech.asr

import android.util.Log
import tr.yasliasistan.core.asr.AsrResult
import tr.yasliasistan.core.asr.AsrRunConfig
import tr.yasliasistan.core.asr.AsrSetup
import tr.yasliasistan.core.asr.AsrTiming
import tr.yasliasistan.speech.whisper.DeviceProfile
import tr.yasliasistan.speech.whisper.WhisperOptions
import tr.yasliasistan.speech.whisper.WhisperTranscriber
import java.io.File

/**
 * Measures one Whisper model on all clips for every settings combination (audio_ctx, threads, beam).
 * Clips with speech are cut by the app's VAD first (as in live use); a clip the VAD rejects is
 * decoded whole, so the model's own hallucination on silence is measured (DEG-14).
 */
class WhisperRunner(private val run: String, private val useVad: Boolean) {

    fun measure(model: File, knobs: List<AsrRunConfig.WhisperKnobs>, clips: List<ClipAudio>, onRow: (AsrResult) -> Unit) {
        val transcriber = WhisperTranscriber()
        val loadSampler = PssSampler().also { it.start() }
        val loaded = transcriber.load(model)
        val loadPeak = loadSampler.stopMb()
        Log.i(TAG, "model=${model.name} loaded=$loaded load=${transcriber.lastLoadMs} ms peakPss=$loadPeak MB")
        try {
            for (k in knobs) {
                val threads = if (k.threads > 0) k.threads else DeviceProfile.threadsFor(Runtime.getRuntime().availableProcessors())
                val setup = AsrSetup("whisper", model.name, k.label(threads))
                val options = WhisperOptions(modelFileName = model.name, threads = threads, audioCtx = k.audioCtx, beamSize = k.beam)
                clips.forEachIndexed { i, c ->
                    val row = if (!loaded) {
                        AsrResult(run, setup, c.clip, AsrTiming(c.durationMs, 0), "", error = "model_yuklenemedi")
                    } else {
                        decode(transcriber, setup, options, c)
                    }
                    Log.i(TAG, "${setup.label} ${i + 1}/${clips.size} ${c.clip.id} ${row.timing.processMs} ms ${row.error}")
                    onRow(row)
                }
            }
        } finally {
            transcriber.unload()
        }
    }

    private fun decode(t: WhisperTranscriber, setup: AsrSetup, options: WhisperOptions, c: ClipAudio): AsrResult {
        val cut = if (useVad) VadClipper.clip(c.samples) else null
        val vad = when {
            !useVad -> "ham"
            cut != null -> "kesildi"
            else -> "reddetti"
        }
        val input = cut ?: c.samples
        val sampler = PssSampler().also { it.start() }
        val result = t.transcribe(input, options)
        val peak = sampler.stopMb()
        val audioMs = input.size * 1000L / 16_000
        return if (result == null) {
            AsrResult(run, setup, c.clip, AsrTiming(audioMs, 0, t.lastLoadMs, peak, vad), "", error = "cozumleme_hatasi")
        } else {
            AsrResult(run, setup, c.clip, AsrTiming(result.audioMs, result.decodeMs, t.lastLoadMs, peak, vad), result.text)
        }
    }

    private companion object {
        const val TAG = "G13Asr"
    }
}
