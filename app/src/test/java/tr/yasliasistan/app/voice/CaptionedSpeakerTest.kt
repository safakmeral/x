package tr.yasliasistan.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.speech.tts.Delay
import tr.yasliasistan.speech.tts.Speaker

class CaptionedSpeakerTest {

    private class FakeSpeaker : Speaker {
        var heard: Speaker.Listener? = null
        val spoken = mutableListOf<Int>()
        var stops = 0
        var doneAtOnce = false

        override fun speak(utteranceId: Int, text: String) {
            spoken += utteranceId
            if (doneAtOnce) heard?.onSpeechDone(utteranceId)
        }
        override fun stop() { stops++ }
        override fun setListener(listener: Speaker.Listener?) { heard = listener }
    }

    private class FakeTimer : Delay {
        var pending: (() -> Unit)? = null
        var delay = 0L
        override fun schedule(delayMs: Long, action: () -> Unit) {
            delay = delayMs
            pending = action
        }
        override fun cancel() { pending = null }
        fun fire() = pending?.also { pending = null }?.invoke()
    }

    private val inner = FakeSpeaker()
    private val timer = FakeTimer()
    private var shown: String? = "unset"
    private val events = mutableListOf<String>()
    private val speaker = CaptionedSpeaker(inner, timer, show = { shown = it }).apply {
        setListener(object : Speaker.Listener {
            override fun onSpeechDone(utteranceId: Int) { events += "done $utteranceId" }
            override fun onSpeechInterrupted(utteranceId: Int) { events += "cut $utteranceId" }
        })
    }

    @Test
    fun everySpokenSentenceIsShownAsText() {
        speaker.speak(1, "Ayarları açıyorum.")
        assertEquals("Ayarları açıyorum.", shown)
        assertEquals(listOf(1), inner.spoken)
    }

    @Test
    fun captionStaysAWhileAfterSpeechThenHides() {
        speaker.speak(1, "Tamam.")
        inner.heard?.onSpeechDone(1)

        assertEquals("Tamam.", shown)
        assertEquals(CaptionedSpeaker.CAPTION_LINGER_MS, timer.delay)
        timer.fire()
        assertNull(shown)
        assertEquals(listOf("done 1"), events)
    }

    @Test
    fun queuedSentenceIsShownWhenItsTurnComes() {
        speaker.speak(1, "Bir")
        speaker.speak(2, "İki")
        assertEquals("Bir", shown)

        inner.heard?.onSpeechDone(1)
        assertEquals("İki", shown)
    }

    @Test
    fun newSentenceCancelsPendingHide() {
        speaker.speak(1, "Bir")
        inner.heard?.onSpeechDone(1)
        speaker.speak(2, "İki")

        timer.fire()
        assertEquals("İki", shown)
    }

    @Test
    fun stopHidesCaptionAtOnceAndForwards() {
        speaker.speak(1, "Bir")
        speaker.stop()
        inner.heard?.onSpeechInterrupted(1)

        assertNull(shown)
        assertEquals(1, inner.stops)
        assertEquals(listOf("cut 1"), events)
    }

    @Test
    fun cutQuestionKeepsItsTextAWhile() {
        speaker.speak(1, "Haritaları açayım mı?")
        speaker.stopKeepingText()

        assertEquals(1, inner.stops)
        assertEquals("Haritaları açayım mı?", shown)
        assertEquals(CaptionedSpeaker.CAPTION_LINGER_MS, timer.delay)
        timer.fire()
        assertNull(shown)
    }

    @Test
    fun textStillShownWhenNoVoiceIsAvailable() {
        inner.doneAtOnce = true
        speaker.speak(5, "Sesim yok ama yazıyorum.")

        assertEquals("Sesim yok ama yazıyorum.", shown)
        assertEquals(listOf("done 5"), events)
    }
}
