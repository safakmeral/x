package tr.yasliasistan.core.metrics

import tr.yasliasistan.core.decision.DecisionSource
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.dialog.DialogSession
import tr.yasliasistan.core.dialog.DialogState
import tr.yasliasistan.core.log.SafeLog

/**
 * Turns the moments of one assistant session into content-free [MetricRecord]s (PLAN G-18).
 *
 * The host calls the timing hooks (tap, microphone open, transcript, decision, speech start,
 * execution); dialog outcomes (confirmation answers, how the session ended) come from the dialog
 * transitions through [onTransition]. No method takes user text. Main thread only; the [sink]
 * must return at once (the app's sink writes on its own background thread).
 *
 * Durations also go to logcat under the tag "Latency" (content-free), as in G-11.
 */
class SessionMetrics(
    private val sink: MetricsSink,
    private val clock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : DialogSession.TransitionObserver, ScreenMetrics {

    private var sessionAt = UNSET
    private var tapAt = UNSET
    private var listenAt = UNSET
    private var lastPartialAt = UNSET
    private var commandAt = UNSET
    private var decisionAt = UNSET
    private var executeAt = UNSET
    private var firstSpeechAfterCommand = false
    private var firstSpeechAfterDecision = false
    private var clarifications = 0

    // --- Timing hooks ---

    /** The bubble was tapped to start (not to end a turn). */
    fun tapped() {
        tapAt = clock()
    }

    fun listening() {
        val now = clock()
        listenAt = now
        lastPartialAt = UNSET
        if (tapAt != UNSET) duration(MetricKind.TAP_TO_LISTENING, now - tapAt)
        tapAt = UNSET
    }

    /** A partial recognition result arrived (its text is not passed here). */
    fun partialHeard() {
        lastPartialAt = clock()
    }

    /** A command or answer is complete; [spoken] is false for typed text. */
    fun commandReceived(spoken: Boolean) {
        val now = clock()
        if (spoken) {
            if (lastPartialAt != UNSET) duration(MetricKind.SPEECH_END_TO_TRANSCRIPT, now - lastPartialAt)
            if (listenAt != UNSET) duration(MetricKind.LISTEN_TO_TRANSCRIPT, now - listenAt)
        }
        listenAt = UNSET
        lastPartialAt = UNSET
        commandAt = now
        firstSpeechAfterCommand = true
    }

    /** The decision engine's result arrived on the main thread. */
    fun decided(source: DecisionSource, result: DialogEvent) {
        val now = clock()
        val outcome = when (result) {
            is DialogEvent.Proceeding -> MetricOutcome.ACT
            is DialogEvent.Understood -> MetricOutcome.CONFIRM_FIRST
            is DialogEvent.Replied -> if (result.expectsAnswer) MetricOutcome.CLARIFY else MetricOutcome.NO_ACTION
            else -> null
        }
        if (outcome == MetricOutcome.CLARIFY) clarifications++
        val took = if (commandAt != UNSET) now - commandAt else null
        emit(MetricRecord(MetricKind.DECISION, wallClock(), took, source = source, outcome = outcome))
        if (source == DecisionSource.NONE) error(ErrorCode.NOT_UNDERSTOOD)
        decisionAt = now
        firstSpeechAfterDecision = true
    }

    /** A "which one?" question was asked although only one candidate existed (FG-102, DEG-31). */
    fun unnecessaryQuestion() {
        emit(MetricRecord(MetricKind.UNNECESSARY_QUESTION, wallClock()))
    }

    fun speechStarted() {
        val now = clock()
        if (firstSpeechAfterCommand && commandAt != UNSET) duration(MetricKind.COMMAND_TO_FIRST_SPEECH, now - commandAt)
        if (firstSpeechAfterDecision && decisionAt != UNSET) duration(MetricKind.DECISION_TO_FIRST_SPEECH, now - decisionAt)
        firstSpeechAfterCommand = false
        firstSpeechAfterDecision = false
    }

    fun executionStarted() {
        executeAt = clock()
    }

    /** The action finished; [error] says why when it failed. */
    fun executionFinished(success: Boolean, error: ErrorCode? = null) {
        val now = clock()
        val took = if (executeAt != UNSET) now - executeAt else null
        emit(MetricRecord(MetricKind.EXECUTION, wallClock(), took, outcome = if (success) MetricOutcome.SUCCESS else MetricOutcome.FAILED))
        if (success && decisionAt != UNSET) duration(MetricKind.DECISION_TO_ACTION_DONE, now - decisionAt)
        if (!success && error != null) error(error)
        executeAt = UNSET
    }

    /** G-19: a target element was missing from the tree (DEG-20 rate); the failure itself is reported via [executionFinished]. */
    override fun elementNotFound() {
        emit(MetricRecord(MetricKind.ELEMENT_NOT_FOUND, wallClock()))
    }

    /** G-22: one "Sen yap" step (STEP: duration, SUCCESS/FAILED). */
    override fun stepFinished(durationMs: Long, success: Boolean) {
        emit(MetricRecord(MetricKind.STEP, wallClock(), durationMs, outcome = if (success) MetricOutcome.SUCCESS else MetricOutcome.FAILED))
    }

    /** G-22: a step led to an unexpected page and was undone (FG-25). */
    override fun wrongStep() {
        emit(MetricRecord(MetricKind.WRONG_TAP, wallClock()))
    }

    fun error(code: ErrorCode) {
        emit(MetricRecord(MetricKind.ERROR, wallClock(), errorCode = code))
    }

    // --- Dialog transitions ---

    override fun onTransition(event: DialogEvent, before: DialogState, after: DialogState) {
        if (!before.sessionOpen && after.sessionOpen) startSession(event)
        confirmation(event, before, after)?.let {
            emit(MetricRecord(MetricKind.CONFIRMATION, wallClock(), outcome = it))
        }
        if (before.sessionOpen && !after.sessionOpen) endSession(endOutcome(event, before))
    }

    private fun startSession(event: DialogEvent) {
        sessionAt = clock()
        clarifications = 0
        decisionAt = UNSET
        executeAt = UNSET
        val mode = if (event is DialogEvent.TextEntered) MetricOutcome.TYPED else MetricOutcome.VOICE
        emit(MetricRecord(MetricKind.SESSION_START, wallClock(), outcome = mode))
    }

    private fun endSession(outcome: MetricOutcome) {
        val took = if (sessionAt != UNSET) clock() - sessionAt else null
        emit(MetricRecord(MetricKind.TASK_END, wallClock(), took, outcome = outcome, count = clarifications))
        sessionAt = UNSET
        tapAt = UNSET
        listenAt = UNSET
    }

    private fun confirmation(event: DialogEvent, before: DialogState, after: DialogState): MetricOutcome? {
        if (before !is DialogState.ReadingBack && before !is DialogState.AwaitingConfirmation) return null
        val answered = event is DialogEvent.Heard || event is DialogEvent.ConfirmTapped
        return when {
            after is DialogState.Working -> MetricOutcome.YES
            after is DialogState.ReadingBack && answered -> MetricOutcome.UNCLEAR
            after is DialogState.Done && event is DialogEvent.TimerFired -> MetricOutcome.TIMEOUT
            after is DialogState.Done && answered -> MetricOutcome.NO
            else -> null
        }
    }

    private fun endOutcome(event: DialogEvent, before: DialogState): MetricOutcome = when (event) {
        DialogEvent.StopPressed ->
            if (before is DialogState.Processing || before is DialogState.Working) {
                MetricOutcome.STOPPED_WHILE_WAITING
            } else {
                MetricOutcome.STOPPED
            }
        is DialogEvent.ExecutionSucceeded -> MetricOutcome.COMPLETED
        is DialogEvent.ExecutionFailed -> MetricOutcome.FAILED
        DialogEvent.ListenFailed -> MetricOutcome.NOT_HEARD
        is DialogEvent.Heard, is DialogEvent.TextEntered ->
            if (before == DialogState.Listening) MetricOutcome.NOT_HEARD else MetricOutcome.CANCELLED
        is DialogEvent.CannotListen -> MetricOutcome.CANNOT_LISTEN
        is DialogEvent.TimerFired, is DialogEvent.ConfirmTapped -> MetricOutcome.CANCELLED
        else -> MetricOutcome.NO_ACTION
    }

    private fun duration(kind: MetricKind, ms: Long) {
        emit(MetricRecord(kind, wallClock(), ms))
    }

    private fun emit(record: MetricRecord) {
        if (record.durationMs != null && record.kind != MetricKind.TASK_END) {
            SafeLog.event(LATENCY_TAG, "${record.kind} ${record.durationMs} ms")
        }
        sink.record(record)
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE
        const val LATENCY_TAG = "Latency"
    }
}
