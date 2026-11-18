package tr.yasliasistan.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.app.listen.VoiceInputCoordinator
import tr.yasliasistan.app.listen.VoiceNotice
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionPhrases
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.dialog.Phrase
import tr.yasliasistan.core.dialog.Scheduler
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.recognition.ListenConfig
import tr.yasliasistan.speech.recognition.SpeechEngine
import tr.yasliasistan.speech.recognition.SpeechListener
import tr.yasliasistan.speech.recognition.SpeechOutcome
import tr.yasliasistan.speech.tts.Speaker

/**
 * G-16 acceptance (unit part): speaking -> user voice -> speech silenced within 300 ms, with the real
 * dialog, session and VoiceInputCoordinator; Android parts are fakes driven by a test clock.
 */
class BargeInSessionTest {
    // Barge-in is off on devices for now (LISTEN_WHILE_SPEAKING); its logic is still tested here.
    @org.junit.Before fun on() { LISTEN_WHILE_SPEAKING = true }
    @org.junit.After fun off() { LISTEN_WHILE_SPEAKING = false }


    private val settings = InstalledApp("Ayarlar", "com.android.settings", "com.android.settings.Settings")
    private val maps = InstalledApp("Google Haritalar", "com.google.android.apps.maps", "Maps")
    private var now = 0L
    private val engine = ClockEngine()
    private val speaker = ClockSpeaker()
    private val ports = Ports()
    private val session = AssistantSession(
        scheduler = NoTimers,
        decisions = DecisionCoordinator(DecisionEngine(RuleLayer(listOf(OpenAppRule { listOf(settings, maps) }))), Phrases),
        background = Direct,
        speaker = speaker,
        texts = Texts,
        ports = ports,
        microphoneFactory = { ui -> VoiceInputCoordinator(engine, { true }, ui).also { it.prepare() } },
        clock = { now },
    )

    /** Tap, say "haritaları aç", the readback question starts playing at [now]. */
    private fun questionPlaying() {
        session.onBubbleTapped()
        engine.listener!!.onListening()
        engine.finish(SpeechOutcome.Heard("haritaları aç"))
        speaker.startPlaying()
    }

    @Test
    fun userVoiceSilencesTheQuestionWithin300Ms() {
        questionPlaying()
        assertEquals("barge-in listening opened during the question", 2, engine.starts)
        assertTrue(engine.config!!.duringSpeech)
        now += 120
        engine.listener!!.onListening()
        assertTrue("listening indicator visible", ports.last.listening)

        now += 900 // the assistant's own voice comes back first...
        engine.listener!!.onPartialText("google haritalar uygulamasını")
        assertEquals(0, speaker.stops)

        now += 400 // ...then the user says "hayır"
        val userSpokeAt = now
        engine.listener!!.onPartialText("google haritalar uygulamasını hayır")
        assertEquals(1, speaker.stops)
        assertTrue("silenced after ${speaker.stoppedAt!! - userSpokeAt} ms", speaker.stoppedAt!! - userSpokeAt <= 300)

        engine.finish(SpeechOutcome.Heard("google haritalar uygulamasını hayır"))
        assertTrue(ports.launched.isEmpty())
        assertEquals("Tamam, yapmıyorum.", speaker.said.last())
        assertFalse("microphone off after the session", ports.last.listening)
        assertFalse(ports.last.sessionActive)
    }

    @Test
    fun yesSpokenOverTheQuestionConfirms() {
        questionPlaying()
        engine.listener!!.onListening()
        engine.listener!!.onUserVoice() // Whisper: stricter detector heard the user
        assertEquals(1, speaker.stops)
        engine.finish(SpeechOutcome.Heard("evet"))
        assertEquals(listOf(maps), ports.launched)
    }

    @Test
    fun withoutInterruptionAFreshTurnListensAfterTheQuestion() {
        questionPlaying()
        engine.listener!!.onListening()
        engine.listener!!.onPartialText("google haritalar") // echo only
        speaker.finishAll()
        assertEquals(1, engine.cancels)
        assertEquals(3, engine.starts)
        assertFalse(engine.config!!.duringSpeech)
        engine.listener!!.onListening()
        engine.finish(SpeechOutcome.Heard("evet"))
        assertEquals(listOf(maps), ports.launched)
    }

    @Test
    fun bubbleTapAlwaysCutsTheQuestion() {
        engine.available = false // barge-in listening impossible on this phone
        questionPlaying()
        assertEquals(1, engine.starts)
        session.onBubbleTapped()
        assertEquals(1, speaker.stops)
        assertTrue(ports.last.sessionActive) // still waiting for the answer
    }

    @Test
    fun stopCutsTheQuestionAndClosesTheMicrophone() {
        questionPlaying()
        engine.listener!!.onListening()
        session.onStopPressed()
        assertTrue(speaker.stops >= 1)
        assertEquals(1, engine.cancels)
        assertFalse(ports.last.listening)
    }

    @Test
    fun noListeningOutsideASession() {
        assertEquals(0, engine.starts)
        assertFalse(ports.last.listening)
    }

    // --- Fakes ---

    private inner class ClockEngine : SpeechEngine {
        var starts = 0
        var cancels = 0
        var available = true
        var listener: SpeechListener? = null
        var config: ListenConfig? = null

        override fun checkAvailability(onResult: (EngineAvailability) -> Unit) = onResult(EngineAvailability.AVAILABLE)

        override fun startListening(listener: SpeechListener, config: ListenConfig) {
            if (config.duringSpeech && !available) {
                listener.onOutcome(SpeechOutcome.Failed(tr.yasliasistan.speech.recognition.FailureReason.AUDIO))
                return
            }
            starts++
            this.listener = listener
            this.config = config
        }

        fun finish(outcome: SpeechOutcome) = listener!!.onOutcome(outcome)
        override fun finishListening() = Unit
        override fun cancel() {
            cancels++
            listener?.onOutcome(SpeechOutcome.Cancelled)
        }
        override fun release() = Unit
    }

    private inner class ClockSpeaker : Speaker {
        private var listener: Speaker.Listener? = null
        private val queue = ArrayDeque<Int>()
        val said = mutableListOf<String>()
        var stops = 0
        var stoppedAt: Long? = null

        override fun speak(utteranceId: Int, text: String) {
            said += text
            queue += utteranceId
        }

        fun startPlaying() = queue.firstOrNull()?.let { listener?.onSpeechStarted(it) }

        fun finishAll() {
            while (queue.isNotEmpty()) listener?.onSpeechDone(queue.removeFirst())
        }

        override fun stop() {
            stops++
            stoppedAt = now
            val cut = queue.toList()
            queue.clear()
            cut.forEach { listener?.onSpeechInterrupted(it) }
        }

        override fun setListener(listener: Speaker.Listener?) { this.listener = listener }
    }

    private class Ports : SessionPorts {
        var last = OverlayState.IDLE
        val launched = mutableListOf<InstalledApp>()
        override fun render(state: OverlayState) { last = state }
        override fun launch(app: InstalledApp): Boolean = launched.add(app)
        override fun openTyping() = Unit
        override fun openVoiceSetup() = Unit
        override fun prefetchApps() = Unit
    }

    private object Direct : Background {
        override fun <T> run(work: () -> T, deliver: (T) -> Unit) = deliver(work())
    }

    private object NoTimers : Scheduler {
        override fun schedule(delayMs: Long, action: () -> Unit) = Scheduler.Cancellable { }
    }

    private object Texts : SessionTexts {
        override fun phrase(phrase: Phrase) = when (phrase) {
            Phrase.CANCELLED -> "Tamam, yapmıyorum."
            Phrase.STOPPED -> "Durdum."
            else -> phrase.name
        }
        override fun appOpened(label: String) = "$label açıldı."
        override fun notice(notice: VoiceNotice) = "notice:${notice.name}"
    }

    private object Phrases : DecisionPhrases {
        override fun openingApp(label: String) = "$label uygulamasını açıyorum."
        override fun confirmOpenApp(label: String) = "$label uygulamasını açayım mı?"
        override fun chooseApp(labels: List<String>) = "Hangisini açayım: ${labels.joinToString()}?"
        override fun tooManyMatches() = "Adını tam söyler misiniz?"
        override fun cannotDoYet() = "Bunu henüz yapamıyorum."
        override fun cancelled() = "Tamam, yapmıyorum."
    }
}
