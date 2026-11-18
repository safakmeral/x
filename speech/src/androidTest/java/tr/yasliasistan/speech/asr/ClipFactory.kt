package tr.yasliasistan.speech.asr

import tr.yasliasistan.core.asr.AsrClip
import tr.yasliasistan.core.asr.AsrCommandSet
import tr.yasliasistan.core.asr.AsrManifest
import tr.yasliasistan.core.asr.AsrRunConfig
import tr.yasliasistan.core.asr.AudioMix
import tr.yasliasistan.speech.whisper.WavAudio
import java.io.File

/** A clip ready for measurement: 16 kHz mono floats. Lives in memory only. */
class ClipAudio(val clip: AsrClip, val samples: FloatArray) {
    val durationMs: Long get() = samples.size * 1000L / WavAudio.WHISPER_RATE
}

/**
 * Builds measurement clips (G-13).
 *  - TTS: each test command spoken by the phone's offline Turkish voice; "temiz" = speech with a
 *    quiet room floor, "gurultulu" = plus background talk (three other commands, pitch-shifted, at
 *    [AsrRunConfig.snrDb]) as a stand-in for TV noise, "sessiz" = room noise only (hallucination test).
 *  - Folder: WAV files listed in manifest.tsv (e.g. human recordings, NFG-19), measured as they are.
 * TTS speech is NOT elderly speech: results show relative engine speed/accuracy, not real-world WER.
 */
class ClipFactory(private val tts: TurkishTtsSynth?) {

    private val speechCache = HashMap<Int, FloatArray>()

    fun ttsClips(config: AsrRunConfig): List<ClipAudio> {
        val synth = tts ?: return emptyList()
        val out = mutableListOf<ClipAudio>()
        val commands = AsrCommandSet.COMMANDS
        for (i in 0 until config.limit) {
            val speech = speech(synth, i)
            if (speech.isEmpty()) continue
            val padded = AudioMix.pad(speech, before = LEAD_SAMPLES, after = TAIL_SAMPLES)
            val room = AudioMix.roomNoise(padded.size, ROOM_DBFS, seed = i)
            val clean = add(padded, room)
            val command = commands[i]
            if (AsrRunConfig.CLEAN in config.conditions) {
                out += ClipAudio(AsrClip(command.id, SOURCE_TTS, AsrRunConfig.CLEAN, command.text), clean)
            }
            if (AsrRunConfig.NOISY in config.conditions) {
                val babble = AudioMix.babble(babbleSources(synth, i, commands.size), padded.size)
                val noisy = AudioMix.mixAtSnr(clean, babble, config.snrDb, speechLevel = speech)
                out += ClipAudio(AsrClip("${command.id}g", SOURCE_TTS, AsrRunConfig.NOISY, command.text), noisy)
            }
        }
        if (AsrRunConfig.SILENT in config.conditions) out += silentClips(config.silentClips)
        return out
    }

    /** Clips listed in [dir]/manifest.tsv; problems (bad lines, unreadable WAVs) go to [problems]. */
    fun folderClips(dir: File, problems: MutableList<String>): List<ClipAudio> {
        val manifest = File(dir, MANIFEST)
        if (!manifest.isFile) {
            problems += "klasör verisi yok: ${dir.name}/$MANIFEST"
            return emptyList()
        }
        val (entries, parseProblems) = AsrManifest.parse(manifest.readText())
        problems += parseProblems
        return entries.mapNotNull { e ->
            try {
                val pcm = WavAudio.parse(File(dir, e.file).readBytes())
                ClipAudio(AsrClip(e.id, SOURCE_FOLDER, e.condition, e.reference), WavAudio.toWhisperInput(pcm))
            } catch (ex: Exception) {
                problems += "${e.file}: okunamadı (${ex.javaClass.simpleName})"
                null
            }
        }
    }

    private fun silentClips(count: Int): List<ClipAudio> = (0 until count).map { i ->
        val seconds = 2 + i % 3
        val level = SILENT_LEVELS[i % SILENT_LEVELS.size]
        val samples = AudioMix.roomNoise(seconds * WavAudio.WHISPER_RATE, level, seed = 1_000 + i)
        val id = "s" + (i + 1).toString().padStart(2, '0')
        ClipAudio(AsrClip(id, SOURCE_TTS, AsrRunConfig.SILENT, ""), samples)
    }

    private fun speech(synth: TurkishTtsSynth, index: Int): FloatArray =
        speechCache.getOrPut(index) { synth.synthesize(AsrCommandSet.COMMANDS[index].text) }

    /** Three other commands, resampled so they sound like different (higher/lower) voices. */
    private fun babbleSources(synth: TurkishTtsSynth, index: Int, total: Int): List<FloatArray> =
        BABBLE_OFFSETS.mapIndexed { k, offset ->
            val other = speech(synth, (index + offset) % total)
            WavAudio.resample(other, WavAudio.WHISPER_RATE, (WavAudio.WHISPER_RATE * PITCH[k]).toInt())
        }

    private fun add(a: FloatArray, b: FloatArray) = FloatArray(a.size) { (a[it] + b[it]).coerceIn(-1f, 1f) }

    companion object {
        const val SOURCE_TTS = "tts"
        const val SOURCE_FOLDER = "kayit"
        const val MANIFEST = "manifest.tsv"
        private const val LEAD_SAMPLES = WavAudio.WHISPER_RATE / 2
        private const val TAIL_SAMPLES = WavAudio.WHISPER_RATE
        private const val ROOM_DBFS = -60.0
        private val SILENT_LEVELS = listOf(-65.0, -55.0, -45.0)
        private val BABBLE_OFFSETS = listOf(10, 20, 30)
        // Resampling to fewer samples and playing at 16 kHz raises the pitch, and vice versa.
        private val PITCH = listOf(0.85, 1.15, 0.95)
    }
}
