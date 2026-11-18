package tr.yasliasistan.speech.recognition

import tr.yasliasistan.speech.whisper.WhisperModelCatalog
import tr.yasliasistan.speech.whisper.WhisperModelSpec

/** Which on-device recognizer listens. */
enum class EngineKind { SYSTEM, WHISPER }

/** The chosen engine; [whisperModel] is set only for [EngineKind.WHISPER]. */
data class EngineChoice(val kind: EngineKind, val whisperModel: WhisperModelSpec? = null)

/** What the policy needs to know about the phone. Pure data, filled by [SelectedSpeechEngine]. */
data class DeviceFacts(
    val sdkInt: Int,
    /** Result of the system recognizer's check; null when not checked yet. */
    val systemAvailability: EngineAvailability?,
    /** The system recognizer said "unknown" but then failed to listen offline in Turkish. */
    val systemFailedOffline: Boolean,
    /** The whisper.cpp library loaded (arm64 phones only). */
    val whisperRuns: Boolean,
    /** ActivityManager.MemoryInfo.totalMem; 0 when unknown. */
    val totalRamBytes: Long,
)

/**
 * The ONE place that decides the speech engine (G-15, decision of 17.11.2026, docs/SAPMALAR.md).
 * If the G-14 measurement is run later and changes the choice, only these defaults change.
 *
 * Rule: Android 12+ with offline Turkish (installed, installable or not reported) -> the system's
 * offline recognizer (faster; Whisper tiny was weak in Turkish in G-12). Otherwise Whisper: base,
 * or tiny on phones with less than [lowRamBytes] memory. Online recognition is never an option
 * (NFG-17). Pure logic, JVM-testable.
 */
data class EnginePolicy(
    /** Android 12: first version with a guaranteed-offline system recognizer (PLAN S-03). */
    val minSystemSdk: Int = 31,
    val lowRamBytes: Long = 3L * 1024 * 1024 * 1024,
    val defaultModel: WhisperModelSpec = WhisperModelCatalog.BASE,
    val lowRamModel: WhisperModelSpec = WhisperModelCatalog.TINY,
    /** Developer/measurement override; ignored where that engine cannot run offline. */
    val override: EngineKind? = null,
) {

    fun choose(facts: DeviceFacts): EngineChoice {
        val systemOk = systemUsable(facts)
        val whisper = EngineChoice(EngineKind.WHISPER, modelFor(facts.totalRamBytes))
        val system = EngineChoice(EngineKind.SYSTEM)
        return when {
            override == EngineKind.WHISPER && facts.whisperRuns -> whisper
            override == EngineKind.SYSTEM && systemOk -> system
            systemOk -> system
            facts.whisperRuns -> whisper
            // Nothing works offline: the system engine reports why and the setup screen explains it.
            else -> system
        }
    }

    /** Unknown memory (0) counts as low: the smaller model is the safe side on a weak phone. */
    fun modelFor(totalRamBytes: Long): WhisperModelSpec =
        if (totalRamBytes < lowRamBytes) lowRamModel else defaultModel

    private fun systemUsable(facts: DeviceFacts): Boolean {
        if (facts.sdkInt < minSystemSdk || facts.systemFailedOffline) return false
        return when (facts.systemAvailability) {
            null,
            EngineAvailability.AVAILABLE,
            EngineAvailability.UNKNOWN,
            EngineAvailability.LANGUAGE_NOT_INSTALLED,
            EngineAvailability.LANGUAGE_DOWNLOADING -> true
            EngineAvailability.ANDROID_TOO_OLD,
            EngineAvailability.NO_ON_DEVICE_RECOGNIZER,
            EngineAvailability.LANGUAGE_NOT_SUPPORTED -> false
        }
    }
}
