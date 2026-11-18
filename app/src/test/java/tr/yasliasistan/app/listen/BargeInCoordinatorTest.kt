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

/** G-16: listening while the assistant speaks, inside VoiceInputCoordinator. */
class BargeInCoordinatorTest {

    private val question = "Google Haritalar uygulamasını açayım mı?"
    private val engine = FakeEngine()
    private val ui = RecordingUi()
    private var permission = true
    private val coordinator = VoiceInputCoordinator(engine, { permission }, ui)

    private fun ready() {
        coordinator.prepare()
        engine.answerCheck(EngineAvailability.AVAILABLE)
    }

    private fun startBargeIn(): Boolean = coordinator.startBargeInTurn { question }.also {
        if (it) engine.listener!!.onListening()
    }

    @Test
    fun opensWithEchoCancellingConfigAndShowsTheIndicator() {
        ready()
        assertTrue(startBargeIn())
        assertTrue(engine.configs.last().duringSpeech)
        assertEquals(MicIndicator.OPEN, ui.mic.last())
    }

    @Test
    fun neverAsksForSetupOrChecksDuringSpeech() {
        assertFalse("availability unknown", coordinator.startBargeInTurn { question })
        permission = false
        ready()
        assertFalse(coordinator.startBargeInTurn { question })
        assertEquals(0, engine.starts)
        assertTrue(ui.notices.isEmpty())
        assertEquals(0, ui.setupOpened)
    }

    @Test
    fun ownVoiceDoesNotInterrupt() {
        ready()
        startBargeIn()
        engine.listener!!.onPartialText("google haritalar")
        engine.listener!!.onPartialText("google haritalar uygulamasını açayım")
        assertEquals(0, ui.interruptions)
    }

    @Test
    fun userWordInterruptsAtOnceAndTheTurnBecomesTheAnswer() {
        ready()
        startBargeIn()
        engine.listener!!.onPartialText("hayır")
        assertEquals(1, ui.interruptions)
        coordinator.startTurn() // the dialog asks to listen after the cut: the running turn is kept
        assertEquals(1, engine.starts)
        assertEquals(0, engine.cancels)
        engine.finish(SpeechOutcome.Heard("hayır"))
        assertEquals(listOf("hayır"), ui.heard)
    }

    @Test
    fun engineVoiceSignalInterruptsOnce() {
        ready()
        startBargeIn()
        engine.listener!!.onUserVoice()
        engine.listener!!.onUserVoice()
        assertEquals(1, ui.interruptions)
    }

    @Test
    fun unusedTurnIsDroppedAndAFreshOneOpensAfterTheQuestion() {
        ready()
        startBargeIn()
        engine.listener!!.onPartialText("google haritalar") // echo only
        coordinator.startTurn()
        assertEquals(1, engine.cancels)
        assertEquals(2, engine.starts)
        assertFalse(engine.configs.last().duringSpeech)
        assertTrue("old turn's outcome ignored", ui.heard.isEmpty() && ui.nothingHeard == 0)
    }

    @Test
    fun echoInTheFinalTextIsRemovedOrDropped() {
        ready()
        startBargeIn()
        engine.finish(SpeechOutcome.Heard("Google Haritalar uygulamasını açayım mı")) // only echo
        assertTrue(ui.heard.isEmpty())
        assertEquals("speech goes on, nothing reported", 0, ui.nothingHeard)

        startBargeIn()
        engine.finish(SpeechOutcome.Heard("Google Haritalar uygulamasını evet"))
        assertEquals(listOf("evet"), ui.heard)
        assertEquals(1, ui.interruptions)
    }

    @Test
    fun failuresAreSilentAndSwitchBargeInOff() {
        ready()
        repeat(VoiceInputCoordinator.MAX_BARGE_IN_FAILURES) {
            assertTrue(coordinator.startBargeInTurn { question })
            engine.finish(SpeechOutcome.Failed(FailureReason.BUSY))
        }
        assertFalse(coordinator.startBargeInTurn { question })
        assertTrue(ui.notices.isEmpty())
        coordinator.startTurn() // normal listening still works
        assertEquals(MicIndicator.OPENING, ui.mic.last())
    }

    @Test
    fun stopClosesTheBargeInTurn() {
        ready()
        startBargeIn()
        coordinator.stop()
        assertEquals(1, engine.cancels)
        assertEquals(MicIndicator.CLOSED, ui.mic.last())
        assertTrue(ui.heard.isEmpty())
    }

    @Test
    fun tapDuringSpeechDoesNotEndTheTurnAsAnAnswer() {
        ready()
        startBargeIn()
        assertFalse(coordinator.finishTurn())
        assertEquals(0, engine.finishes)
    }

    private class FakeEngine : SpeechEngine {
        var starts = 0
        var finishes = 0
        var cancels = 0
        var listener: SpeechListener? = null
        val configs = mutableListOf<ListenConfig>()
        private val pendingChecks = ArrayDeque<(EngineAvailability) -> Unit>()

        override fun checkAvailability(onResult: (EngineAvailability) -> Unit) {
            pendingChecks += onResult
        }

        fun answerCheck(availability: EngineAvailability) = pendingChecks.removeFirst()(availability)

        override fun startListening(listener: SpeechListener, config: ListenConfig) {
            starts++
            configs += config
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
        var interruptions = 0
        override fun showMic(state: MicIndicator) { mic += state }
        override fun onHeard(text: String, partialOnly: Boolean) { heard += text }
        override fun onNothingHeard() { nothingHeard++ }
        override fun onUserInterrupted() { interruptions++ }
        override fun showNotice(notice: VoiceNotice) { notices += notice }
        override fun openVoiceSetup() { setupOpened++ }
    }
}
