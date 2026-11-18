package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.dialog.DialogEffect.CancelSpeech
import tr.yasliasistan.core.dialog.DialogEffect.Decide
import tr.yasliasistan.core.dialog.DialogEffect.Execute
import tr.yasliasistan.core.dialog.DialogEffect.SessionEnded
import tr.yasliasistan.core.dialog.DialogEffect.SessionStarted
import tr.yasliasistan.core.dialog.DialogEffect.Speak
import tr.yasliasistan.core.dialog.DialogEffect.StartListening
import tr.yasliasistan.core.dialog.DialogEffect.StopListening
import tr.yasliasistan.core.dialog.DialogEvent.BubbleTapped
import tr.yasliasistan.core.dialog.DialogEvent.CannotListen
import tr.yasliasistan.core.dialog.DialogEvent.Heard
import tr.yasliasistan.core.dialog.DialogEvent.SpeechFinished
import tr.yasliasistan.core.dialog.DialogEvent.TextEntered
import tr.yasliasistan.core.dialog.DialogEvent.Understood

/** Typed commands (FG-04) and microphone problems reported during a session (G-11). */
class DialogTypedAndMicTest {

    private val machine = DialogMachine()
    private val log = mutableListOf<DialogEffect>()

    private fun send(event: DialogEvent): List<DialogEffect> = machine.handle(event).also { log += it }

    @Test
    fun typedCommandFromIdleStartsASessionWithoutTheMicrophone() {
        val effects = send(TextEntered("ayarları aç"))
        assertTrue(effects.contains(SessionStarted))
        assertFalse(effects.contains(StartListening))
        assertEquals("ayarları aç", effects.filterIsInstance<Decide>().single().transcript)
        assertTrue(machine.state is DialogState.Processing)
    }

    @Test
    fun typedCommandWhileListeningReplacesSpeech() {
        send(BubbleTapped)
        val effects = send(TextEntered("ayarları aç"))
        assertTrue(effects.contains(StopListening))
        assertFalse("session already running", effects.contains(SessionStarted))
        assertEquals(1, effects.filterIsInstance<Decide>().size)
    }

    @Test
    fun typedCommandDuringClosingSentenceCutsItAndStartsAgain() {
        send(BubbleTapped)
        send(DialogEvent.ListenFailed) // -> Done ("Sizi duyamadım...")
        assertTrue(machine.state is DialogState.Done)
        val effects = send(TextEntered("ayarları aç"))
        assertEquals(CancelSpeech, effects.first())
        assertTrue(effects.contains(SessionStarted))
        assertTrue(machine.state is DialogState.Processing)
    }

    @Test
    fun typedYesAnswersTheConfirmationQuestion() {
        send(TextEntered("haritaları aç"))
        val id = log.filterIsInstance<Decide>().last().requestId
        send(Understood(id, "Google Haritalar uygulamasını açayım mı?"))
        send(SpeechFinished(log.filterIsInstance<Speak>().last().utteranceId))
        val effects = send(TextEntered("evet"))
        assertTrue(effects.contains(Execute(id)))
    }

    @Test
    fun blankTypedTextIsNotACommand() {
        val effects = send(TextEntered("   "))
        assertTrue(effects.none { it is Decide })
        assertTrue(machine.state is DialogState.Done)
    }

    @Test
    fun cannotListenEndsTheSessionAndSaysWhy() {
        send(BubbleTapped)
        val effects = send(CannotListen("Mikrofon izni gerekiyor."))
        assertTrue(effects.contains(StopListening))
        assertTrue(effects.contains(SessionEnded))
        assertEquals(
            Utterance.Text("Mikrofon izni gerekiyor."),
            effects.filterIsInstance<Speak>().single().utterance,
        )
        assertTrue(machine.state is DialogState.Done)
    }

    @Test
    fun cannotListenWhileAwaitingConfirmationMeansNoAction() {
        send(BubbleTapped)
        send(Heard("haritaları aç"))
        val id = log.filterIsInstance<Decide>().last().requestId
        send(Understood(id, "Açayım mı?"))
        send(SpeechFinished(log.filterIsInstance<Speak>().last().utteranceId))
        val effects = send(CannotListen("Mikrofon kullanılıyor."))
        assertTrue(effects.none { it is Execute })
        assertTrue(machine.state is DialogState.Done)
    }

    @Test
    fun cannotListenIsIgnoredWhenNotListening() {
        assertTrue(send(CannotListen("x")).isEmpty())
        send(BubbleTapped)
        send(Heard("ayarları aç"))
        assertTrue(send(CannotListen("x")).isEmpty())
        assertTrue(machine.state is DialogState.Processing)
    }

    @Test
    fun eventTextIsNeverPrinted() {
        assertFalse(TextEntered("Ayşe Test").toString().contains("Ayşe"))
        assertFalse(CannotListen("gizli").toString().contains("gizli"))
    }
}
