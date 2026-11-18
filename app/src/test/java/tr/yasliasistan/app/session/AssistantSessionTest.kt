package tr.yasliasistan.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.app.listen.MicIndicator
import tr.yasliasistan.app.listen.Microphone
import tr.yasliasistan.app.listen.VoiceInputUi
import tr.yasliasistan.app.listen.VoiceNotice
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionPhrases
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.decision.DecisionSource
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.metrics.ErrorCode
import tr.yasliasistan.core.metrics.MetricKind
import tr.yasliasistan.core.metrics.MetricOutcome
import tr.yasliasistan.core.metrics.MetricRecord
import tr.yasliasistan.core.dialog.Phrase
import tr.yasliasistan.core.dialog.Scheduler
import tr.yasliasistan.speech.tts.Speaker

/** End-to-end wiring of G-11 with fakes for Android parts (KT-1: "Ayarları aç"). */
class AssistantSessionTest {

    private val settings = InstalledApp("Ayarlar", "com.android.settings", "com.android.settings.Settings")
    private val maps = InstalledApp("Google Haritalar", "com.google.android.apps.maps", "Maps")
    private val apps = listOf(settings, maps, InstalledApp("WhatsApp", "com.whatsapp", "Main"))

    private val scheduler = ManualScheduler()
    private val speaker = FakeSpeaker()
    private val ports = FakePorts()
    private val mic = FakeMic()
    private val background = DeferrableBackground()
    private val session = AssistantSession(
        scheduler = scheduler,
        decisions = DecisionCoordinator(DecisionEngine(RuleLayer(listOf(OpenAppRule { apps }))), Phrases),
        background = background,
        speaker = speaker,
        texts = Texts,
        ports = ports,
        microphoneFactory = { ui -> mic.also { it.ui = ui } },
        clock = { 0L },
        metricsSink = { metrics += it },
    )
    private val metrics = mutableListOf<MetricRecord>()

    @Test
    fun metricsRecordSourceOutcomeAndCompletion() {
        session.onBubbleTapped()
        mic.open()
        mic.hear("Ayarları aç")
        val decision = metrics.single { it.kind == MetricKind.DECISION }
        assertEquals(DecisionSource.RULE, decision.source)
        assertEquals(MetricOutcome.ACT, decision.outcome)
        assertEquals(MetricOutcome.SUCCESS, metrics.single { it.kind == MetricKind.EXECUTION }.outcome)
        assertEquals(MetricOutcome.COMPLETED, metrics.single { it.kind == MetricKind.TASK_END }.outcome)
        assertTrue(metrics.any { it.kind == MetricKind.TAP_TO_LISTENING })
        assertTrue(metrics.any { it.kind == MetricKind.LISTEN_TO_TRANSCRIPT })
    }

    @Test
    fun screenMayBeReadOnlyWhileATaskIsOpen() {
        assertFalse(session.taskActive)
        session.onBubbleTapped()
        assertTrue(session.taskActive)
        session.onStopPressed()
        assertFalse(session.taskActive)
    }

    @Test
    fun screenMetricsRecordElementNotFound() {
        session.screenMetrics.elementNotFound()
        assertEquals(1, metrics.count { it.kind == MetricKind.ELEMENT_NOT_FOUND })
    }

    @Test
    fun lateDecisionAfterStopIsNotCountedAndStopIsWhileWaiting() {
        background.defer = true
        session.onBubbleTapped()
        mic.hear("ayarları aç")
        session.onStopPressed()
        background.release()
        assertTrue(metrics.none { it.kind == MetricKind.DECISION })
        assertEquals(MetricOutcome.STOPPED_WHILE_WAITING, metrics.single { it.kind == MetricKind.TASK_END }.outcome)
    }

    @Test
    fun failedLaunchIsAnExecutionError() {
        ports.launchWorks = false
        session.onTyped("ayarları aç")
        assertEquals(ErrorCode.APP_LAUNCH_FAILED, metrics.single { it.kind == MetricKind.ERROR }.errorCode)
        assertEquals(MetricOutcome.FAILED, metrics.single { it.kind == MetricKind.TASK_END }.outcome)
    }

    @Test
    fun micProblemIsAnEnvironmentError() {
        session.onBubbleTapped()
        mic.ui!!.showNotice(VoiceNotice.NEEDS_MIC_PERMISSION)
        assertEquals(ErrorCode.MIC_PERMISSION_MISSING, metrics.single { it.kind == MetricKind.ERROR }.errorCode)
        assertEquals(MetricOutcome.CANNOT_LISTEN, metrics.single { it.kind == MetricKind.TASK_END }.outcome)
    }

    @Test
    fun spokenOpenSettingsRunsEndToEnd() {
        session.onBubbleTapped()
        assertEquals(1, mic.starts)
        mic.open()
        assertEquals(OverlayState(sessionActive = true, listening = true, typeVisible = true), ports.last)

        mic.hear("Ayarları aç")

        assertEquals(listOf(settings), ports.launched)
        assertEquals(listOf("Ayarlar uygulamasını açıyorum.", "Ayarlar açıldı."), speaker.texts())
        assertFalse("session over, Dur hidden", ports.last.stopVisible)
        assertTrue(ports.prefetches >= 1)
    }

    @Test
    fun typedCommandWorksWithoutTheMicrophone() {
        session.onTyped("Ayarlari ac") // ASCII, as typed on a keyboard without Turkish letters
        assertEquals(0, mic.starts)
        assertEquals(listOf(settings), ports.launched)
    }

    @Test
    fun typeButtonClosesMicAndHidesBarWhileTyping() {
        session.onBubbleTapped()
        mic.open()
        session.onTypePressed()
        assertEquals(1, ports.typingOpened)
        assertEquals(1, mic.stops)
        assertFalse(ports.last.stopVisible)
        assertFalse(ports.last.typeVisible)

        session.onTyped("ayarları aç")
        assertEquals(listOf(settings), ports.launched)
    }

    @Test
    fun cancelledTypingStopsTheRunningSession() {
        session.onBubbleTapped()
        session.onTypePressed()
        session.onTypingCancelled()
        assertEquals(listOf("Durdum."), speaker.texts())
        assertFalse(ports.last.stopVisible)
    }

    @Test
    fun stopWhileListeningClosesMicAndSaysStopped() {
        session.onBubbleTapped()
        mic.open()
        session.onStopPressed()
        assertTrue(mic.stops >= 1)
        assertFalse(mic.isOpen)
        assertEquals(listOf("Durdum."), speaker.texts())
        assertTrue(ports.launched.isEmpty())
        assertFalse(ports.last.stopVisible)
    }

    @Test
    fun stopWhileDecidingDropsTheLateResult() {
        background.defer = true
        session.onBubbleTapped()
        mic.hear("ayarları aç")
        session.onStopPressed()
        background.release()
        assertTrue(ports.launched.isEmpty())
        assertEquals(listOf("Durdum."), speaker.texts())
    }

    @Test
    fun partialMatchAsksAndOpensOnlyAfterYes() {
        session.onBubbleTapped()
        mic.hear("haritaları aç")
        assertEquals(listOf("Google Haritalar uygulamasını açayım mı?"), speaker.texts())
        assertTrue(ports.launched.isEmpty())
        assertEquals("listens only after the question", 1, mic.starts)

        speaker.finishAll()
        assertEquals(2, mic.starts)
        mic.hear("evet")
        assertEquals(listOf(maps), ports.launched)
    }

    @Test
    fun silenceAfterTheQuestionMeansNo() {
        session.onBubbleTapped()
        mic.hear("haritaları aç")
        speaker.finishAll()
        scheduler.runAll()
        assertTrue(ports.launched.isEmpty())
        assertEquals("Tamam, yapmıyorum.", speaker.texts().last())
    }

    @Test
    fun nothingHeardSaysSoAndRetryIsOneTap() {
        session.onBubbleTapped()
        mic.nothing()
        assertEquals(listOf("Sizi duyamadım."), speaker.texts())
        session.onBubbleTapped()
        assertEquals(2, mic.starts)
    }

    @Test
    fun microphoneProblemIsSpokenAndSessionEnds() {
        session.onBubbleTapped()
        mic.ui!!.showNotice(VoiceNotice.NEEDS_MIC_PERMISSION)
        mic.ui!!.openVoiceSetup()
        assertEquals(listOf("notice:NEEDS_MIC_PERMISSION"), speaker.texts())
        assertEquals(1, ports.setupOpened)
        assertFalse(ports.last.stopVisible)
    }

    @Test
    fun secondTapWhileListeningEndsTheTurnInsteadOfRestarting() {
        session.onBubbleTapped()
        mic.open()
        session.onBubbleTapped()
        assertEquals(1, mic.finishes)
        assertEquals(1, mic.starts)
    }

    @Test
    fun failedLaunchIsReported() {
        ports.launchWorks = false
        session.onTyped("ayarları aç")
        assertEquals("Bunu yapamadım.", speaker.texts().last())
    }

    @Test
    fun unknownCommandSaysCannotDoYet() {
        session.onTyped("bana bir şarkı söyle")
        assertEquals(listOf("Bunu henüz yapamıyorum."), speaker.texts())
        assertTrue(ports.launched.isEmpty())
    }

    @Test
    fun ownSentencesWithNegativeIdsDoNotDriveTheDialog() {
        session.onBubbleTapped()
        mic.hear("haritaları aç")
        speaker.current!!.onSpeechDone(-1)
        assertEquals("still reading back", 1, mic.starts)
    }

    @Test
    fun closeSilencesEverything() {
        session.onBubbleTapped()
        session.close()
        session.onTyped("ayarları aç")
        assertTrue(ports.launched.isEmpty())
        assertTrue(speaker.stops >= 1)
    }

    // --- UD-1 fixes ---

    @Test
    fun partialOnlyCommandIsReadBackInsteadOfRun() { // Ö-1, NFG-18
        session.onBubbleTapped()
        mic.hear("Ayarları aç", partialOnly = true)
        assertTrue(ports.launched.isEmpty())
        assertEquals(listOf("Ayarlar uygulamasını açayım mı?"), speaker.texts())
    }

    @Test
    fun yesNoButtonsShowOnlyDuringTheQuestionAndConfirm() { // Ö-3
        session.onBubbleTapped()
        assertFalse(ports.last.confirmVisible)
        mic.hear("haritaları aç")
        assertTrue("visible while the question is read", ports.last.confirmVisible)
        speaker.finishAll()
        assertTrue(ports.last.confirmVisible)
        session.onConfirmTapped(true)
        assertEquals(listOf(maps), ports.launched)
        assertFalse(ports.last.confirmVisible)
    }

    @Test
    fun noButtonCancels() { // Ö-3
        session.onBubbleTapped()
        mic.hear("haritaları aç")
        session.onConfirmTapped(false)
        assertTrue(ports.launched.isEmpty())
        assertEquals("Tamam, yapmıyorum.", speaker.texts().last())
    }

    @Test
    fun typingAnAnswerIsNotSilence() { // Ö-4
        session.onBubbleTapped()
        mic.hear("haritaları aç")
        speaker.finishAll()
        session.onTypePressed()
        assertFalse("buttons hidden while typing", ports.last.confirmVisible)
        scheduler.runAll() // the 10 s confirmation timeout would have fired here
        assertTrue(speaker.texts().none { it == "Tamam, yapmıyorum." })
        session.onTyped("evet")
        assertEquals(listOf(maps), ports.launched)
    }

    // --- Fakes ---

    private object Texts : SessionTexts {
        override fun phrase(phrase: Phrase) = when (phrase) {
            Phrase.STOPPED -> "Durdum."
            Phrase.NOT_HEARD -> "Sizi duyamadım."
            Phrase.CANCELLED -> "Tamam, yapmıyorum."
            Phrase.FAILED -> "Bunu yapamadım."
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

    private class FakePorts : SessionPorts {
        var last = OverlayState.IDLE
        val launched = mutableListOf<InstalledApp>()
        var launchWorks = true
        var typingOpened = 0
        var setupOpened = 0
        var prefetches = 0
        override fun render(state: OverlayState) { last = state }
        override fun launch(app: InstalledApp): Boolean {
            if (launchWorks) launched += app
            return launchWorks
        }
        override fun openTyping() { typingOpened++ }
        override fun openVoiceSetup() { setupOpened++ }
        override fun prefetchApps() { prefetches++ }
    }

    private class FakeMic : Microphone {
        var ui: VoiceInputUi? = null
        var starts = 0
        var stops = 0
        var finishes = 0
        var isOpen = false
        private var running = false

        override fun startTurn() { starts++; running = true }
        override fun finishTurn(): Boolean {
            if (!running) return false
            finishes++
            return true
        }
        override fun stop() {
            stops++
            running = false
            isOpen = false
            ui!!.showMic(MicIndicator.CLOSED)
        }

        fun open() { isOpen = true; ui!!.showMic(MicIndicator.OPEN) }
        fun hear(text: String, partialOnly: Boolean = false) { end(); ui!!.onHeard(text, partialOnly) }
        fun nothing() { end(); ui!!.onNothingHeard() }
        private fun end() { running = false; isOpen = false; ui!!.showMic(MicIndicator.CLOSED) }
    }

    /** Speech that finishes only when the test says so, like a real TTS engine. */
    private class FakeSpeaker : Speaker {
        var current: Speaker.Listener? = null
        private val said = mutableListOf<String>()
        private val playing = ArrayDeque<Int>()
        var stops = 0
        override fun speak(utteranceId: Int, text: String) { said += text; playing += utteranceId }
        override fun stop() {
            stops++
            val cut = playing.toList()
            playing.clear()
            cut.forEach { current?.onSpeechInterrupted(it) }
        }
        override fun setListener(listener: Speaker.Listener?) { current = listener }
        fun texts(): List<String> = said.toList()
        fun finishAll() {
            while (playing.isNotEmpty()) current?.onSpeechDone(playing.removeFirst())
        }
    }

    private class DeferrableBackground : Background {
        var defer = false
        private val pending = mutableListOf<() -> Unit>()
        override fun <T> run(work: () -> T, deliver: (T) -> Unit) {
            if (defer) pending += { deliver(work()) } else deliver(work())
        }
        fun release() { pending.toList().forEach { it() }; pending.clear() }
    }

    private class ManualScheduler : Scheduler {
        private val tasks = mutableListOf<Pair<() -> Unit, BooleanArray>>()
        override fun schedule(delayMs: Long, action: () -> Unit): Scheduler.Cancellable {
            val cancelled = BooleanArray(1)
            tasks += action to cancelled
            return Scheduler.Cancellable { cancelled[0] = true }
        }
        fun runAll() {
            while (true) {
                val next = tasks.firstOrNull { !it.second[0] } ?: return
                tasks -= next
                next.first()
            }
        }
    }
}
