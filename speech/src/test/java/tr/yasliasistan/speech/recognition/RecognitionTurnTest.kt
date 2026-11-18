package tr.yasliasistan.speech.recognition

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionTurnTest {

    private val scheduler = FakeScheduler()
    private val config = ListenConfig(silenceMs = 3_500, maxMs = 60_000, noSpeechMs = 8_000, finalResultGraceMs = 1_500)
    private val mic = FakeMic()
    private val listener = RecordingListener()
    private val turn = RecognitionTurn(config, scheduler, mic, listener)

    @Test
    fun finalTextBecomesCommand() {
        turn.onReady()
        turn.onPartialText("ayarları")
        turn.onFinalText("  Ayarları   aç ")
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Heard("Ayarları aç")), listener.outcomes)
        assertTrue(listener.listening)
        assertEquals(listOf("ayarları"), listener.partials)
    }

    @Test
    fun emptyResultProducesNoCommand() {
        turn.onReady()
        turn.onFinalText("   ")
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.NothingHeard), listener.outcomes)
    }

    @Test
    fun noiseOnlyResultProducesNoCommand() {
        turn.onReady()
        turn.onFinalText("ııı... hmm")
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.NothingHeard), listener.outcomes)
    }

    @Test
    fun noMatchErrorWithoutSpeechIsNothingHeard() {
        turn.onReady()
        turn.onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.NothingHeard), listener.outcomes)
    }

    @Test
    fun noSpeechClosesMicAfterNoSpeechTimeout() {
        turn.onReady()
        scheduler.advanceBy(7_999)
        assertTrue(listener.outcomes.isEmpty())
        scheduler.advanceBy(1)
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.NothingHeard), listener.outcomes)
        assertEquals(1, mic.aborts)
    }

    @Test
    fun windowStaysOpenWhileUserKeepsTalking() {
        turn.onReady()
        // Speech keeps coming every 3 s for 30 s: longer than both silence and no-speech limits.
        repeat(10) { i ->
            turn.onPartialText("kelime $i")
            scheduler.advanceBy(3_000)
        }
        assertEquals(0, mic.stops)
        assertTrue(listener.outcomes.isEmpty())
    }

    @Test
    fun silenceAfterSpeechStopsAndWaitsForFinalText() {
        turn.onReady()
        turn.onPartialText("Ayşe Test'i")
        scheduler.advanceBy(3_499)
        assertEquals(0, mic.stops)
        scheduler.advanceBy(1)
        assertEquals(1, mic.stops)
        turn.onFinalText("Ayşe Test'i ara")
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Heard("Ayşe Test'i ara")), listener.outcomes)
    }

    @Test
    fun missingFinalTextFallsBackToLastPartialAfterGrace() {
        turn.onReady()
        turn.onPartialText("Ayarları aç")
        scheduler.advanceBy(3_500)
        scheduler.advanceBy(1_500)
        // UD-1 Ö-1 / NFG-18: an unconfirmed partial is marked, so it can never act without a question.
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Heard("Ayarları aç", partialOnly = true)), listener.outcomes)
        assertEquals(1, mic.aborts)
    }

    @Test
    fun noMatchAfterPartialIsMarkedPartialOnly() {
        turn.onReady()
        turn.onPartialText("evet")
        turn.onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Heard("evet", partialOnly = true)), listener.outcomes)
    }

    @Test
    fun unusableFinalTextFallsBackToPartialOnly() {
        turn.onReady()
        turn.onPartialText("Ayarları aç")
        turn.onFinalText("   ")
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Heard("Ayarları aç", partialOnly = true)), listener.outcomes)
    }

    @Test
    fun finalTextIsNotPartialOnly() {
        turn.onReady()
        turn.onPartialText("Ayarları")
        turn.onFinalText("Ayarları aç")
        assertEquals(false, (listener.outcomes.single() as SpeechOutcome.Heard).partialOnly)
    }

    @Test
    fun hardLimitStopsEvenDuringContinuousSpeech() {
        turn.onReady()
        repeat(30) { i ->
            turn.onPartialText("söz $i")
            scheduler.advanceBy(2_000)
        }
        assertEquals(1, mic.stops)
    }

    @Test
    fun cancelGivesCancelledAndIgnoresLateResults() {
        turn.onReady()
        turn.onPartialText("Ayarları")
        turn.cancelRequested()
        turn.onFinalText("Ayarları aç")
        turn.onError(SpeechRecognizer.ERROR_CLIENT)
        scheduler.advanceBy(60_000)
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Cancelled), listener.outcomes)
        assertEquals(1, mic.aborts)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun userFinishDeliversHeardText() {
        turn.onReady()
        turn.onPartialText("Saati aç")
        turn.finishRequested()
        turn.finishRequested()
        assertEquals(1, mic.stops)
        turn.onFinalText("Saati aç")
        assertEquals(listOf<SpeechOutcome>(SpeechOutcome.Heard("Saati aç")), listener.outcomes)
    }

    @Test
    fun languageErrorIsReportedAsFailure() {
        turn.onError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(
            listOf<SpeechOutcome>(SpeechOutcome.Failed(FailureReason.LANGUAGE_UNAVAILABLE)),
            listener.outcomes,
        )
    }

    @Test
    fun permissionErrorIsReportedAsFailure() {
        turn.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals(
            listOf<SpeechOutcome>(SpeechOutcome.Failed(FailureReason.MIC_PERMISSION_MISSING)),
            listener.outcomes,
        )
    }

    @Test
    fun exactlyOneOutcome() {
        turn.onReady()
        turn.onFinalText("Ayarları aç")
        turn.onFinalText("başka")
        turn.onError(SpeechRecognizer.ERROR_AUDIO)
        assertEquals(1, listener.outcomes.size)
    }

    private class FakeMic : RecognitionTurn.MicControl {
        var stops = 0
        var aborts = 0
        override fun stop() { stops++ }
        override fun abort() { aborts++ }
    }

    private class RecordingListener : SpeechListener {
        var listening = false
        val partials = mutableListOf<String>()
        val outcomes = mutableListOf<SpeechOutcome>()
        override fun onListening() { listening = true }
        override fun onPartialText(text: String) { partials += text }
        override fun onOutcome(outcome: SpeechOutcome) { outcomes += outcome }
    }
}
