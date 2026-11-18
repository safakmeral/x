package tr.yasliasistan.speech.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueuedSpeakerTest {

    private class FakeBackend : TtsBackend {
        lateinit var callbacks: TtsBackend.Callbacks
        val said = mutableListOf<Pair<String, String>>()
        var halts = 0
        var restarts = 0
        var lastRate = 1f
        var shutDown = false

        override fun start(callbacks: TtsBackend.Callbacks) {
            this.callbacks = callbacks
        }
        override fun restart() { restarts++ }
        override fun say(key: String, text: String) { said += key to text }
        override fun halt() { halts++ }
        override fun setRate(factor: Float) { lastRate = factor }
        override fun shutdown() { shutDown = true }

        fun keyOf(index: Int) = said[index].first
    }

    private class FakeDelay : Delay {
        var pending: (() -> Unit)? = null
        var delayMs = 0L
        override fun schedule(delayMs: Long, action: () -> Unit) {
            this.delayMs = delayMs
            pending = action
        }
        override fun cancel() { pending = null }
        fun fire() = pending?.also { pending = null }?.invoke()
    }

    private val delay = FakeDelay()
    private val log = mutableListOf<String>()
    private val listener = object : Speaker.Listener {
        override fun onSpeechStarted(utteranceId: Int) { log += "start $utteranceId" }
        override fun onSpeechDone(utteranceId: Int) { log += "done $utteranceId" }
        override fun onSpeechInterrupted(utteranceId: Int) { log += "cut $utteranceId" }
    }
    private val ready = VoiceStatus.Ready("com.google.android.tts", "tr-tr-x-local")

    private lateinit var backend: FakeBackend
    private lateinit var speaker: QueuedSpeaker

    @Before
    fun setUp() {
        backend = FakeBackend()
        speaker = QueuedSpeaker(backend, delay)
        speaker.setListener(listener)
        speaker.start()
    }

    @Test
    fun speechBeforeEngineIsReadyIsHeldThenSpokenInOrder() {
        speaker.speak(1, "bir")
        speaker.speak(2, "iki")
        assertTrue(backend.said.isEmpty())

        backend.callbacks.onStatus(ready)

        assertEquals(listOf("bir", "iki"), backend.said.map { it.second })
    }

    @Test
    fun slowEngineStartReleasesHeldSpeechAsDoneSoTheDialogGoesOn() {
        // A15 finding: with the screen off the engine may take a minute to start.
        speaker.speak(1, "bir")
        assertEquals(QueuedSpeaker.HELD_SPEECH_TIMEOUT_MS, delay.delayMs)

        delay.fire()
        backend.callbacks.onStatus(ready)

        assertEquals(listOf("done 1"), log)
        assertTrue(backend.said.isEmpty())
    }

    @Test
    fun timeoutIsCancelledWhenEngineBecomesReady() {
        speaker.speak(1, "bir")
        backend.callbacks.onStatus(ready)

        assertEquals(null, delay.pending)
    }

    @Test
    fun callbacksMapBackToUtteranceIds() {
        backend.callbacks.onStatus(ready)
        speaker.speak(7, "merhaba")
        val key = backend.keyOf(0)

        backend.callbacks.onStart(key)
        backend.callbacks.onDone(key)

        assertEquals(listOf("start 7", "done 7"), log)
    }

    @Test
    fun stopInterruptsCurrentAndQueuedOnceAndHaltsEngine() {
        backend.callbacks.onStatus(ready)
        speaker.speak(1, "bir")
        speaker.speak(2, "iki")

        speaker.stop()
        // Late engine callbacks for cut utterances must not report "done".
        backend.callbacks.onDone(backend.keyOf(0))
        backend.callbacks.onError(backend.keyOf(1))

        assertEquals(1, backend.halts)
        assertEquals(listOf("cut 1", "cut 2"), log)
    }

    @Test
    fun stopWhileCheckingDropsHeldSpeech() {
        speaker.speak(1, "bir")
        speaker.stop()
        backend.callbacks.onStatus(ready)

        assertTrue(backend.said.isEmpty())
        assertEquals(listOf("cut 1"), log)
    }

    @Test
    fun withoutTurkishVoiceNothingIsSpokenButDialogContinues() {
        speaker.speak(1, "bir")
        backend.callbacks.onStatus(VoiceStatus.MissingData("com.google.android.tts"))
        speaker.speak(2, "iki")

        assertTrue(backend.said.isEmpty())
        assertEquals(listOf("done 1", "done 2"), log)
    }

    @Test
    fun engineErrorEndsTheUtteranceSoTheDialogDoesNotHang() {
        backend.callbacks.onStatus(ready)
        speaker.speak(3, "üç")

        backend.callbacks.onError(backend.keyOf(0))

        assertEquals(listOf("done 3"), log)
    }

    @Test
    fun blankTextIsDoneAtOnce() {
        backend.callbacks.onStatus(ready)
        speaker.speak(4, "  ")

        assertTrue(backend.said.isEmpty())
        assertEquals(listOf("done 4"), log)
    }

    @Test
    fun unknownKeysAreIgnored() {
        backend.callbacks.onStatus(ready)
        backend.callbacks.onDone("warmup")
        backend.callbacks.onDone("u99")

        assertTrue(log.isEmpty())
    }

    @Test
    fun recheckStopsSpeechAndRestartsEngine() {
        backend.callbacks.onStatus(VoiceStatus.NotSupported)
        speaker.recheck()

        assertEquals(VoiceStatus.Checking, speaker.status)
        assertEquals(1, backend.restarts)
    }

    @Test
    fun rateIsPassedToEngineAndStatusIsReported() {
        val seen = mutableListOf<VoiceStatus>()
        speaker.setStatusListener { seen += it }
        speaker.setRate(SpeechRate.SLOW)
        backend.callbacks.onStatus(ready)

        assertEquals(0.8f, backend.lastRate)
        assertEquals(listOf(VoiceStatus.Checking, ready), seen)
    }

    @Test
    fun releaseStopsAndShutsDown() {
        backend.callbacks.onStatus(ready)
        speaker.speak(1, "bir")
        speaker.release()

        assertTrue(backend.shutDown)
        assertEquals(listOf("cut 1"), log)
    }
}
