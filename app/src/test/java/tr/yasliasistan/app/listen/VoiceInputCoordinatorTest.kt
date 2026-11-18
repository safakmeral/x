package tr.yasliasistan.app.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.recognition.FailureReason
import tr.yasliasistan.speech.recognition.ListenConfig
import tr.yasliasistan.speech.recognition.SpeechEngine
import tr.yasliasistan.speech.recognition.SpeechListener
import tr.yasliasistan.speech.recognition.SpeechOutcome

class VoiceInputCoordinatorTest {

    private val engine = FakeEngine()
    private val ui = RecordingUi()
    private var permission = true
    private val coordinator = VoiceInputCoordinator(engine, { permission }, ui)

    @Test
    fun microphoneNeverOpensWithoutATap() {
        coordinator.prepare()
        engine.answerCheck(EngineAvailability.AVAILABLE)
        assertEquals(0, engine.starts)
    }

    @Test
    fun tapAfterWarmUpStartsListeningImmediately() {
        coordinator.prepare()
        engine.answerCheck(EngineAvailability.AVAILABLE)
        coordinator.startTurn()
        assertEquals(1, engine.starts)
        engine.listener!!.onListening()
        assertEquals(listOf(MicIndicator.OPENING, MicIndicator.OPEN), ui.mic)
    }

    @Test
    fun heardTextIsHandedOn() {
        startListening()
        engine.finish(SpeechOutcome.Heard("Ayarları aç"))
        assertEquals(listOf("Ayarları aç"), ui.heard)
        assertEquals(listOf(false), ui.partialOnly)
        assertEquals(MicIndicator.CLOSED, ui.mic.last())
    }

    @Test
    fun partialOnlyMarkIsHandedOn() {
        startListening()
        engine.finish(SpeechOutcome.Heard("Ayarları aç", partialOnly = true))
        assertEquals(listOf(true), ui.partialOnly)
    }

    @Test
    fun emptyResultGivesNoCommand() {
        startListening()
        engine.finish(SpeechOutcome.NothingHeard)
        assertTrue(ui.heard.isEmpty())
        assertEquals(1, ui.nothingHeard)
        assertTrue(ui.notices.isEmpty())
    }

    @Test
    fun missingPermissionOpensSetupInsteadOfMicrophone() {
        permission = false
        coordinator.startTurn()
        assertEquals(0, engine.starts)
        assertEquals(listOf(VoiceNotice.NEEDS_MIC_PERMISSION), ui.notices)
        assertEquals(1, ui.setupOpened)
    }

    @Test
    fun missingTurkishPackWarnsAndNeverListens() {
        coordinator.startTurn()
        engine.answerCheck(EngineAvailability.LANGUAGE_NOT_INSTALLED)
        assertEquals(0, engine.starts)
        assertEquals(listOf(VoiceNotice.LANGUAGE_PACK_MISSING), ui.notices)
        assertEquals(1, ui.setupOpened)
        assertEquals(MicIndicator.CLOSED, ui.mic.last())
    }

    @Test
    fun oldAndroidOrNoOfflineRecognizerWarnsAndNeverListens() {
        listOf(
            EngineAvailability.ANDROID_TOO_OLD,
            EngineAvailability.NO_ON_DEVICE_RECOGNIZER,
            EngineAvailability.LANGUAGE_NOT_SUPPORTED,
        ).forEach { availability ->
            coordinator.startTurn()
            engine.answerCheck(availability)
        }
        assertEquals(0, engine.starts)
        assertEquals(List(3) { VoiceNotice.OFFLINE_NOT_SUPPORTED }, ui.notices)
    }

    @Test
    fun unavailableIsCheckedAgainOnNextTap() {
        coordinator.prepare()
        engine.answerCheck(EngineAvailability.LANGUAGE_NOT_INSTALLED)
        coordinator.startTurn() // user installed the pack meanwhile
        engine.answerCheck(EngineAvailability.AVAILABLE)
        assertEquals(1, engine.starts)
    }

    @Test
    fun languageErrorDuringListeningForgetsAvailability() {
        startListening()
        engine.finish(SpeechOutcome.Failed(FailureReason.LANGUAGE_UNAVAILABLE))
        assertEquals(listOf(VoiceNotice.LANGUAGE_PACK_MISSING), ui.notices)
        coordinator.startTurn()
        assertEquals("re-checks before opening the mic again", 1, engine.starts)
        assertEquals(2, engine.checks)
    }

    @Test
    fun stopDuringCheckKeepsMicrophoneClosed() {
        coordinator.startTurn()
        coordinator.stop()
        engine.answerCheck(EngineAvailability.AVAILABLE)
        assertEquals(0, engine.starts)
        assertEquals(MicIndicator.CLOSED, ui.mic.last())
    }

    @Test
    fun stopWhileListeningCancels() {
        startListening()
        coordinator.stop()
        assertEquals(1, engine.cancels)
        assertTrue(ui.heard.isEmpty())
        assertTrue(ui.notices.isEmpty())
    }

    @Test
    fun secondTapEndsTheTurnEarly() {
        assertFalse(coordinator.finishTurn())
        startListening()
        assertTrue(coordinator.finishTurn())
        coordinator.startTurn() // a running turn is never restarted
        assertEquals(1, engine.finishes)
        assertEquals(1, engine.starts)
    }

    @Test
    fun retryIsOneTap() {
        startListening()
        engine.finish(SpeechOutcome.NothingHeard)
        coordinator.startTurn()
        assertEquals(2, engine.starts)
    }

    @Test
    fun busyAndBlockedMicrophoneAreReported() {
        startListening()
        engine.finish(SpeechOutcome.Failed(FailureReason.BUSY))
        coordinator.startTurn()
        engine.finish(SpeechOutcome.Failed(FailureReason.AUDIO))
        assertEquals(listOf(VoiceNotice.MIC_BUSY, VoiceNotice.MIC_BLOCKED), ui.notices)
        assertFalse(ui.setupOpened > 0)
    }

    private fun startListening() {
        coordinator.prepare()
        engine.answerCheck(EngineAvailability.AVAILABLE)
        coordinator.startTurn()
        engine.listener!!.onListening()
    }

    private class FakeEngine : SpeechEngine {
        var starts = 0
        var finishes = 0
        var cancels = 0
        var checks = 0
        var listener: SpeechListener? = null
        private val pendingChecks = ArrayDeque<(EngineAvailability) -> Unit>()

        override fun checkAvailability(onResult: (EngineAvailability) -> Unit) {
            checks++
            pendingChecks += onResult
        }

        fun answerCheck(availability: EngineAvailability) = pendingChecks.removeFirst()(availability)

        override fun startListening(listener: SpeechListener, config: ListenConfig) {
            starts++
            this.listener = listener
        }

        fun finish(outcome: SpeechOutcome) = listener!!.onOutcome(outcome)

        override fun finishListening() { finishes++ }

        override fun cancel() {
            cancels++
            listener?.onOutcome(SpeechOutcome.Cancelled)
        }

        override fun release() = Unit
    }

    private class RecordingUi : VoiceInputUi {
        val mic = mutableListOf<MicIndicator>()
        val heard = mutableListOf<String>()
        val notices = mutableListOf<VoiceNotice>()
        var setupOpened = 0
        var nothingHeard = 0
        override fun showMic(state: MicIndicator) { mic += state }
        val partialOnly = mutableListOf<Boolean>()
        override fun onHeard(text: String, partialOnly: Boolean) { heard += text; this.partialOnly += partialOnly }
        override fun onNothingHeard() { nothingHeard++ }
        override fun showNotice(notice: VoiceNotice) { notices += notice }
        override fun openVoiceSetup() { setupOpened++ }
    }
}
