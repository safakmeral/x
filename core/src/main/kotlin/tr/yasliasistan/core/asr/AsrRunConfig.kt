package tr.yasliasistan.core.asr

/**
 * Settings of one measurement run, read from instrumentation arguments (`am instrument -e key value`).
 * Unknown or broken values fall back to defaults and are reported in [problems].
 */
data class AsrRunConfig(
    /** "system" and/or Whisper model file names (e.g. "ggml-tiny.bin"). */
    val engines: List<String> = listOf(TINY),
    /** Synthesized clips from the phone's Turkish TTS voice. */
    val useTts: Boolean = true,
    /** Recorded clips listed in [dataDir]/manifest.tsv. */
    val useFolder: Boolean = false,
    val conditions: List<String> = listOf(CLEAN, NOISY, SILENT),
    /** How many of the 40 commands to synthesize (from the start of the list). */
    val limit: Int = AsrCommandSet.COMMANDS.size,
    val silentClips: Int = 5,
    val snrDb: Double = 5.0,
    val audioCtx: List<Int> = listOf(0),
    val threads: List<Int> = listOf(0),
    val beams: List<Int> = listOf(1),
    /** Cut speech clips with the app's VAD first, as the live engine does. */
    val vad: Boolean = true,
    val dataDir: String = "asr-veri",
    /** Also write the synthesized clips + manifest into [dataDir] (to re-run in folder mode). */
    val exportClips: Boolean = false,
    val run: String = "",
    val problems: List<String> = emptyList(),
) {
    /** Every combination of the Whisper knobs (system recognizer ignores them). */
    fun whisperSettings(): List<WhisperKnobs> =
        audioCtx.flatMap { ctx -> threads.flatMap { th -> beams.map { WhisperKnobs(ctx, th, it) } } }

    data class WhisperKnobs(val audioCtx: Int, val threads: Int, val beam: Int) {
        fun label(resolvedThreads: Int) = "ctx=$audioCtx th=$resolvedThreads beam=$beam"
    }

    companion object {
        const val SYSTEM = "system"
        const val TINY = "ggml-tiny.bin"
        const val CLEAN = "temiz"
        const val NOISY = "gurultulu"
        const val SILENT = "sessiz"

        private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")

        fun parse(args: Map<String, String>): AsrRunConfig {
            val problems = mutableListOf<String>()
            val d = AsrRunConfig()

            fun ints(key: String, default: List<Int>, range: IntRange): List<Int> {
                val raw = args[key] ?: return default
                val values = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it.toIntOrNull() }
                if (values.isEmpty() || values.any { it == null || it !in range }) {
                    problems += "$key='$raw' geçersiz, varsayılan kullanıldı"
                    return default
                }
                return values.filterNotNull().distinct()
            }

            val engines = args["engines"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.mapNotNull { name ->
                    val resolved = engineName(name)
                    if (resolved == null) problems += "motor '$name' tanınmadı"
                    resolved
                }?.distinct()?.ifEmpty { null } ?: d.engines

            val data = args["data"]?.trim() ?: "tts"
            val (tts, folder) = when (data) {
                "tts" -> true to false
                "klasor" -> false to true
                "hepsi" -> true to true
                else -> { problems += "data='$data' geçersiz (tts|klasor|hepsi)"; true to false }
            }
            val conditions = args["conditions"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.filter { (it in listOf(CLEAN, NOISY, SILENT)).also { ok -> if (!ok) problems += "koşul '$it' tanınmadı" } }
                ?.ifEmpty { null } ?: d.conditions
            val dataDir = args["dataDir"]?.trim()?.takeIf { safeName.matches(it) && ".." !in it }
                ?: d.dataDir.also { if (args.containsKey("dataDir")) problems += "dataDir geçersiz" }

            return AsrRunConfig(
                engines = engines,
                useTts = tts,
                useFolder = folder,
                conditions = conditions,
                limit = ints("limit", listOf(d.limit), 1..AsrCommandSet.COMMANDS.size).first(),
                silentClips = ints("silent", listOf(d.silentClips), 0..50).first(),
                snrDb = ints("snr", listOf(d.snrDb.toInt()), -10..40).first().toDouble(),
                audioCtx = ints("audioCtx", d.audioCtx, 0..1500),
                threads = ints("threads", d.threads, 0..16),
                beams = ints("beam", d.beams, 1..8),
                vad = args["vad"]?.trim() != "0",
                dataDir = dataDir,
                exportClips = args["export"]?.trim() == "1",
                run = args["run"]?.trim()?.takeIf { safeName.matches(it) } ?: "",
                problems = problems,
            )
        }

        /** "system", a short model name ("tiny", "base-q5_1") or a ggml file name; null if unusable. */
        fun engineName(name: String): String? = when {
            name == SYSTEM -> SYSTEM
            !safeName.matches(name) || ".." in name -> null
            name.endsWith(".bin") -> name
            else -> "ggml-$name.bin"
        }
    }
}
