package tr.yasliasistan.app.session

import tr.yasliasistan.app.listen.MicIndicator
import tr.yasliasistan.app.listen.Microphone
import tr.yasliasistan.app.listen.VoiceInputUi
import tr.yasliasistan.app.listen.VoiceNotice
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.dialog.DialogEffect
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.dialog.DialogMachine
import tr.yasliasistan.core.dialog.DialogSession
import tr.yasliasistan.core.dialog.DialogState
import tr.yasliasistan.core.dialog.Scheduler
import tr.yasliasistan.core.dialog.Utterance
import tr.yasliasistan.core.metrics.ErrorCode
import tr.yasliasistan.core.metrics.MetricsSink
import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.core.metrics.SessionMetrics
import tr.yasliasistan.speech.tts.Speaker

/**
 * End-to-end wiring of one assistant (PLAN G-11): bubble -> speech input -> dialog -> decision ->
 * spoken answer -> action. It owns the [DialogSession] and performs its effects; it holds no
 * dialog rules itself. Main thread only (results from [Background] arrive on the main thread).
 *
 * Privacy: transcripts and typed text go only to the dialog/decision code; nothing here logs them.
 * Metrics (G-18): the delays users feel and the dialog outcomes go to [metrics] as numbers and codes
 * only (V-06, NFG-23, NFG-27, FG-90).
 */
class AssistantSession(
    private val scheduler: Scheduler,
    private val decisions: DecisionCoordinator,
    private val background: Background,
    private val speaker: Speaker,
    private val texts: SessionTexts,
    private val ports: SessionPorts,
    microphoneFactory: (VoiceInputUi) -> Microphone,
    clock: () -> Long,
    metricsSink: MetricsSink = MetricsSink.NONE,
    private val devices: DeviceActions = DeviceActions.NONE,
    /** G-17 "neden": the G-39 layered explanation of the last answer (on the device); null = none. */
    private val explain: () -> String? = { null },
) {
    private val metrics = SessionMetrics(metricsSink, clock)

    /** G-19: content-free hook for the screen reader / element lookup (DEG-20). */
    val screenMetrics: ScreenMetrics get() = metrics

    /**
     * G-19 / NFG-53: true only while a session (task) is open; the screen reader reads nothing
     * otherwise. Updated on the main thread, readable from the worker thread.
     */
    @Volatile
    var taskActive: Boolean = false
        private set
    private val dialog = DialogSession(DialogMachine(), scheduler, ::perform).also { it.observer = metrics }
    private val microphone: Microphone = microphoneFactory(VoiceEvents())

    private val speech = SpeechTracker()
    private var micOpen = false
    private var cloudBusy = false
    private var typing = false
    private var closed = false

    // G-17: command listening starts a moment later, so an instant action never flashes the microphone.
    private var commandListen: Scheduler.Cancellable? = null

    init {
        speaker.setListener(SpeechEvents())
        render()
    }

    // --- Inputs from the overlay and the typing screen ---

    fun onBubbleTapped() {
        // G-16 sure fallback: a tap during a spoken question always cuts it, with or without barge-in.
        if (dialog.state.speaksQuestion()) {
            send(DialogEvent.UserInterrupted)
            return
        }
        if (microphone.finishTurn()) return // second tap while listening: "I am done talking"
        metrics.tapped()
        send(DialogEvent.BubbleTapped)
    }

    fun onStopPressed() = send(DialogEvent.StopPressed)

    /**
     * "Yazarak söyle" (FG-04): close the microphone and open the typing screen. The dialog pauses the
     * yes/no timeout while the user types (UD-1 Ö-4).
     */
    fun onTypePressed() {
        typing = true
        microphone.stop()
        ports.openTyping()
        send(DialogEvent.TypingStarted)
    }

    /** "Evet" / "Hayır" buttons of the confirmation question (FG-07; UD-1 Ö-3). */
    fun onConfirmTapped(yes: Boolean) = send(DialogEvent.ConfirmTapped(yes))

    /** G-34 (NFG-58): a cloud request started/finished; the bubble shows it. Main thread. */
    fun onCloudBusy(busy: Boolean) {
        if (closed || cloudBusy == busy) return
        cloudBusy = busy
        render()
    }

    fun onTyped(text: String) {
        typing = false
        if (text.isBlank()) {
            onTypingCancelled()
            return
        }
        metrics.commandReceived(spoken = false)
        send(DialogEvent.TextEntered(text))
    }

    /** The typing screen closed without text: same as "Dur" when a session is running. */
    fun onTypingCancelled() {
        typing = false
        if (dialog.state.sessionOpen) send(DialogEvent.StopPressed) else render()
    }

    fun close() {
        closed = true
        taskActive = false
        cancelCommandListening()
        microphone.stop()
        speaker.setListener(null)
        speaker.stop()
    }

    // --- Dialog effects ---

    private fun perform(effect: DialogEffect) {
        if (closed) return
        when (effect) {
            DialogEffect.SessionStarted -> {
                decisions.reset()
                ports.prefetchApps()
            }
            DialogEffect.SessionEnded -> {
                ports.stopScreenActions() // FG-23: a spoken "dur" stops running steps too
                cancelCommandListening()
                speech.sessionEnded()
                microphone.stop()
            }
            DialogEffect.StartListening -> {
                cancelCommandListening()
                if (!typing) microphone.startTurn()
            }
            DialogEffect.StopListening -> {
                cancelCommandListening()
                microphone.stop()
            }
            DialogEffect.ListenForCommands -> if (!typing && LISTEN_WHILE_SPEAKING) listenForCommandsSoon()
            is DialogEffect.Explain -> background.run(work = explain, deliver = { send(DialogEvent.Explained(effect.token, it)) })
            DialogEffect.SlowDown -> ports.slowDown()
            // G-22: "Sen yap" steps done so far are told again from the first one.
            is DialogEffect.RestartFromFirstStep -> ports.retellSteps()
            // G-16: the microphone opens when the question's sound starts (see SpeechEvents).
            is DialogEffect.ListenWhileSpeaking -> if (!typing && LISTEN_WHILE_SPEAKING) speech.listenWhenStarted(effect.utteranceId)
            is DialogEffect.Speak -> {
                val text = textOf(effect.utterance)
                speech.queued(effect.utteranceId, text)
                speaker.speak(effect.utteranceId, text)
            }
            // A question cut short but still open keeps its caption next to the Evet/Hayır buttons.
            DialogEffect.CancelSpeech -> if (dialog.state.asksYesNo() || dialog.state.speaksQuestion()) speaker.stopKeepingText() else speaker.stop()
            is DialogEffect.Decide -> decide(effect)
            is DialogEffect.Execute -> execute(effect.requestId)
            // Results of a cancelled request are ignored by the dialog (request ids); running steps stop.
            is DialogEffect.CancelWork -> ports.stopScreenActions()
            // Handled inside DialogSession; never forwarded.
            is DialogEffect.ScheduleTimer, DialogEffect.CancelTimer -> Unit
        }
    }

    private fun decide(effect: DialogEffect.Decide) {
        background.run(
            work = { decisions.decideTraced(effect.requestId, effect.transcript, effect.partialOnly) },
            deliver = { traced ->
                if (!closed && dialog.state == DialogState.Processing(effect.requestId)) {
                    metrics.decided(traced.source, traced.event) // only results the dialog still waits for
                    if (traced.unnecessaryQuestion) metrics.unnecessaryQuestion() // FG-102
                }
                send(traced.event)
            },
        )
    }

    private fun execute(requestId: Int) {
        metrics.executionStarted()
        var error: ErrorCode? = null
        val taken = decisions.takeAction(requestId)
        if (taken is Action.SendPhoto) {
            // Demo: several "Sen yap" screen steps; the result comes later on the main thread.
            devices.runAsync(taken) { outcome ->
                val ok = outcome is DeviceResult.Done
                metrics.executionFinished(success = ok, error = if (ok) null else ErrorCode.DEVICE_ACTION_FAILED)
                send(
                    when (outcome) {
                        is DeviceResult.Done -> DialogEvent.ExecutionSucceeded(requestId, outcome.text)
                        is DeviceResult.Failed -> DialogEvent.ExecutionFailed(requestId, outcome.text)
                    },
                )
            }
            return
        }
        val result = when (val action = taken) {
            is Action.OpenApp ->
                if (ports.launch(action.app)) {
                    DialogEvent.ExecutionSucceeded(requestId, texts.appOpened(action.spokenName))
                } else {
                    error = ErrorCode.APP_LAUNCH_FAILED
                    DialogEvent.ExecutionFailed(requestId, null)
                }
            null -> {
                error = ErrorCode.ACTION_MISSING
                DialogEvent.ExecutionFailed(requestId, null)
            }
            // G-37: dialer, message screen, volume, settings screen, contacts permission screen.
            else -> when (val outcome = devices.run(action)) {
                is DeviceResult.Done -> DialogEvent.ExecutionSucceeded(requestId, outcome.text)
                is DeviceResult.Failed -> {
                    error = ErrorCode.DEVICE_ACTION_FAILED
                    DialogEvent.ExecutionFailed(requestId, outcome.text)
                }
            }
        }
        metrics.executionFinished(success = error == null, error = error)
        send(result)
    }

    private fun listenForCommandsSoon() {
        commandListen?.cancel()
        commandListen = scheduler.schedule(COMMAND_LISTEN_DELAY_MS) {
            commandListen = null
            if (!closed && !typing && dialog.state.listensForCommands()) {
                speech.commandListening()
                microphone.startCommandTurn(speech::echoReference)
            }
        }
    }

    private fun cancelCommandListening() {
        commandListen?.cancel()
        commandListen = null
    }

    private fun textOf(utterance: Utterance): String = when (utterance) {
        is Utterance.Text -> utterance.text
        is Utterance.Template -> texts.phrase(utterance.phrase)
    }

    private fun send(event: DialogEvent) {
        if (closed) return
        dialog.send(event)
        render()
    }

    private fun render() {
        val state = dialog.state
        taskActive = !closed && state.sessionOpen
        ports.render(
            OverlayState(
                sessionActive = state.sessionOpen && !typing,
                listening = micOpen,
                typeVisible = !typing && state.acceptsInput(),
                confirmVisible = !typing && state.asksYesNo(),
                cloudBusy = cloudBusy,
            ),
        )
    }

    /** A yes/no question is being read or answered (UD-1 Ö-3: on-screen "Evet" / "Hayır"). */
    private fun DialogState.asksYesNo() = this is DialogState.ReadingBack || this is DialogState.AwaitingConfirmation

    /** A question is being spoken; the user may talk over it (G-16). */
    private fun DialogState.speaksQuestion() = this is DialogState.ReadingBack || this is DialogState.Replying

    /** G-17: the assistant works; only fixed commands are listened for. */
    private fun DialogState.listensForCommands() = this is DialogState.Processing || this is DialogState.Working

    /** States in which a typed command or answer is useful. */
    private fun DialogState.acceptsInput() = this == DialogState.Listening ||
        this is DialogState.ReadingBack || this is DialogState.AwaitingConfirmation ||
        this is DialogState.Replying

    private inner class VoiceEvents : VoiceInputUi {
        override fun showMic(state: MicIndicator) {
            micOpen = state == MicIndicator.OPEN
            if (micOpen) metrics.listening()
            render()
        }

        override fun onPartialHeard() = metrics.partialHeard()

        override fun onHeard(text: String, partialOnly: Boolean) {
            metrics.commandReceived(spoken = true)
            send(DialogEvent.Heard(text, partialOnly))
        }

        override fun onNothingHeard() = send(DialogEvent.ListenFailed)

        override fun onUserInterrupted() = send(DialogEvent.UserInterrupted)

        override fun showNotice(notice: VoiceNotice) {
            metrics.error(errorCodeOf(notice))
            send(DialogEvent.CannotListen(texts.notice(notice)))
        }

        override fun openVoiceSetup() = ports.openVoiceSetup()
    }

    private inner class SpeechEvents : Speaker.Listener {
        override fun onSpeechStarted(utteranceId: Int) {
            metrics.speechStarted()
            if (speech.started(utteranceId) && !closed && !typing && dialog.state.speaksQuestion()) {
                microphone.startBargeInTurn(speech::echoReference)
            }
        }

        // Negative ids are the app's own sentences (AssistantVoice.sayNow), not dialog utterances.
        override fun onSpeechDone(utteranceId: Int) {
            speech.ended(utteranceId)
            if (utteranceId > 0) send(DialogEvent.SpeechFinished(utteranceId))
        }

        // An interrupted sentence is over too; the dialog ignores ids it no longer waits for.
        override fun onSpeechInterrupted(utteranceId: Int) = onSpeechDone(utteranceId)
    }
}

/** Delay before command listening opens (G-17); instant actions finish before it. */
internal const val COMMAND_LISTEN_DELAY_MS = 400L

/**
 * Device finding 18.11.2026 (Galaxy A15): opening the recognizer while the assistant speaks silences
 * the assistant's voice (only the caption is seen). Until this is solved, the microphone opens only
 * after the sentence ends (G-16 barge-in and G-17 command listening off). "Dur" button always works.
 * Recorded in docs/SAPMALAR.md.
 */
internal var LISTEN_WHILE_SPEAKING = false

/** Metric error code for a voice-input problem (FG-92: all are environment causes). */
internal fun errorCodeOf(notice: VoiceNotice): ErrorCode = when (notice) {
    VoiceNotice.NEEDS_MIC_PERMISSION -> ErrorCode.MIC_PERMISSION_MISSING
    VoiceNotice.LANGUAGE_PACK_MISSING,
    VoiceNotice.LANGUAGE_DOWNLOADING,
    VoiceNotice.OFFLINE_NOT_SUPPORTED -> ErrorCode.SPEECH_MODEL_MISSING
    VoiceNotice.MIC_BUSY, VoiceNotice.MIC_BLOCKED -> ErrorCode.MIC_BUSY
    VoiceNotice.NOTHING_HEARD -> ErrorCode.NOTHING_HEARD
    VoiceNotice.FAILED -> ErrorCode.SPEECH_FAILED
}
