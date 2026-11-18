package tr.yasliasistan.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tr.yasliasistan.app.listen.ListenSetupActivity
import tr.yasliasistan.app.listen.VoiceInputCoordinator
import tr.yasliasistan.app.listen.hasMicPermission
import tr.yasliasistan.app.metrics.AppMetrics
import tr.yasliasistan.app.overlay.OverlayCallbacks
import tr.yasliasistan.app.overlay.OverlayController
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.app.overlay.TouchTraceOverlay
import tr.yasliasistan.app.session.AssistantSession
import tr.yasliasistan.app.session.CachedAppsSource
import tr.yasliasistan.app.session.HandlerScheduler
import tr.yasliasistan.app.session.PackageManagerAppsSource
import tr.yasliasistan.app.session.ResourceTexts
import tr.yasliasistan.app.session.SessionPorts
import tr.yasliasistan.app.session.WorkerThread
import tr.yasliasistan.app.session.launchApp
import tr.yasliasistan.app.typing.TypeCommandActivity
import tr.yasliasistan.app.typing.TypedCommands
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.app.contacts.AndroidContactAccess
import tr.yasliasistan.app.contacts.ClarifyResourceTexts
import tr.yasliasistan.app.device.AndroidDeviceActions
import tr.yasliasistan.core.clarify.ClarificationEngine
import tr.yasliasistan.core.clarify.ClarificationRule
import tr.yasliasistan.core.contacts.ContactFinder
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.context.ConversationContext
import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.speech.recognition.SelectedSpeechEngine
import tr.yasliasistan.speech.tts.SpeechRate

/** In-process view of whether the system has bound our accessibility service. */
object AccessibilityServiceStatus {
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    internal fun set(value: Boolean) {
        mutableConnected.value = value
    }
}

/**
 * Hosts the floating bubble and "Dur" overlays (G-06) and the end-to-end assistant session (G-11).
 * G-19: the screen is read only through [screenReader], on request and only while a task is open
 * (NFG-53). The window-change events the XML config subscribes to are never read here; they only
 * keep the system's node cache fresh on Android 10-11.
 */
class AssistantAccessibilityService : AccessibilityService(), OverlayCallbacks, TypedCommands.Listener {

    private var overlay: OverlayController? = null
    private var captionJob: Job? = null
    private var voiceInput: VoiceInputCoordinator? = null
    private var session: AssistantSession? = null
    private var worker: WorkerThread? = null
    private var contacts: AndroidContactAccess? = null

    /** G-19 snapshot source for the executor (G-20); returns null when no task is open. */
    internal var screenReader: ScreenReader? = null
        private set

    /** G-20 executor; stopped by "Dur" (FG-23). */
    private var screenActions: ScreenActionRunner? = null

    /** G-21: touch dot and scroll arrow of "Sen yap" steps. */
    private var touchTrace: TouchTraceOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceStatus.set(true)
        overlay = OverlayController(this, this).also { it.show() }
        // G-08: every spoken sentence is also shown as text next to the bubble (NFG-04).
        captionJob = MainScope().launch { AssistantVoice.caption.collect { overlay?.showCaption(it) } }
        session = createSession()
        val reader = ScreenReader(this, isTaskActive = {
            session?.taskActive == true || screenActions?.debugTaskOpen == true || tr.yasliasistan.app.device.PhotoGuideTask.active
        })
        screenReader = reader
        ScreenActions.reader = reader
        // G-20: "Sen yap" executor; G-24 replaces the interim gate with the allowlist + bank block.
        // G-21/G-22: dot/arrow overlay and one-line narration of each verified step (worker -> main thread).
        val trace = TouchTraceOverlay(this).also { touchTrace = it; ScreenActions.guide = it }
        val main = Handler(Looper.getMainLooper())
        screenActions = ScreenActionRunner(
            this, reader, InterimPackageGate, session?.screenMetrics ?: ScreenMetrics.NONE,
            texts = StepResourceTexts(this),
            narrator = { text -> main.post { AssistantVoice.sayNext(text) } },
            indicator = trace,
        )
            .also { ScreenActions.runner = it }
        TypedCommands.register(this)
        SafeLog.event(TAG, "service connected")
    }

    /** G-11: bubble -> on-device speech -> dialog -> decision -> voice -> action. */
    private fun createSession(): AssistantSession {
        val texts = ResourceTexts(this)
        val apps = CachedAppsSource(PackageManagerAppsSource(this), SystemClock::elapsedRealtime)
        val contacts = AndroidContactAccess(this).also { contacts = it }
        // G-37: clarification rules (contacts, messaging apps, settings) first, then "open app".
        val clarify = ClarificationEngine(ContactFinder(contacts), apps, ClarifyResourceTexts(this))
        val rules = RuleLayer(listOf(ClarificationRule(clarify), OpenAppRule(apps)))
        // G-39 context, shared with the fixed "neden" command of G-17.
        val conversation = ConversationContext(texts)
        val decisions = DecisionCoordinator(DecisionEngine(rules), texts, context = conversation)
        val worker = WorkerThread().also { worker = it }
        return AssistantSession(
            scheduler = HandlerScheduler(),
            decisions = decisions,
            background = worker,
            speaker = AssistantVoice.speaker,
            texts = texts,
            ports = Ports(apps, contacts, worker),
            microphoneFactory = { ui ->
                // G-07/G-15: offline speech input (system recognizer or Whisper, EnginePolicy decides);
                // prepare() only checks availability, the mic stays closed.
                VoiceInputCoordinator(SelectedSpeechEngine(this), ::hasMicPermission, ui)
                    .also { it.prepare(); voiceInput = it }
            },
            clock = SystemClock::elapsedRealtime,
            metricsSink = AppMetrics.store, // G-18: numbers and codes only, kept on the device
            devices = AndroidDeviceActions(this),
            explain = conversation::explain,
        )
    }

    private inner class Ports(
        private val apps: CachedAppsSource,
        private val contacts: AndroidContactAccess,
        private val worker: WorkerThread,
    ) : SessionPorts {
        override fun render(state: OverlayState) {
            overlay?.render(state)
        }

        override fun launch(app: InstalledApp): Boolean = launchApp(app)

        override fun openTyping() = startOwnActivity(TypeCommandActivity::class.java)

        override fun openVoiceSetup() = startOwnActivity(ListenSetupActivity::class.java)

        // G-37: the address book (names only, in memory) is warmed too while the user talks.
        override fun slowDown() = AssistantVoice.setRate(SpeechRate.SLOW)

        override fun stopScreenActions() {
            screenActions?.stop()
        }

        override fun retellSteps() {
            screenActions?.retell()
        }

        override fun prefetchApps() = worker.execute {
            apps.launchableApps()
            contacts.prefetch()
        }
    }

    private fun startOwnActivity(type: Class<*>) {
        startActivity(
            Intent(this, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    override fun onBubbleTapped() {
        SafeLog.event(TAG, "bubble tapped")
        tr.yasliasistan.app.device.PhotoGuideTask.stop() // a new command ends a running guide
        session?.onBubbleTapped()
    }

    override fun onStopPressed() {
        SafeLog.event(TAG, "stop pressed")
        screenActions?.stop() // first: no further step may start (FG-23)
        tr.yasliasistan.app.device.PhotoGuideTask.stop()
        session?.onStopPressed()
    }

    override fun onTypePressed() {
        SafeLog.event(TAG, "type pressed")
        session?.onTypePressed()
    }

    override fun onConfirmTapped(yes: Boolean) {
        SafeLog.event(TAG, "confirm button pressed")
        session?.onConfirmTapped(yes)
    }

    override fun onTyped(text: String) {
        session?.onTyped(text)
    }

    override fun onTypingCancelled() {
        session?.onTypingCancelled()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onScreenChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty (NFG-53): events are never read; the screen is read only on request.
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        tearDown()
        SafeLog.event(TAG, "service disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        TypedCommands.register(null)
        ScreenActions.runner = null
        ScreenActions.guide = null
        ScreenActions.reader = null
        tr.yasliasistan.app.device.PhotoGuideTask.stop()
        screenActions?.shutdown()
        screenActions = null
        touchTrace?.release()
        touchTrace = null
        session?.close()
        session = null
        screenReader = null
        worker?.shutdown()
        worker = null
        contacts?.release()
        contacts = null
        voiceInput?.release()
        voiceInput = null
        captionJob?.cancel()
        captionJob = null
        overlay?.hide()
        overlay = null
        AccessibilityServiceStatus.set(false)
    }

    private companion object {
        const val TAG = "A11yService"
    }
}
