package tr.yasliasistan.speech.tts

/** What an engine said about Turkish (from `TextToSpeech.isLanguageAvailable`). */
enum class LanguageSupport { INSTALLED, MISSING_DATA, NOT_SUPPORTED }

/** Android-free copy of a `Voice` (unit testable). [language] is the ISO 639-1 code. */
data class VoiceInfo(
    val name: String,
    val language: String,
    val networkRequired: Boolean,
    val installed: Boolean,
    val quality: Int,
)

/**
 * Result of asking one engine about Turkish. [allVoicesKey] identifies the engine's full voice
 * list: if the requested engine cannot be bound (e.g. a private system engine), Android silently
 * falls back to another engine, and the same key then reveals the duplicate.
 */
data class EngineProbe(
    val enginePackage: String,
    val isDefault: Boolean,
    val language: LanguageSupport,
    val voices: List<VoiceInfo>,
    val allVoicesKey: String = "",
)

/**
 * Picks the engine and voice to use. Rules:
 * - Only named Turkish voices that are installed and need no network (text never leaves the device);
 *   an English voice is never used to read Turkish. No voice: no speech, text only.
 * - The phone's default engine is preferred, then Google's, then any other.
 * - If none is ready, point at an engine that can download Turkish data.
 */
object VoiceSelector {

    const val TURKISH = "tr"
    const val GOOGLE_ENGINE = "com.google.android.tts"

    fun bestOfflineVoice(probe: EngineProbe): VoiceInfo? =
        probe.voices
            .filter { it.language == TURKISH && it.installed && !it.networkRequired }
            .sortedWith(compareByDescending<VoiceInfo> { it.quality }.thenBy { it.name })
            .firstOrNull()

    /** True when [probe] alone can speak Turkish offline. */
    fun isReady(probe: EngineProbe): Boolean = readyStatus(probe) != null

    /**
     * Engines are probed in a fixed order (Google first). A later probe with the same non-empty
     * voice list as an earlier one is a silent fallback, not the engine it claims to be.
     */
    fun dropFallbacks(probes: List<EngineProbe>): List<EngineProbe> {
        val seen = mutableSetOf<String>()
        return probes.filter { it.allVoicesKey.isEmpty() || seen.add(it.allVoicesKey) }
    }

    fun choose(probes: List<EngineProbe>): VoiceStatus {
        if (probes.isEmpty()) return VoiceStatus.InitFailed
        val ordered = dropFallbacks(probes).sortedBy(::rank)
        ordered.firstNotNullOfOrNull(::readyStatus)?.let { return it }
        ordered.firstOrNull(::canDownloadTurkish)?.let { return VoiceStatus.MissingData(it.enginePackage) }
        return VoiceStatus.NotSupported
    }

    /**
     * Ready only with a named voice that is installed and needs no network. An engine that lists no
     * voices cannot prove it stays offline (it may pick a network voice), so it is not used (UD-1 Ö-5).
     */
    private fun readyStatus(probe: EngineProbe): VoiceStatus.Ready? =
        bestOfflineVoice(probe)?.let { VoiceStatus.Ready(probe.enginePackage, it.name) }

    /**
     * Engines the app may send sentences to: Google's, the one the user chose as phone default, and
     * engines shipped with the phone. Other third-party engines are skipped (UD-1 Ö-5).
     */
    fun isTrustedEngine(enginePackage: String, defaultPackage: String?, isSystemApp: Boolean): Boolean =
        enginePackage == GOOGLE_ENGINE || enginePackage == defaultPackage || isSystemApp

    private fun canDownloadTurkish(probe: EngineProbe): Boolean =
        probe.language != LanguageSupport.NOT_SUPPORTED || probe.voices.any { it.language == TURKISH }

    private fun rank(probe: EngineProbe): Int = when {
        probe.isDefault -> 0
        probe.enginePackage == GOOGLE_ENGINE -> 1
        else -> 2
    }
}
