package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.dialog.DialogEffect.CancelSpeech
import tr.yasliasistan.core.dialog.DialogEffect.Execute
import tr.yasliasistan.core.dialog.DialogEffect.ListenWhileSpeaking
import tr.yasliasistan.core.dialog.DialogEffect.Speak
import tr.yasliasistan.core.dialog.DialogEffect.StartListening

/** G-16 / FG-14: listening during a spoken question and cutting it when the user talks. */
class DialogBargeInTest {

    private val machine = DialogMachine()
    private val log = mutableListOf<DialogEffect>()

    private fun send(event: DialogEvent) = machine.handle(event).also { log += it }

    private fun lastSpeak() = log.filterIsInstance<Speak>().last()

    private fun toReadingBack(): Int {
        send(DialogEvent.BubbleTapped)
        send(DialogEvent.Heard("haritaları aç"))
        val id = log.filterIsInstance<DialogEffect.Decide>().last().requestId
        send(DialogEvent.Understood(id, "Haritaları açayım mı?"))
        return id
    }

    @Test
    fun readbackQuestionListensWhileSpeaking() {
        toReadingBack()
        assertEquals(ListenWhileSpeaking(lastSpeak().utteranceId), log.last())
    }

    @Test
    fun replyQuestionListensWhileSpeaking() {
        send(DialogEvent.BubbleTapped)
        send(DialogEvent.Heard("whatsapp aç"))
        val id = log.filterIsInstance<DialogEffect.Decide>().last().requestId
        send(DialogEvent.Replied(id, "Hangisini açayım?", expectsAnswer = true))
        assertTrue(log.last() is ListenWhileSpeaking)
    }

    @Test
    fun finalSentencesAndAnnouncementsDoNotListen() {
        send(DialogEvent.BubbleTapped)
        send(DialogEvent.Heard("bir şey"))
        val id = log.filterIsInstance<DialogEffect.Decide>().last().requestId
        send(DialogEvent.Replied(id, "Bunu henüz yapamıyorum.", expectsAnswer = false))
        assertTrue(log.none { it is ListenWhileSpeaking }) // session over: FG-02
    }

    @Test
    fun userVoiceCutsTheQuestionAndTheAnswerStillCounts() {
        val id = toReadingBack()
        val cut = send(DialogEvent.UserInterrupted)
        assertEquals(listOf(CancelSpeech), cut)
        // The interrupted utterance reports finished; the dialog waits for the answer.
        val after = send(DialogEvent.SpeechFinished(lastSpeak().utteranceId))
        assertTrue(after.contains(StartListening))
        assertTrue(machine.state is DialogState.AwaitingConfirmation)
        assertTrue(send(DialogEvent.Heard("evet")).contains(Execute(id)))
    }

    @Test
    fun interruptionOutsideAQuestionChangesNothing() {
        assertEquals(emptyList<DialogEffect>(), send(DialogEvent.UserInterrupted)) // idle
        send(DialogEvent.BubbleTapped)
        assertEquals(emptyList<DialogEffect>(), send(DialogEvent.UserInterrupted)) // listening
        assertEquals(DialogState.Listening, machine.state)
    }

    @Test
    fun noListeningDuringSpeechWhileTyping() {
        send(DialogEvent.BubbleTapped)
        send(DialogEvent.Heard("haritaları aç"))
        val id = log.filterIsInstance<DialogEffect.Decide>().last().requestId
        send(DialogEvent.TypingStarted)
        send(DialogEvent.Understood(id, "Haritaları açayım mı?"))
        assertTrue(log.none { it is ListenWhileSpeaking })
    }
}
