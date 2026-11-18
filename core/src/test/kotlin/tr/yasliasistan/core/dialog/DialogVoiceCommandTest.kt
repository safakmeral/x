package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.dialog.DialogEffect.CancelSpeech
import tr.yasliasistan.core.dialog.DialogEffect.CancelWork
import tr.yasliasistan.core.dialog.DialogEffect.Decide
import tr.yasliasistan.core.dialog.DialogEffect.Explain
import tr.yasliasistan.core.dialog.DialogEffect.ListenForCommands
import tr.yasliasistan.core.dialog.DialogEffect.SessionEnded
import tr.yasliasistan.core.dialog.DialogEffect.Speak

/** G-17: fixed voice commands in every session state (FG-12, FG-13, FG-23). */
class DialogVoiceCommandTest {

    private val machine = DialogMachine()
    private val log = mutableListOf<DialogEffect>()

    private fun send(event: DialogEvent) = machine.handle(event).also { log += it }

    private fun said(effects: List<DialogEffect>) = effects.filterIsInstance<Speak>().map { it.utterance }
    private fun text(s: String) = Utterance.Text(s)
    private fun template(p: Phrase) = Utterance.Template(p)

    private fun toProcessing(): Int {
        send(DialogEvent.BubbleTapped)
        send(DialogEvent.Heard("haritaları aç"))
        return log.filterIsInstance<Decide>().last().requestId
    }

    private fun toReadingBack(): Int = toProcessing().also { send(DialogEvent.Understood(it, "Haritaları açayım mı?")) }

    private fun toAwaiting(): Int = toReadingBack().also {
        send(DialogEvent.SpeechFinished(log.filterIsInstance<Speak>().last().utteranceId))
    }

    private fun toWorking(): Int = toProcessing().also { send(DialogEvent.Proceeding(it, "Ayarları açıyorum.")) }

    private fun toReplying(): Int = toProcessing().also { send(DialogEvent.Replied(it, "Hangisini açayım?", expectsAnswer = true)) }

    // --- Listening for commands while working (bilgi cümleleri sırasında) ---

    @Test
    fun processingAndWorkingListenForCommands() {
        val id = toProcessing()
        assertTrue(log.last() is ListenForCommands)
        log.clear()
        send(DialogEvent.Proceeding(id, "Ayarları açıyorum."))
        assertTrue(log.contains(ListenForCommands))
    }

    @Test
    fun nonCommandOrSilenceWhileWorkingListensAgain() {
        toWorking()
        assertEquals(listOf(ListenForCommands), send(DialogEvent.ListenFailed))
        assertEquals(listOf(ListenForCommands), send(DialogEvent.Heard("bugün hava güzel")))
        assertTrue(machine.state is DialogState.Working)
    }

    // --- "Dur" ---

    @Test
    fun stopWhileWorkingCancelsTheWorkAtOnce() {
        val id = toWorking()
        val out = send(DialogEvent.Heard("dur"))
        assertEquals(CancelSpeech, out.first())
        assertTrue(out.contains(CancelWork(id)))
        assertTrue(out.contains(SessionEnded))
        assertEquals(listOf(template(Phrase.STOPPED)), said(out))
        assertEquals(DialogState.Idle, machine.state)
        // A late result of the stopped work does nothing (FG-23).
        assertTrue(send(DialogEvent.ExecutionSucceeded(id, "Açıldı.")).isEmpty())
    }

    @Test
    fun stopWhileDecidingCancelsTheDecision() {
        val id = toProcessing()
        val out = send(DialogEvent.Heard("Dur!", partialOnly = true))
        assertTrue(out.contains(CancelWork(id)))
        assertEquals(DialogState.Idle, machine.state)
    }

    @Test
    fun stopWhileListeningOrReplyingEndsTheSession() {
        send(DialogEvent.BubbleTapped)
        send(DialogEvent.Heard("dur"))
        assertEquals(DialogState.Idle, machine.state)
        assertTrue(log.none { it is Decide }) // never reaches the rules or the cloud

        log.clear()
        toReplying()
        send(DialogEvent.Heard("yeter"))
        assertEquals(DialogState.Idle, machine.state)
    }

    @Test
    fun stopDuringYesNoQuestionIsTheNoAnswer() {
        toAwaiting()
        val out = send(DialogEvent.Heard("dur"))
        assertTrue(out.none { it is DialogEffect.Execute })
        assertEquals(listOf(template(Phrase.CANCELLED)), said(out))
    }

    // --- "Tamam, hatırladım" ---

    @Test
    fun rememberedSaysClosingAndStopsInEveryState() {
        val starts = listOf<() -> Unit>(
            { send(DialogEvent.BubbleTapped) }, { toProcessing() }, { toReadingBack() }, { toAwaiting() },
            { toWorking() }, { toReplying() },
        )
        for (start in starts) {
            log.clear()
            start()
            val out = send(DialogEvent.Heard("Tamam, hatırladım."))
            assertEquals(listOf(template(Phrase.CLOSING)), said(out))
            assertTrue(out.none { it is DialogEffect.Execute })
            assertTrue(out.contains(SessionEnded))
            assertFalse(machine.state.sessionOpen)
            send(DialogEvent.SpeechFinished(out.filterIsInstance<Speak>().last().utteranceId))
        }
    }

    // --- "Tekrar söyle" / "baştan anlat" ---

    @Test
    fun repeatDuringReadbackReadsTheQuestionAgain() {
        val id = toReadingBack()
        val out = send(DialogEvent.Heard("tekrar söyle"))
        assertEquals(CancelSpeech, out.first())
        assertEquals(listOf(text("Haritaları açayım mı?")), said(out))
        assertTrue(out.last() is DialogEffect.ListenWhileSpeaking)
        assertEquals(id, (machine.state as DialogState.ReadingBack).requestId)
    }

    @Test
    fun restartWhileAwaitingAsksAgainAndRestartsTheTimeout() {
        toAwaiting()
        val out = send(DialogEvent.Heard("baştan anlat"))
        assertTrue(out.contains(DialogEffect.StopListening))
        assertTrue(out.contains(DialogEffect.CancelTimer))
        assertEquals(listOf(text("Haritaları açayım mı?")), said(out))
        assertTrue(machine.state is DialogState.ReadingBack)
        // Still needs an explicit yes afterwards.
        val after = send(DialogEvent.Heard("evet"))
        assertTrue(after.any { it is DialogEffect.Execute })
    }

    @Test
    fun repeatAfterConfirmAgainReadsTheRealQuestion() {
        toAwaiting()
        send(DialogEvent.Heard("bilmem"))
        val out = send(DialogEvent.Heard("ne dedin"))
        assertEquals(listOf(text("Haritaları açayım mı?")), said(out))
        assertEquals(1, (machine.state as DialogState.ReadingBack).unclearAnswers) // no extra chance gained
    }

    @Test
    fun repeatWhileListeningForTheAnswerAsksTheReplyAgain() {
        val id = toReplying()
        send(DialogEvent.SpeechFinished(log.filterIsInstance<Speak>().last().utteranceId))
        assertEquals(DialogState.Listening, machine.state)
        val out = send(DialogEvent.Heard("tekrar söyler misin"))
        assertEquals(listOf(text("Hangisini açayım?")), said(out))
        assertEquals(id, (machine.state as DialogState.Replying).requestId)
        assertTrue(out.none { it is Decide })
    }

    @Test
    fun repeatWithNothingSaidYetKeepsListening() {
        send(DialogEvent.BubbleTapped)
        val out = send(DialogEvent.Heard("tekrar söyle"))
        assertEquals(listOf(DialogEffect.StartListening), out)
        assertEquals(DialogState.Listening, machine.state)
    }

    @Test
    fun repeatWhileWorkingSaysTheLastInformationAgain() {
        toWorking()
        val out = send(DialogEvent.Heard("tekrar söyle"))
        assertEquals(listOf(text("Ayarları açıyorum.")), said(out))
        assertTrue(out.last() is ListenForCommands)
    }

    @Test
    fun restartWhileWorkingRestartsFromTheFirstStep() {
        val id = toWorking()
        val out = send(DialogEvent.Heard("en baştan anlat"))
        assertTrue(out.contains(DialogEffect.RestartFromFirstStep(id)))
        assertEquals(listOf(text("Ayarları açıyorum.")), said(out))
    }

    @Test
    fun repeatWhileDecidingSaysItIsWorking() {
        toProcessing()
        val out = send(DialogEvent.Heard("tekrar söyle"))
        assertEquals(listOf(template(Phrase.PROGRESS)), said(out))
        assertTrue(out.none { it is Decide })
    }

    // --- "Yavaş konuş" ---

    @Test
    fun slowerSwitchesRateThenRepeats() {
        toReadingBack()
        val out = send(DialogEvent.Heard("biraz yavaş konuş"))
        assertTrue(out.indexOf(DialogEffect.SlowDown) < out.indexOfFirst { it is Speak })
        assertEquals(listOf(text("Haritaları açayım mı?")), said(out))
    }

    // --- "Neden" ---

    @Test
    fun whyDuringQuestionExplainsThenAsksAgainWithoutTimeout() {
        toAwaiting()
        val out = send(DialogEvent.Heard("neden"))
        val token = out.filterIsInstance<Explain>().single().token
        assertTrue(out.contains(DialogEffect.CancelTimer))
        assertTrue(out.none { it is DialogEffect.ScheduleTimer })

        val answer = send(DialogEvent.Explained(token, "Siz istediniz."))
        assertEquals(listOf(text("Siz istediniz."), text("Haritaları açayım mı?")), said(answer))
        assertTrue(machine.state is DialogState.ReadingBack)
        assertTrue(send(DialogEvent.Explained(token, "x")).isEmpty()) // only once
    }

    @Test
    fun whyDuringReadbackCutsItAndAsksAgainEvenWithoutText() {
        toReadingBack()
        val out = send(DialogEvent.Heard("niye"))
        assertTrue(out.contains(CancelSpeech))
        val token = out.filterIsInstance<Explain>().single().token
        val answer = send(DialogEvent.Explained(token, null))
        assertEquals(listOf(text("Haritaları açayım mı?")), said(answer))
    }

    @Test
    fun whyWhileWorkingSpeaksTheExplanation() {
        toWorking()
        val token = send(DialogEvent.Heard("neden")).filterIsInstance<Explain>().single().token
        assertEquals(listOf(text("Siz istediniz.")), said(send(DialogEvent.Explained(token, "Siz istediniz."))))
        assertTrue(machine.state is DialogState.Working)
    }

    @Test
    fun whyWhileListeningGoesToTheOnDeviceRules() {
        send(DialogEvent.BubbleTapped)
        val out = send(DialogEvent.Heard("neden"))
        assertTrue(out.any { it is Decide }) // G-39 answers it in the rules layer
    }

    @Test
    fun lateExplanationAfterStopIsIgnored() {
        toAwaiting()
        val token = send(DialogEvent.Heard("neden")).filterIsInstance<Explain>().single().token
        send(DialogEvent.StopPressed)
        assertTrue(send(DialogEvent.Explained(token, "Siz istediniz.")).isEmpty())
    }

    @Test
    fun typedCommandFromIdleIsHandledToo() {
        send(DialogEvent.TextEntered("dur"))
        assertEquals(DialogState.Idle, machine.state)
        assertTrue(log.none { it is Decide })
    }

    @Test
    fun ordinaryAnswerStillWorks() {
        toAwaiting()
        assertTrue(send(DialogEvent.Heard("evet")).any { it is DialogEffect.Execute })
    }
}
