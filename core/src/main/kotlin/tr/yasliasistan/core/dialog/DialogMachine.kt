package tr.yasliasistan.core.dialog

import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.dialog.ConfirmationParser.Answer

/** Wait lengths used by the machine; defaults come from [Defaults]. */
data class DialogTimings(
    val confirmTimeoutMs: Long = Defaults.CONFIRM_TIMEOUT_MS,
    val progressAfterMs: Long = Defaults.PROGRESS_NOTICE_AFTER_MS,
    val progressRepeatMs: Long = Defaults.PROGRESS_REPEAT_MS,
)

/**
 * Dialog state machine (PLAN G-09; FG-07, FG-11, FG-16, FG-17, NFG-05, NFG-07, TK-16).
 *
 * Pure and single-threaded: [handle] maps one event to a list of effects. Time is not read here;
 * timers are requested as effects and come back as [DialogEvent.TimerFired] with a token, so tests can
 * drive time explicitly. Use [DialogSession] for scheduling and thread safety.
 *
 * Guarantees: an action is executed only after an explicit "yes" to the readback question, except
 * a certain Low-risk action the decision engine reports with [DialogEvent.Proceeding] (FG-71: tell and do);
 * silence until the confirmation timeout means "no" (paused while the typing screen is open);
 * a partial-only transcript never acts or confirms without a question (NFG-18); Stop returns to Idle from every state;
 * results, speech completions and timers that belong to an older step are ignored.
 */
class DialogMachine(private val timings: DialogTimings = DialogTimings()) {

    var state: DialogState = DialogState.Idle
        private set

    private var lastId = 0
    private var activeTimer = 0

    // The typing screen is open: typing is not silence, so no confirmation timeout runs (UD-1 Ö-4).
    private var typing = false

    // G-17 memory of this session, for "tekrar söyle" / "baştan anlat" / "neden".
    private var lastQuestion: Utterance? = null
    private var lastQuestionRequest = 0
    private var lastInfo: Utterance? = null
    private var explainToken = 0

    fun handle(event: DialogEvent): List<DialogEffect> {
        val out = mutableListOf<DialogEffect>()
        val before = state
        when (event) {
            DialogEvent.StopPressed -> stop(out)
            DialogEvent.BubbleTapped -> onTap(out)
            DialogEvent.TypingStarted -> onTypingStarted(out)
            is DialogEvent.TimerFired -> if (event.token != 0 && event.token == activeTimer) onTimer(out)
            is DialogEvent.TextEntered -> onTyped(event.text, out)
            is DialogEvent.CannotListen -> onCannotListen(event.reason, out)
            DialogEvent.UserInterrupted -> onUserInterrupted(out)
            is DialogEvent.Explained -> onExplained(event, out)
            else -> onInput(event, out)
        }
        if (state::class != before::class) {
            SafeLog.event(TAG, "${before::class.simpleName} -> ${state::class.simpleName}")
        }
        return out
    }

    private fun onTap(out: MutableList<DialogEffect>) {
        when (state) {
            DialogState.Idle -> startListening(out)
            is DialogState.Done -> {
                out += DialogEffect.CancelSpeech
                startListening(out)
            }
            else -> Unit // A tap during an active session changes nothing; "Dur" is the way out.
        }
    }

    private fun onTypingStarted(out: MutableList<DialogEffect>) {
        if (!state.sessionOpen) return
        typing = true
        if (state is DialogState.AwaitingConfirmation) cancelTimer(out)
    }

    /** FG-04: typed text is a command (or an answer) without the microphone. */
    private fun onTyped(text: String, out: MutableList<DialogEffect>) {
        typing = false
        when (state) {
            DialogState.Idle, is DialogState.Done -> {
                if (state is DialogState.Done) out += DialogEffect.CancelSpeech
                out += DialogEffect.SessionStarted
                forgetSession()
                state = DialogState.Listening
                onInput(DialogEvent.Heard(text), out)
            }
            else -> onInput(DialogEvent.Heard(text), out)
        }
    }

    private fun onCannotListen(reason: String, out: MutableList<DialogEffect>) {
        if (state != DialogState.Listening && state !is DialogState.AwaitingConfirmation) return
        out += DialogEffect.StopListening
        finish(out, Utterance.Text(reason)) // no action: the safe side (NFG-07)
    }

    private fun onInput(event: DialogEvent, out: MutableList<DialogEffect>) {
        // G-17: fixed commands come first, in every session state, on the device (no rules, no cloud).
        if (event is DialogEvent.Heard && state.sessionOpen) {
            val command = VoiceCommands.match(event.text)
            if (command != null && onVoiceCommand(command, out)) return
        }
        when (val s = state) {
            DialogState.Idle -> Unit
            DialogState.Listening -> onListening(event, out)
            is DialogState.Processing -> onProcessing(s, event, out)
            is DialogState.Replying -> onReplying(s, event, out)
            is DialogState.ReadingBack -> onReadingBack(s, event, out)
            is DialogState.AwaitingConfirmation -> onAwaiting(s, event, out)
            is DialogState.Working -> onWorking(s, event, out)
            is DialogState.Done -> if (event is DialogEvent.SpeechFinished && event.utteranceId == s.utteranceId) state = DialogState.Idle
        }
    }

    private fun onListening(event: DialogEvent, out: MutableList<DialogEffect>) {
        when (event) {
            is DialogEvent.Heard -> onCommand(event.text, out, event.partialOnly)
            DialogEvent.ListenFailed -> finish(out, Utterance.Template(Phrase.NOT_HEARD))
            else -> Unit
        }
    }

    private fun onCommand(text: String, out: MutableList<DialogEffect>, partialOnly: Boolean = false) {
        out += DialogEffect.StopListening
        if (text.isBlank()) {
            finish(out, Utterance.Template(Phrase.NOT_HEARD))
            return
        }
        val id = nextId()
        out += DialogEffect.Decide(id, text, partialOnly)
        state = DialogState.Processing(id)
        lastInfo = null
        scheduleTimer(out, timings.progressAfterMs)
        listenForCommands(out)
    }

    private fun onProcessing(s: DialogState.Processing, event: DialogEvent, out: MutableList<DialogEffect>) {
        when {
            event is DialogEvent.Understood && event.requestId == s.requestId -> {
                cancelTimer(out)
                readBack(out, s.requestId, Utterance.Text(event.readback), unclearAnswers = 0, lowRisk = event.lowRisk)
            }
            event is DialogEvent.Proceeding && event.requestId == s.requestId -> {
                cancelTimer(out)
                speakInfo(out, Utterance.Text(event.announcement))
                startWork(out, s.requestId)
            }
            event is DialogEvent.Heard || event == DialogEvent.ListenFailed -> listenForCommands(out)
            event is DialogEvent.Replied && event.requestId == s.requestId -> {
                cancelTimer(out)
                val reply = Utterance.Text(event.text)
                if (event.expectsAnswer) {
                    lastQuestionRequest = s.requestId
                    state = DialogState.Replying(s.requestId, speakQuestion(out, reply))
                } else {
                    finish(out, reply)
                }
            }
        }
    }

    private fun onReplying(s: DialogState.Replying, event: DialogEvent, out: MutableList<DialogEffect>) {
        when {
            event is DialogEvent.SpeechFinished && event.utteranceId == s.utteranceId -> {
                out += DialogEffect.StartListening
                state = DialogState.Listening
            }
            event is DialogEvent.Heard -> { // Answer while the question is still being spoken.
                out += DialogEffect.CancelSpeech
                onCommand(event.text, out, event.partialOnly)
            }
        }
    }

    private fun onReadingBack(s: DialogState.ReadingBack, event: DialogEvent, out: MutableList<DialogEffect>) {
        when {
            event is DialogEvent.SpeechFinished && event.utteranceId == s.utteranceId -> {
                state = DialogState.AwaitingConfirmation(s.requestId, s.unclearAnswers, s.lowRisk)
                if (!typing) { // While typing, the answer comes as text and no timeout runs (Ö-4).
                    out += DialogEffect.StartListening
                    scheduleTimer(out, timings.confirmTimeoutMs)
                }
            }
            event is DialogEvent.Heard || event is DialogEvent.ConfirmTapped -> { // Answer during the readback.
                out += DialogEffect.CancelSpeech
                answer(out, s.requestId, s.unclearAnswers, s.lowRisk, event)
            }
        }
    }

    private fun onAwaiting(s: DialogState.AwaitingConfirmation, event: DialogEvent, out: MutableList<DialogEffect>) {
        when (event) {
            is DialogEvent.Heard, is DialogEvent.ConfirmTapped -> answer(out, s.requestId, s.unclearAnswers, s.lowRisk, event)
            // The recognizer ended on its own silence limit; keep listening until the confirm timeout.
            DialogEvent.ListenFailed -> out += DialogEffect.StartListening
            else -> Unit
        }
    }

    private fun answer(
        out: MutableList<DialogEffect>,
        requestId: Int,
        unclearAnswers: Int,
        lowRisk: Boolean,
        event: DialogEvent,
    ) {
        val result = when (event) {
            is DialogEvent.ConfirmTapped -> if (event.yes) Answer.YES else Answer.NO
            is DialogEvent.Heard -> spokenAnswer(event, lowRisk)
            else -> return
        }
        out += DialogEffect.StopListening
        cancelTimer(out)
        explainToken = 0
        when {
            result == Answer.YES -> startWork(out, requestId)
            result == Answer.UNCLEAR && unclearAnswers == 0 ->
                readBack(out, requestId, Utterance.Template(Phrase.CONFIRM_AGAIN), unclearAnswers = 1, lowRisk = lowRisk)
            // "No", or a second unclear answer: do nothing (NFG-07, the safe side).
            else -> finish(out, Utterance.Template(Phrase.CANCELLED))
        }
    }

    /**
     * A spoken yes counts only from a final transcript (NFG-18; UD-1 Ö-1), and a soft assent
     * ("olur", "tamam") only for Low risk (V-12; UD-1 Ö-2). Otherwise it is asked once more.
     */
    private fun spokenAnswer(heard: DialogEvent.Heard, lowRisk: Boolean): Answer =
        when (val parsed = ConfirmationParser.parse(heard.text)) {
            Answer.NO, Answer.UNCLEAR -> parsed
            Answer.YES, Answer.SOFT_YES -> when {
                heard.partialOnly -> Answer.UNCLEAR
                parsed == Answer.SOFT_YES && !lowRisk -> Answer.UNCLEAR
                else -> Answer.YES
            }
        }

    private fun startWork(out: MutableList<DialogEffect>, requestId: Int) {
        out += DialogEffect.Execute(requestId)
        state = DialogState.Working(requestId)
        scheduleTimer(out, timings.progressAfterMs)
        listenForCommands(out)
    }

    private fun onWorking(s: DialogState.Working, event: DialogEvent, out: MutableList<DialogEffect>) {
        when {
            event is DialogEvent.ExecutionSucceeded && event.requestId == s.requestId ->
                finish(out, event.doneText?.let(Utterance::Text) ?: Utterance.Template(Phrase.DONE))
            event is DialogEvent.ExecutionFailed && event.requestId == s.requestId ->
                finish(out, event.reasonText?.let(Utterance::Text) ?: Utterance.Template(Phrase.FAILED))
            // A command turn ended without a command: keep listening while the work runs (G-17).
            event is DialogEvent.Heard || event == DialogEvent.ListenFailed -> listenForCommands(out)
        }
    }

    private fun onTimer(out: MutableList<DialogEffect>) {
        activeTimer = 0
        when (state) {
            is DialogState.Processing, is DialogState.Working -> {
                speakInfo(out, Utterance.Template(Phrase.PROGRESS))
                scheduleTimer(out, timings.progressRepeatMs)
            }
            is DialogState.AwaitingConfirmation -> { // Silence means "no".
                out += DialogEffect.StopListening
                finish(out, Utterance.Template(Phrase.CANCELLED))
            }
            else -> Unit
        }
    }

    private fun stop(out: MutableList<DialogEffect>, closing: Phrase = Phrase.STOPPED) {
        typing = false
        forgetSession()
        val s = state
        if (s == DialogState.Idle) return
        out += DialogEffect.CancelSpeech
        state = DialogState.Idle
        if (!s.sessionOpen) return
        out += DialogEffect.StopListening
        cancelTimer(out)
        when (s) {
            is DialogState.Processing -> out += DialogEffect.CancelWork(s.requestId)
            is DialogState.Working -> out += DialogEffect.CancelWork(s.requestId)
            else -> Unit
        }
        out += DialogEffect.SessionEnded
        speak(out, Utterance.Template(closing))
    }

    private fun startListening(out: MutableList<DialogEffect>) {
        forgetSession()
        out += DialogEffect.SessionStarted
        out += DialogEffect.StartListening
        state = DialogState.Listening
    }

    private fun readBack(
        out: MutableList<DialogEffect>,
        requestId: Int,
        utterance: Utterance,
        unclearAnswers: Int,
        lowRisk: Boolean,
    ) {
        lastQuestionRequest = requestId
        state = DialogState.ReadingBack(requestId, speakQuestion(out, utterance), unclearAnswers, lowRisk)
    }

    /** A question the user may answer before it ends: listen during it (G-16), unless typing. */
    private fun speakQuestion(out: MutableList<DialogEffect>, utterance: Utterance): Int {
        // "Evet mi, hayır mı?" is not worth repeating: "tekrar söyle" reads the real question again.
        if (utterance != Utterance.Template(Phrase.CONFIRM_AGAIN)) lastQuestion = utterance
        val id = speak(out, utterance)
        if (!typing) out += DialogEffect.ListenWhileSpeaking(id)
        return id
    }

    // --- G-17 fixed commands (FG-12, FG-13, FG-23, V-12) ---

    /**
     * Handles a fixed command; false lets the text take the normal path:
     * "dur"/"vazgeç" during a yes/no question is the "no" answer (same result: nothing is done),
     * and "neden" while listening or replying goes to the decision rules (G-39, on the device).
     */
    private fun onVoiceCommand(command: VoiceCommand, out: MutableList<DialogEffect>): Boolean {
        val confirming = state is DialogState.ReadingBack || state is DialogState.AwaitingConfirmation
        return when (command) {
            VoiceCommand.STOP -> if (confirming) false else true.also { stop(out) }
            VoiceCommand.REMEMBERED -> true.also { stop(out, Phrase.CLOSING) }
            VoiceCommand.REPEAT, VoiceCommand.RESTART -> sayAgain(out, restart = command == VoiceCommand.RESTART)
            VoiceCommand.SLOWER -> {
                out += DialogEffect.SlowDown
                sayAgain(out, restart = false)
            }
            VoiceCommand.WHY -> askWhy(out)
        }
    }

    /** "Tekrar söyle" / "baştan anlat": the question again, or the last information sentence. */
    private fun sayAgain(out: MutableList<DialogEffect>, restart: Boolean): Boolean {
        when (val s = state) {
            is DialogState.ReadingBack -> {
                out += DialogEffect.CancelSpeech
                askAgain(out, s.requestId, s.unclearAnswers, s.lowRisk)
            }
            is DialogState.AwaitingConfirmation -> {
                out += DialogEffect.StopListening
                cancelTimer(out)
                askAgain(out, s.requestId, s.unclearAnswers, s.lowRisk)
            }
            is DialogState.Replying -> {
                out += DialogEffect.CancelSpeech
                state = DialogState.Replying(s.requestId, speakQuestion(out, lastQuestion ?: return false))
            }
            DialogState.Listening -> {
                val question = lastQuestion
                if (question == null) {
                    out += DialogEffect.StartListening // nothing said yet: just keep listening
                } else {
                    out += DialogEffect.StopListening
                    state = DialogState.Replying(lastQuestionRequest, speakQuestion(out, question))
                }
            }
            is DialogState.Processing, is DialogState.Working -> {
                if (restart && s is DialogState.Working) out += DialogEffect.RestartFromFirstStep(s.requestId)
                out += DialogEffect.CancelSpeech
                speakInfo(out, lastInfo ?: Utterance.Template(Phrase.PROGRESS))
                listenForCommands(out)
            }
            DialogState.Idle, is DialogState.Done -> return false
        }
        return true
    }

    private fun askAgain(out: MutableList<DialogEffect>, requestId: Int, unclearAnswers: Int, lowRisk: Boolean) {
        val question = lastQuestion ?: Utterance.Template(Phrase.CONFIRM_AGAIN)
        readBack(out, requestId, question, unclearAnswers, lowRisk)
    }

    /** "Neden" during a yes/no question or while working: the G-39 explanation, then the same step. */
    private fun askWhy(out: MutableList<DialogEffect>): Boolean {
        when (val s = state) {
            is DialogState.ReadingBack, is DialogState.AwaitingConfirmation -> {
                out += DialogEffect.CancelSpeech
                out += DialogEffect.StopListening
                cancelTimer(out) // no "silence = no" while the explanation is fetched and said
                val (id, unclear, low) = when (s) {
                    is DialogState.ReadingBack -> Triple(s.requestId, s.unclearAnswers, s.lowRisk)
                    is DialogState.AwaitingConfirmation -> Triple(s.requestId, s.unclearAnswers, s.lowRisk)
                    else -> return false
                }
                state = DialogState.AwaitingConfirmation(id, unclear, low)
                requestExplanation(out)
            }
            is DialogState.Working -> {
                requestExplanation(out)
                listenForCommands(out)
            }
            // Still deciding: nothing to explain yet; say that it is working.
            is DialogState.Processing -> return sayAgain(out, restart = false)
            else -> return false
        }
        return true
    }

    private fun requestExplanation(out: MutableList<DialogEffect>) {
        explainToken = nextId()
        out += DialogEffect.Explain(explainToken)
    }

    private fun onExplained(event: DialogEvent.Explained, out: MutableList<DialogEffect>) {
        if (event.token == 0 || event.token != explainToken) return
        explainToken = 0
        when (val s = state) {
            is DialogState.AwaitingConfirmation -> {
                event.text?.let { speak(out, Utterance.Text(it)) }
                askAgain(out, s.requestId, s.unclearAnswers, s.lowRisk) // the question comes back
            }
            is DialogState.Working -> event.text?.let { speakInfo(out, Utterance.Text(it)) }
            else -> Unit
        }
    }

    /** An information sentence said while processing or working ("tekrar söyle" repeats it). */
    private fun speakInfo(out: MutableList<DialogEffect>, utterance: Utterance) {
        lastInfo = utterance
        speak(out, utterance)
    }

    private fun listenForCommands(out: MutableList<DialogEffect>) {
        if (!typing) out += DialogEffect.ListenForCommands
    }

    private fun forgetSession() {
        lastQuestion = null
        lastQuestionRequest = 0
        lastInfo = null
        explainToken = 0
    }

    /** Barge-in (FG-14): only a question being spoken is cut; the answer arrives as Heard. */
    private fun onUserInterrupted(out: MutableList<DialogEffect>) {
        if (state is DialogState.ReadingBack || state is DialogState.Replying) out += DialogEffect.CancelSpeech
    }

    /** Ends the session and speaks the closing sentence (FG-16). */
    private fun finish(out: MutableList<DialogEffect>, utterance: Utterance) {
        typing = false
        forgetSession()
        cancelTimer(out)
        out += DialogEffect.SessionEnded
        state = DialogState.Done(speak(out, utterance))
    }

    private fun speak(out: MutableList<DialogEffect>, utterance: Utterance): Int {
        val id = nextId()
        out += DialogEffect.Speak(id, utterance)
        return id
    }

    private fun scheduleTimer(out: MutableList<DialogEffect>, delayMs: Long) {
        activeTimer = nextId()
        out += DialogEffect.ScheduleTimer(activeTimer, delayMs)
    }

    private fun cancelTimer(out: MutableList<DialogEffect>) {
        if (activeTimer == 0) return
        activeTimer = 0
        out += DialogEffect.CancelTimer
    }

    private fun nextId(): Int {
        lastId = if (lastId == Int.MAX_VALUE) 1 else lastId + 1
        return lastId
    }

    private companion object {
        const val TAG = "Dialog"
    }
}
