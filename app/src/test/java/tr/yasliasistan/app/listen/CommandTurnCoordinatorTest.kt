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

/** G-17: command-only listening while the assistant works or says an information sentence. */
class CommandTurnCoordinatorTest {

    private val said = "Ayarlar uygulamasını açıyorum."
    private val engine = FakeEngine()
    private val ui = RecordingUi()
    private var permission = true
    private val coordinator = VoiceInputCoordinator(engine, { permission }, ui)

    private fun ready() {
        coordinator.prepare()
        engine.answerCheck(EngineAvailability.AVAILABLE)
    }

    private fun startCommands(): Boolean = coordinator.startCommandTurn { said }.also {
        if (it) engine.listener!!.onListening()
    }

    @Test
    fun opensSilentlyWithEchoConfigAndVisibleIndicator() {
        ready()
        assertTrue(startCommands())
        assertTrue(engine.configs.last().duringSpeech)
        assertEquals(MicIndicator.OPEN, ui.mic.last())
        assertTrue("already listening for commands", coordinator.startCommandTurn { said })
        assertEquals(1, engine.starts)
    }

    @Test
    fun neverOpensWithoutPermissionOrModel() {
        assertFalse(coordinator.startCommandTurn { said })
        permission = false
        ready()
        assertFalse(coordinator.startCommandTurn { said })
        assertEquals(0, engine.starts)
        assertTrue(ui.notices.isEmpty())
    }

    @Test
    fun stopActsOnTheLiveTextAtOnce() {
        ready()
        startCommands()
        engine.listener!!.onPartialText("dur")
        assertEquals(listOf("dur"), ui.heard)
        assertEquals(1, engine.cancels)
        assertEquals(MicIndicator.CLOSED, ui.mic.last())
        engine.finish(SpeechOutcome.Heard("dur")) // late outcome of the dropped turn
        assertEquals(1, ui.heard.size)
    }

    @Test
    fun ownVoiceAndOtherWordsNeverReachTheDialog() {
        ready()
        startCommands()
        engine.listener!!.onPartialText("ayarlar uygulamasını")
        engine.listener!!.onUserVoice()
        assertEquals(0, ui.interruptions)
        engine.finish(SpeechOutcome.Heard("ayarlar uygulamasını açıyorum"))
        assertTrue(ui.heard.isEmpty())
        assertEquals(1, ui.nothingHeard)

        startCommands()
        engine.finish(SpeechOutcome.Heard("bugün hava çok güzel"))
        assertTrue(ui.heard.isEmpty())
        assertEquals(2, ui.nothingHeard)
    }

    @Test
    fun commandInTheFinalTextIsPassedOn() {
        ready()
        startCommands()
        engine.listener!!.onPartialText("tekrar") // not "dur": waits for the final text
        assertTrue(ui.heard.isEmpty())
        engine.finish(SpeechOutcome.Heard("ayarlar uygulamasını tekrar söyle")) // echo, then the user
        assertEquals(listOf("tekrar söyle"), ui.heard)
    }

    @Test
    fun aQuestionOrANormalTurnReplacesTheCommandTurn() {
        ready()
        startCommands()
        assertTrue(coordinator.startBargeInTurn { "Hangisini açayım?" })
        assertEquals(1, engine.cancels)
        assertEquals(2, engine.starts)

        coordinator.stop()
        startCommands()
        coordinator.startTurn()
        assertEquals(4, engine.starts)
        assertFalse(engine.configs.last().duringSpeech)
    }

    @Test
    fun failuresAreSilentAndSwitchItOff() {
        ready()
        repeat(VoiceInputCoordinator.MAX_BARGE_IN_FAILURES) {
            coordinator.startCommandTurn { said }
            engine.finish(SpeechOutcome.Failed(FailureReason.AUDIO))
        }
        assertFalse(coordinator.startCommandTurn { said })
        assertTrue(ui.notices.isEmpty())
        assertEquals(0, ui.nothingHeard) // no loop of retries
    }

    @Test
    fun tapDoesNotEndACommandTurn() {
        ready()
        startCommands()
        assertFalse(coordinator.finishTurn())
    }

    private class FakeEngine : SpeechEngine {
        var starts = 0
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

        override fun finishListening() = Unit

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
        var nothingHeard = 0
        var interruptions = 0
        override fun showMic(state: MicIndicator) { mic += state }
        override fun onHeard(text: String, partialOnly: Boolean) { heard += text }
        override fun onNothingHeard() { nothingHeard++ }
        override fun onUserInterrupted() { interruptions++ }
        override fun showNotice(notice: VoiceNotice) { notices += notice }
        override fun openVoiceSetup() = Unit
    }
}
