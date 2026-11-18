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

/** G-17 with the real dialog, session and VoiceInputCoordinator; Android parts are fakes. */
class VoiceCommandSessionTest {
    // Listening during speech is off on devices for now (LISTEN_WHILE_SPEAKING); logic still tested.
    @org.junit.Before fun on() { LISTEN_WHILE_SPEAKING = true }
    @org.junit.After fun off() { LISTEN_WHILE_SPEAKING = false }


    private val settings = InstalledApp("Ayarlar", "com.android.settings", "com.android.settings.Settings")
    private val maps = InstalledApp("Google Haritalar", "com.google.android.apps.maps", "Maps")
    private val engine = FakeEngine()
    private val speaker = FakeSpeaker()
    private val ports = Ports()
    private val timers = ManualScheduler()
    private val background = HeldBackground()
    private val session = AssistantSession(
        scheduler = timers,
        decisions = DecisionCoordinator(DecisionEngine(RuleLayer(listOf(OpenAppRule { listOf(settings, maps) }))), Phrases),
        background = background,
        speaker = speaker,
        texts = Texts,
        ports = ports,
        microphoneFactory = { ui -> VoiceInputCoordinator(engine, { true }, ui).also { it.prepare() } },
        clock = { 0L },
        explain = { "Siz istediniz." },
    )

    private fun say(text: String) {
        engine.listener!!.onListening()
        engine.finish(SpeechOutcome.Heard(text))
    }

    @Test
    fun stopWhileDecidingWorksByVoiceAtOnce() {
        background.hold = true
        session.onBubbleTapped()
        say("ayarları aç")
        timers.fireAll()
        assertEquals("command listening opened", 2, engine.starts)
        assertTrue(engine.config!!.duringSpeech)
        engine.listener!!.onListening()
        assertTrue("indicator visible", ports.last.listening)

        engine.listener!!.onPartialText("dur")
        assertEquals("Durdum.", speaker.said.last())
        assertTrue("running steps stop too (FG-23)", ports.actionStops > 0)
        assertFalse(ports.last.sessionActive)
        assertFalse(ports.last.listening)

        background.release() // the late decision does nothing
        assertTrue(ports.launched.isEmpty())
    }

    @Test
    fun instantActionNeverOpensTheMicrophoneAgain() {
        session.onBubbleTapped()
        say("ayarları aç")
        assertEquals(listOf(settings), ports.launched)
        timers.fireAll()
        assertEquals(1, engine.starts)
    }

    @Test
    fun repeatWhileDecidingSaysItIsWorking() {
        background.hold = true
        session.onBubbleTapped()
        say("ayarları aç")
        timers.fireAll()
        say("tekrar söyle")
        assertEquals("PROGRESS", speaker.said.last())
        timers.fireAll()
        assertEquals("listens again", 3, engine.starts)
    }

    @Test
    fun whyDuringTheQuestionExplainsAndAsksAgain() {
        session.onBubbleTapped()
        say("haritaları aç")
        speaker.startPlaying()
        say("neden")
        val tail = speaker.said.takeLast(2)
        assertEquals(listOf("Siz istediniz.", "Google Haritalar uygulamasını açayım mı?"), tail)
        assertTrue(ports.last.confirmVisible)
        assertTrue(ports.launched.isEmpty())
    }

    @Test
    fun rememberedClosesPolitely() {
        session.onBubbleTapped()
        say("haritaları aç")
        speaker.startPlaying()
        say("tamam hatırladım")
        assertEquals("CLOSING", speaker.said.last())
        assertFalse(ports.last.sessionActive)
        assertTrue(ports.launched.isEmpty())
    }

    @Test
    fun slowerSetsSlowSpeechAndRepeats() {
        session.onBubbleTapped()
        say("haritaları aç")
        speaker.startPlaying()
        say("yavaş konuş")
        assertEquals(1, ports.slowDowns)
        assertEquals("Google Haritalar uygulamasını açayım mı?", speaker.said.last())
    }

    @Test
    fun interruptedQuestionKeepsItsCaptionAndButtons() {
        session.onBubbleTapped()
        say("haritaları aç")
        speaker.startPlaying()
        engine.listener!!.onListening()
        engine.listener!!.onUserVoice()
        assertEquals(1, speaker.keptTextStops)
        assertTrue(ports.last.confirmVisible)
    }

    // --- Fakes ---

    private class FakeEngine : SpeechEngine {
        var starts = 0
        var listener: SpeechListener? = null
        var config: ListenConfig? = null

        override fun checkAvailability(onResult: (EngineAvailability) -> Unit) = onResult(EngineAvailability.AVAILABLE)

        override fun startListening(listener: SpeechListener, config: ListenConfig) {
            starts++
            this.listener = listener
            this.config = config
        }

        fun finish(outcome: SpeechOutcome) = listener!!.onOutcome(outcome)
        override fun finishListening() = Unit
        override fun cancel() {
            listener?.onOutcome(SpeechOutcome.Cancelled)
        }
        override fun release() = Unit
    }

    private class FakeSpeaker : Speaker {
        private var listener: Speaker.Listener? = null
        private val queue = ArrayDeque<Int>()
        val said = mutableListOf<String>()
        var keptTextStops = 0

        override fun speak(utteranceId: Int, text: String) {
            said += text
            queue += utteranceId
        }

        fun startPlaying() = queue.lastOrNull()?.let { listener?.onSpeechStarted(it) }

        override fun stop() {
            val cut = queue.toList()
            queue.clear()
            cut.forEach { listener?.onSpeechInterrupted(it) }
        }

        override fun stopKeepingText() {
            keptTextStops++
            stop()
        }

        override fun setListener(listener: Speaker.Listener?) { this.listener = listener }
    }

    private class Ports : SessionPorts {
        var last = OverlayState.IDLE
        val launched = mutableListOf<InstalledApp>()
        var slowDowns = 0
        var actionStops = 0
        override fun stopScreenActions() { actionStops++ }
        override fun render(state: OverlayState) { last = state }
        override fun launch(app: InstalledApp): Boolean = launched.add(app)
        override fun openTyping() = Unit
        override fun openVoiceSetup() = Unit
        override fun prefetchApps() = Unit
        override fun slowDown() { slowDowns++ }
    }

    /** Runs work at once, or holds the results until [release] (a slow decision). */
    private class HeldBackground : Background {
        var hold = false
        private val held = mutableListOf<() -> Unit>()
        override fun <T> run(work: () -> T, deliver: (T) -> Unit) {
            if (hold) held += { deliver(work()) } else deliver(work())
        }
        fun release() {
            hold = false
            held.toList().also { held.clear() }.forEach { it() }
        }
    }

    private class ManualScheduler : Scheduler {
        private val pending = mutableListOf<Pair<Long, () -> Unit>>()
        override fun schedule(delayMs: Long, action: () -> Unit): Scheduler.Cancellable {
            val entry = delayMs to action
            pending += entry
            return Scheduler.Cancellable { pending.remove(entry) }
        }

        /** Fires only the G-17 command-listening delay (dialog progress timers stay pending). */
        fun fireAll() {
            val due = pending.filter { it.first == COMMAND_LISTEN_DELAY_MS }
            pending.removeAll(due)
            due.forEach { it.second() }
        }
    }

    private object Texts : SessionTexts {
        override fun phrase(phrase: Phrase) = when (phrase) {
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
