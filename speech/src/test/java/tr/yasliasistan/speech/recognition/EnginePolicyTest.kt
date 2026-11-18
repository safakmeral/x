package tr.yasliasistan.speech.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.speech.whisper.WhisperModelCatalog

class EnginePolicyTest {

    private val policy = EnginePolicy()
    private val gib = 1024L * 1024 * 1024

    private fun facts(
        sdk: Int = 36,
        system: EngineAvailability? = EngineAvailability.AVAILABLE,
        systemFailed: Boolean = false,
        whisperRuns: Boolean = true,
        ram: Long = 4 * gib,
    ) = DeviceFacts(sdk, system, systemFailed, whisperRuns, ram)

    @Test
    fun android12WithTurkishUsesSystemRecognizer() {
        val choice = policy.choose(facts(sdk = 31))
        assertEquals(EngineKind.SYSTEM, choice.kind)
        assertNull(choice.whisperModel)
    }

    @Test
    fun systemStaysWhenTheTurkishPackCanStillBeInstalled() {
        listOf(
            EngineAvailability.LANGUAGE_NOT_INSTALLED,
            EngineAvailability.LANGUAGE_DOWNLOADING,
            EngineAvailability.UNKNOWN,
            null, // not checked yet: try the system first (fast path)
        ).forEach {
            assertEquals("$it", EngineKind.SYSTEM, policy.choose(facts(system = it)).kind)
        }
    }

    @Test
    fun android10And11UseWhisper() {
        listOf(29, 30).forEach { sdk ->
            val choice = policy.choose(facts(sdk = sdk, system = EngineAvailability.ANDROID_TOO_OLD))
            assertEquals(EngineKind.WHISPER, choice.kind)
            assertEquals(WhisperModelCatalog.BASE, choice.whisperModel)
        }
    }

    @Test
    fun noOfflineTurkishOnTheSystemUsesWhisper() {
        listOf(EngineAvailability.NO_ON_DEVICE_RECOGNIZER, EngineAvailability.LANGUAGE_NOT_SUPPORTED).forEach {
            assertEquals("$it", EngineKind.WHISPER, policy.choose(facts(system = it)).kind)
        }
    }

    @Test
    fun systemThatFailedOfflineAtRunTimeIsReplacedByWhisper() {
        val choice = policy.choose(facts(system = EngineAvailability.UNKNOWN, systemFailed = true))
        assertEquals(EngineKind.WHISPER, choice.kind)
    }

    @Test
    fun lowRamPhoneGetsTinyModel() {
        val choice = policy.choose(facts(sdk = 30, system = null, ram = 2_800_000_000L))
        assertEquals(WhisperModelCatalog.TINY, choice.whisperModel)
    }

    @Test
    fun fourGigabytePhoneReportingLessThanFourGetsBase() {
        // A "4 GB" phone such as the Galaxy A15 reports about 3.6 GB total memory.
        val choice = policy.choose(facts(sdk = 30, system = null, ram = 3_600_000_000L))
        assertEquals(WhisperModelCatalog.BASE, choice.whisperModel)
    }

    @Test
    fun unknownRamCountsAsLowRam() {
        val choice = policy.choose(facts(sdk = 29, system = null, ram = 0L))
        assertEquals(WhisperModelCatalog.TINY, choice.whisperModel)
    }

    @Test
    fun withoutWhisperTheSystemReportsItsOwnProblem() {
        // x86 emulator or a non-arm64 phone: no Whisper library, so the system explains what is missing.
        val choice = policy.choose(facts(sdk = 30, system = EngineAvailability.ANDROID_TOO_OLD, whisperRuns = false))
        assertEquals(EngineKind.SYSTEM, choice.kind)
    }

    @Test
    fun overrideToWhisperIsHonouredWhenWhisperRuns() {
        val forced = EnginePolicy(override = EngineKind.WHISPER)
        assertEquals(EngineKind.WHISPER, forced.choose(facts()).kind)
        assertEquals(EngineKind.SYSTEM, forced.choose(facts(whisperRuns = false)).kind)
    }

    @Test
    fun overrideToSystemIsIgnoredWhereTheSystemCannotBeOffline() {
        val forced = EnginePolicy(override = EngineKind.SYSTEM)
        assertEquals(EngineKind.SYSTEM, forced.choose(facts()).kind)
        assertEquals(EngineKind.WHISPER, forced.choose(facts(sdk = 30, system = EngineAvailability.ANDROID_TOO_OLD)).kind)
    }

    @Test
    fun thresholdsAreConfigurable() {
        val custom = EnginePolicy(lowRamBytes = 5 * gib, defaultModel = WhisperModelCatalog.TINY)
        assertEquals(WhisperModelCatalog.TINY, custom.choose(facts(sdk = 30, system = null, ram = 4 * gib)).whisperModel)
        val noSystem = EnginePolicy(minSystemSdk = Int.MAX_VALUE)
        assertEquals(EngineKind.WHISPER, noSystem.choose(facts()).kind)
    }
}
