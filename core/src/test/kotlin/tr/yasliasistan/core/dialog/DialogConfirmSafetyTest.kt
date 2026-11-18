package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.dialog.DialogEffect.CancelTimer
import tr.yasliasistan.core.dialog.DialogEffect.Decide
import tr.yasliasistan.core.dialog.DialogEffect.Execute
import tr.yasliasistan.core.dialog.DialogEffect.ScheduleTimer
import tr.yasliasistan.core.dialog.DialogEffect.Speak
import tr.yasliasistan.core.dialog.DialogEffect.StartListening
import tr.yasliasistan.core.dialog.DialogEvent.BubbleTapped
import tr.yasliasistan.core.dialog.DialogEvent.Heard
import tr.yasliasistan.core.dialog.DialogEvent.SpeechFinished
import tr.yasliasistan.core.dialog.DialogEvent.StopPressed
import tr.yasliasistan.core.dialog.DialogEvent.TextEntered
import tr.yasliasistan.core.dialog.DialogEvent.TimerFired
import tr.yasliasistan.core.dialog.DialogEvent.TypingStarted
import tr.yasliasistan.core.dialog.DialogEvent.Understood

/** UD-1 fixes: partial-only transcripts (Ö-1), V-12 yes words (Ö-2), typing pauses the timeout (Ö-4). */
class DialogConfirmSafetyTest {

    private val machine = DialogMachine()
    private val log = mutableListOf<DialogEffect>()

    private fun send(event: DialogEvent): List<DialogEffect> = machine.handle(event).also { log += it }

    private inline fun <reified T : DialogEffect> last(): T = log.filterIsInstance<T>().last()

    private fun spoken(effects: List<DialogEffect>) = effects.filterIsInstance<Speak>().map { it.utterance }

    private fun toReadingBack(lowRisk: Boolean = false): Int {
        send(BubbleTapped)
        send(Heard("haritaları aç"))
        val id = last<Decide>().requestId
        send(Understood(id, "Google Haritalar uygulamasını açayım mı?", lowRisk = lowRisk))
        return id
    }

    private fun toAwaiting(lowRisk: Boolean = false): Int = toReadingBack(lowRisk).also {
        send(SpeechFinished(last<Speak>().utteranceId))
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
    }

    // --- Ö-1: only the recognizer's final result may act without a question ---

    @Test
    fun partialOnlyCommandIsMarkedForTheDecisionEngine() {
        send(BubbleTapped)
        send(Heard("ayarları aç", partialOnly = true))
        assertTrue(last<Decide>().partialOnly)
        send(StopPressed)
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        assertTrue(!last<Decide>().partialOnly)
    }

    @Test
    fun partialOnlyYesIsNotAConfirmation() {
        toAwaiting()
        val effects = send(Heard("evet", partialOnly = true))
        assertTrue(effects.none { it is Execute })
        assertEquals(listOf(Utterance.Template(Phrase.CONFIRM_AGAIN)), spoken(effects))
    }

    @Test
    fun partialOnlyNoStillCancels() {
        toAwaiting()
        val effects = send(Heard("hayır", partialOnly = true))
        assertTrue(effects.none { it is Execute })
        assertEquals(listOf(Utterance.Template(Phrase.CANCELLED)), spoken(effects))
    }

    // --- Ö-2: soft yes words only for Low risk ---

    @Test
    fun softYesConfirmsLowRisk() {
        val id = toAwaiting(lowRisk = true)
        assertTrue(send(Heard("olur")).contains(Execute(id)))
    }

    @Test
    fun softYesIsUnclearForMediumOrHighRisk() {
        toAwaiting(lowRisk = false)
        val effects = send(Heard("tamam"))
        assertTrue(effects.none { it is Execute })
        assertEquals(listOf(Utterance.Template(Phrase.CONFIRM_AGAIN)), spoken(effects))
        send(SpeechFinished(last<Speak>().utteranceId))
        assertTrue("explicit yes still works", send(Heard("evet")).any { it is Execute })
    }

    @Test
    fun softYesDuringReadbackKeepsTheRiskRule() {
        toReadingBack(lowRisk = false)
        assertTrue(send(Heard("peki")).none { it is Execute })
    }

    @Test
    fun lowRiskSurvivesTheSecondQuestion() {
        val id = toAwaiting(lowRisk = true)
        send(Heard("bilmiyorum"))
        send(SpeechFinished(last<Speak>().utteranceId))
        assertTrue(send(Heard("tamam")).contains(Execute(id)))
    }

    // --- Ö-4: typing an answer is not silence ---

    @Test
    fun typingWhileAwaitingStopsTheTimeout() {
        val id = toAwaiting()
        val token = last<ScheduleTimer>().token
        assertTrue(send(TypingStarted).contains(CancelTimer))
        assertTrue(send(TimerFired(token)).isEmpty())
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
        assertTrue(send(TextEntered("evet")).contains(Execute(id)))
    }

    @Test
    fun typingDuringReadbackStartsNoTimeoutAndNoMicrophone() {
        val id = toReadingBack()
        send(TypingStarted)
        val after = send(SpeechFinished(last<Speak>().utteranceId))
        assertTrue(after.none { it is ScheduleTimer || it is StartListening })
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
        assertTrue(send(TextEntered("evet")).contains(Execute(id)))
    }

    @Test
    fun typingEndsWithTheSession() {
        toAwaiting()
        send(TypingStarted)
        send(StopPressed)
        log.clear()
        toAwaiting()
        assertEquals(10_000L, last<ScheduleTimer>().delayMs)
    }

    @Test
    fun typedUnclearAnswerRestartsTheTimeoutForTheSecondQuestion() {
        toAwaiting()
        send(TypingStarted)
        send(TextEntered("bilmiyorum"))
        val after = send(SpeechFinished(last<Speak>().utteranceId))
        assertTrue(after.any { it is ScheduleTimer })
        assertTrue(after.contains(StartListening))
    }

    @Test
    fun typingOutsideASessionIsIgnored() {
        assertTrue(send(TypingStarted).isEmpty())
        assertEquals(DialogState.Idle, machine.state)
    }
}
