package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.dialog.DialogEffect.CancelSpeech
import tr.yasliasistan.core.dialog.DialogEffect.CancelWork
import tr.yasliasistan.core.dialog.DialogEffect.Decide
import tr.yasliasistan.core.dialog.DialogEffect.Execute
import tr.yasliasistan.core.dialog.DialogEffect.ScheduleTimer
import tr.yasliasistan.core.dialog.DialogEffect.SessionEnded
import tr.yasliasistan.core.dialog.DialogEffect.SessionStarted
import tr.yasliasistan.core.dialog.DialogEffect.Speak
import tr.yasliasistan.core.dialog.DialogEffect.StartListening
import tr.yasliasistan.core.dialog.DialogEvent.BubbleTapped
import tr.yasliasistan.core.dialog.DialogEvent.ConfirmTapped
import tr.yasliasistan.core.dialog.DialogEvent.ExecutionFailed
import tr.yasliasistan.core.dialog.DialogEvent.ExecutionSucceeded
import tr.yasliasistan.core.dialog.DialogEvent.Heard
import tr.yasliasistan.core.dialog.DialogEvent.ListenFailed
import tr.yasliasistan.core.dialog.DialogEvent.Proceeding
import tr.yasliasistan.core.dialog.DialogEvent.Replied
import tr.yasliasistan.core.dialog.DialogEvent.SpeechFinished
import tr.yasliasistan.core.dialog.DialogEvent.StopPressed
import tr.yasliasistan.core.dialog.DialogEvent.TimerFired
import tr.yasliasistan.core.dialog.DialogEvent.Understood

class DialogMachineTest {

    private val machine = DialogMachine()
    private val log = mutableListOf<DialogEffect>()

    private fun send(event: DialogEvent): List<DialogEffect> = machine.handle(event).also { log += it }

    private inline fun <reified T : DialogEffect> last(): T = log.filterIsInstance<T>().last()

    private fun lastSpeechId() = last<Speak>().utteranceId
    private fun lastTimerToken() = last<ScheduleTimer>().token

    private fun spoken(effects: List<DialogEffect>) = effects.filterIsInstance<Speak>().map { it.utterance }

    /** Drives the machine to "awaiting confirmation"; returns the request id. */
    private fun toAwaiting(): Int {
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        val id = last<Decide>().requestId
        send(Understood(id, "Ayarları açayım mı?"))
        send(SpeechFinished(lastSpeechId()))
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
        return id
    }

    private fun toWorking(): Int = toAwaiting().also { send(Heard("evet")) }

    @Test
    fun happyPathRunsOnlyAfterYesAndAnnouncesCompletion() {
        val effects = send(BubbleTapped)
        assertTrue(effects.contains(SessionStarted))
        assertTrue(effects.contains(StartListening))
        assertEquals(DialogState.Listening, machine.state)

        send(Heard("ayarları aç"))
        val id = last<Decide>().requestId
        assertTrue(machine.state is DialogState.Processing)

        val readback = send(Understood(id, "Ayarları açayım mı?"))
        assertEquals(listOf(Utterance.Text("Ayarları açayım mı?")), spoken(readback))
        assertTrue(machine.state is DialogState.ReadingBack)

        val awaiting = send(SpeechFinished(lastSpeechId()))
        assertTrue(awaiting.contains(StartListening))
        assertEquals(10_000L, last<ScheduleTimer>().delayMs)
        assertTrue(log.none { it is Execute })

        val yes = send(Heard("Evet"))
        assertTrue(yes.contains(Execute(id)))
        assertTrue(machine.state is DialogState.Working)

        val done = send(ExecutionSucceeded(id, "Ayarları açtım."))
        assertEquals(listOf(Utterance.Text("Ayarları açtım.")), spoken(done))
        assertTrue(done.contains(SessionEnded))
        assertTrue(machine.state is DialogState.Done)

        send(SpeechFinished(lastSpeechId()))
        assertEquals(DialogState.Idle, machine.state)
    }

    @Test
    fun successWithoutTextUsesDoneTemplate() {
        val id = toWorking()
        assertEquals(listOf(Utterance.Template(Phrase.DONE)), spoken(send(ExecutionSucceeded(id, null))))
    }

    @Test
    fun failureIsAnnouncedAndEndsSession() {
        val id = toWorking()
        val effects = send(ExecutionFailed(id, null))
        assertEquals(listOf(Utterance.Template(Phrase.FAILED)), spoken(effects))
        assertTrue(effects.contains(SessionEnded))
    }

    @Test
    fun noExecutionWithoutConfirmation_yesInOtherStatesIsIgnored() {
        send(ConfirmTapped(true))
        send(BubbleTapped)
        send(ConfirmTapped(true))
        send(Heard("evet"))
        val id = last<Decide>().requestId
        send(ConfirmTapped(true))
        send(Understood(id, "Ayarları açayım mı?"))
        send(ExecutionSucceeded(id, null))
        assertTrue(log.none { it is Execute })
        assertTrue(machine.state is DialogState.ReadingBack)
    }

    @Test
    fun noAnswerCancels() {
        toAwaiting()
        val effects = send(Heard("hayır"))
        assertTrue(effects.none { it is Execute })
        assertEquals(listOf(Utterance.Template(Phrase.CANCELLED)), spoken(effects))
        assertTrue(effects.contains(SessionEnded))
    }

    @Test
    fun confirmButtonsWork() {
        val id = toAwaiting()
        assertTrue(send(ConfirmTapped(true)).contains(Execute(id)))
        toAwaitingFresh()
        assertTrue(send(ConfirmTapped(false)).none { it is Execute })
    }

    private fun toAwaitingFresh() {
        send(StopPressed)
        toAwaiting()
    }

    @Test
    fun silenceMeansNo() {
        toAwaiting()
        val effects = send(TimerFired(lastTimerToken()))
        assertTrue(effects.none { it is Execute })
        assertEquals(listOf(Utterance.Template(Phrase.CANCELLED)), spoken(effects))
        assertTrue(machine.state is DialogState.Done)
    }

    @Test
    fun recognizerSilenceInsideConfirmWindowKeepsListening() {
        toAwaiting()
        val effects = send(ListenFailed)
        assertTrue(effects.contains(StartListening))
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
    }

    @Test
    fun unclearAnswerAsksOnceMoreThenCountsAsNo() {
        toAwaiting()
        val first = send(Heard("bilmiyorum"))
        assertEquals(listOf(Utterance.Template(Phrase.CONFIRM_AGAIN)), spoken(first))
        assertTrue(machine.state is DialogState.ReadingBack)
        send(SpeechFinished(lastSpeechId()))
        assertTrue(machine.state is DialogState.AwaitingConfirmation)

        val second = send(Heard("şey bilmem"))
        assertTrue(second.none { it is Execute })
        assertEquals(listOf(Utterance.Template(Phrase.CANCELLED)), spoken(second))
    }

    @Test
    fun answerDuringReadbackIsAcceptedImmediately() {
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        val id = last<Decide>().requestId
        send(Understood(id, "Ayarları açayım mı?"))
        val effects = send(Heard("evet"))
        assertTrue(effects.contains(CancelSpeech))
        assertTrue(effects.contains(Execute(id)))
    }

    @Test
    fun staleTimerIsIgnored() {
        toAwaiting()
        val oldToken = lastTimerToken()
        send(Heard("bilmiyorum"))
        send(SpeechFinished(lastSpeechId()))
        assertTrue(send(TimerFired(oldToken)).isEmpty())
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
    }

    @Test
    fun staleSpeechFinishedDoesNotAdvance() {
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        val id = last<Decide>().requestId
        send(TimerFired(lastTimerToken())) // progress "Bakıyorum" spoken
        val progressId = lastSpeechId()
        send(Understood(id, "Ayarları açayım mı?"))
        send(SpeechFinished(progressId))
        assertTrue(machine.state is DialogState.ReadingBack)
    }

    @Test
    fun staleResultsForOldRequestAreIgnored() {
        val oldId = toAwaiting()
        send(StopPressed)
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        assertTrue(send(Understood(oldId, "eski")).isEmpty())
        assertTrue(machine.state is DialogState.Processing)
    }

    @Test
    fun stopReturnsToIdleFromEveryState() {
        val drivers: List<() -> Unit> = listOf(
            {},
            { send(BubbleTapped) },
            { send(BubbleTapped); send(Heard("ayarları aç")) },
            {
                send(BubbleTapped); send(Heard("ayarları aç"))
                send(Understood(last<Decide>().requestId, "Ayarları açayım mı?"))
            },
            { toAwaiting() },
            { toWorking() },
            { send(BubbleTapped); send(Heard("x")); send(Replied(last<Decide>().requestId, "Hangisi?", true)) },
            { toWorking(); send(ExecutionSucceeded(last<Execute>().requestId, null)) },
        )
        for ((index, drive) in drivers.withIndex()) {
            send(StopPressed)
            log.clear()
            drive()
            val stateBefore = machine.state
            val effects = send(StopPressed)
            assertEquals("case $index from $stateBefore", DialogState.Idle, machine.state)
            assertTrue("case $index: no execution after stop", effects.none { it is Execute })
            if (stateBefore is DialogState.Working) assertTrue(effects.any { it is CancelWork })
            if (stateBefore.sessionOpen) assertTrue("case $index", effects.contains(SessionEnded))
        }
    }

    @Test
    fun stopThenLateResultDoesNothing() {
        val id = toWorking()
        send(StopPressed)
        assertTrue(send(ExecutionSucceeded(id, null)).isEmpty())
        assertEquals(DialogState.Idle, machine.state)
    }

    @Test
    fun lateDecisionEmitsProgressAndRepeats() {
        send(BubbleTapped)
        val effects = send(Heard("ayarları aç"))
        val timer = effects.filterIsInstance<ScheduleTimer>().single()
        assertEquals(5_000L, timer.delayMs)

        val first = send(TimerFired(timer.token))
        assertEquals(listOf(Utterance.Template(Phrase.PROGRESS)), spoken(first))
        val repeat = first.filterIsInstance<ScheduleTimer>().single()
        assertEquals(10_000L, repeat.delayMs)
        assertTrue(machine.state is DialogState.Processing)
    }

    @Test
    fun lateExecutionEmitsProgress() {
        toWorking()
        val first = send(TimerFired(lastTimerToken()))
        assertEquals(listOf(Utterance.Template(Phrase.PROGRESS)), spoken(first))
        assertTrue(machine.state is DialogState.Working)
    }

    @Test
    fun emptyOrFailedListeningEndsWithNotHeard() {
        send(BubbleTapped)
        assertEquals(listOf(Utterance.Template(Phrase.NOT_HEARD)), spoken(send(Heard("  "))))
        assertTrue(log.none { it is Decide })
        send(StopPressed)
        send(BubbleTapped)
        assertEquals(listOf(Utterance.Template(Phrase.NOT_HEARD)), spoken(send(ListenFailed)))
    }

    @Test
    fun clarifyingReplyListensAgain_finalReplyEnds() {
        send(BubbleTapped)
        send(Heard("ali'yi ara"))
        val id = last<Decide>().requestId
        send(Replied(id, "Hangi Ali?", expectsAnswer = true))
        assertTrue(machine.state is DialogState.Replying)
        val after = send(SpeechFinished(lastSpeechId()))
        assertTrue(after.contains(StartListening))
        assertEquals(DialogState.Listening, machine.state)

        send(Heard("uçağı uçur"))
        val id2 = last<Decide>().requestId
        val end = send(Replied(id2, "Bunu henüz yapamıyorum.", expectsAnswer = false))
        assertTrue(end.contains(SessionEnded))
        assertTrue(machine.state is DialogState.Done)
    }

    @Test
    fun tapDuringDoneStartsNewSessionAtOnce() {
        val id = toWorking()
        send(ExecutionSucceeded(id, null))
        val effects = send(BubbleTapped)
        assertTrue(effects.contains(CancelSpeech))
        assertTrue(effects.contains(StartListening))
        assertEquals(DialogState.Listening, machine.state)
    }

    /** Low-risk decision (FG-71): announce and act at once, no yes/no question. */
    private fun toProceeding(): Int {
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        val id = last<Decide>().requestId
        val effects = send(Proceeding(id, "Ayarlar uygulamasını açıyorum."))
        assertEquals(listOf(Utterance.Text("Ayarlar uygulamasını açıyorum.")), spoken(effects))
        assertTrue(effects.contains(Execute(id)))
        assertTrue("no yes/no listening turn", effects.none { it is StartListening })
        return id
    }

    @Test
    fun lowRiskDecisionAnnouncesAndExecutesWithoutConfirmation() {
        val id = toProceeding()
        assertTrue(machine.state is DialogState.Working)
        val done = send(ExecutionSucceeded(id, "Açtım."))
        assertEquals(listOf(Utterance.Text("Açtım.")), spoken(done))
        assertTrue(done.contains(SessionEnded))
    }

    @Test
    fun announcementSpeechEndDoesNotChangeWorkingState() {
        toProceeding()
        assertTrue(send(SpeechFinished(lastSpeechId())).isEmpty())
        assertTrue(machine.state is DialogState.Working)
    }

    @Test
    fun stopDuringLowRiskExecutionCancelsWork() {
        val id = toProceeding()
        val effects = send(StopPressed)
        assertTrue(effects.contains(CancelWork(id)))
        assertEquals(DialogState.Idle, machine.state)
    }

    @Test
    fun lateProceedingForOldRequestIsIgnored() {
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        val oldId = last<Decide>().requestId
        send(StopPressed)
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        assertTrue(send(Proceeding(oldId, "eski")).isEmpty())
        assertTrue(machine.state is DialogState.Processing)
    }

    @Test
    fun proceedingOutsideProcessingIsIgnored() {
        val id = toAwaiting()
        assertTrue(send(Proceeding(id, "x")).isEmpty())
        assertTrue(log.none { it is Execute })
    }

    @Test
    fun personalTextIsNotExposedByToString() {
        val secret = "Ayşe Yılmaz"
        assertFalse(Proceeding(1, secret).toString().contains(secret))
        assertFalse(Heard(secret).toString().contains(secret))
        assertFalse(Understood(1, secret).toString().contains(secret))
        assertFalse(Decide(1, secret).toString().contains(secret))
        assertFalse(Utterance.Text(secret).toString().contains(secret))
        assertFalse(Replied(1, secret, true).toString().contains(secret))
        assertFalse(ExecutionSucceeded(1, secret).toString().contains(secret))
    }
}
