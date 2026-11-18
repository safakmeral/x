package tr.yasliasistan.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.decision.DecisionSource
import tr.yasliasistan.core.dialog.DialogEffect
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.dialog.DialogMachine
import tr.yasliasistan.core.dialog.DialogSession
import tr.yasliasistan.core.dialog.FakeScheduler

class SessionMetricsTest {

    private val records = mutableListOf<MetricRecord>()
    private var now = 0L
    private val metrics = SessionMetrics({ records += it }, clock = { now }, wallClock = { 1_000_000 + now })
    private val scheduler = FakeScheduler()
    private val effects = mutableListOf<DialogEffect>()
    private val dialog = DialogSession(DialogMachine(), scheduler) { effects += it }.also { it.observer = metrics }

    private fun decideId() = effects.filterIsInstance<DialogEffect.Decide>().last().requestId
    private fun executeId() = effects.filterIsInstance<DialogEffect.Execute>().last().requestId
    private fun lastSpeakId() = effects.filterIsInstance<DialogEffect.Speak>().last().utteranceId
    private fun kinds() = records.map { it.kind }
    private fun one(kind: MetricKind) = records.single { it.kind == kind }

    @Test
    fun elementNotFoundIsRecordedWithoutContent() {
        now = 5
        (metrics as ScreenMetrics).elementNotFound()
        assertEquals(listOf(MetricRecord(MetricKind.ELEMENT_NOT_FOUND, 1_000_005)), records)
    }

    @Test
    fun stepsAndWrongStepsAreRecordedWithoutContent() {
        now = 7
        (metrics as ScreenMetrics).stepFinished(120, success = true)
        metrics.stepFinished(80, success = false)
        metrics.wrongStep()
        assertEquals(
            listOf(
                MetricRecord(MetricKind.STEP, 1_000_007, 120, outcome = MetricOutcome.SUCCESS),
                MetricRecord(MetricKind.STEP, 1_000_007, 80, outcome = MetricOutcome.FAILED),
                MetricRecord(MetricKind.WRONG_TAP, 1_000_007),
            ),
            records,
        )
    }

    @Test
    fun spokenCommandProducesAllLatencies() {
        metrics.tapped()
        dialog.send(DialogEvent.BubbleTapped)
        now = 300; metrics.listening()
        now = 2_000; metrics.partialHeard()
        now = 2_400; metrics.commandReceived(spoken = true)
        dialog.send(DialogEvent.Heard("ayarları aç"))
        now = 2_450
        val proceeding = DialogEvent.Proceeding(decideId(), "Ayarlar uygulamasını açıyorum.")
        metrics.decided(DecisionSource.RULE, proceeding)
        dialog.send(proceeding)
        now = 2_700; metrics.speechStarted()
        metrics.executionStarted()
        now = 2_900; metrics.executionFinished(success = true)
        dialog.send(DialogEvent.ExecutionSucceeded(executeId(), null))

        assertEquals(300L, one(MetricKind.TAP_TO_LISTENING).durationMs)
        assertEquals(400L, one(MetricKind.SPEECH_END_TO_TRANSCRIPT).durationMs)
        assertEquals(2_100L, one(MetricKind.LISTEN_TO_TRANSCRIPT).durationMs)
        val decision = one(MetricKind.DECISION)
        assertEquals(50L, decision.durationMs)
        assertEquals(DecisionSource.RULE, decision.source)
        assertEquals(MetricOutcome.ACT, decision.outcome)
        assertEquals(300L, one(MetricKind.COMMAND_TO_FIRST_SPEECH).durationMs)
        assertEquals(250L, one(MetricKind.DECISION_TO_FIRST_SPEECH).durationMs)
        assertEquals(200L, one(MetricKind.EXECUTION).durationMs)
        assertEquals(450L, one(MetricKind.DECISION_TO_ACTION_DONE).durationMs)
        assertEquals(MetricOutcome.VOICE, one(MetricKind.SESSION_START).outcome)
        val end = one(MetricKind.TASK_END)
        assertEquals(MetricOutcome.COMPLETED, end.outcome)
        assertEquals(2_900L, end.durationMs)
        assertEquals(0, end.count)
        assertEquals(1_002_900L, end.atMs)
    }

    @Test
    fun firstSpeechIsMeasuredOnlyOnce() {
        metrics.commandReceived(spoken = false)
        now = 100; metrics.speechStarted()
        now = 900; metrics.speechStarted()
        assertEquals(1, records.count { it.kind == MetricKind.COMMAND_TO_FIRST_SPEECH })
    }

    @Test
    fun typedSessionIsMarkedTypedAndHasNoListeningMetrics() {
        metrics.commandReceived(spoken = false)
        dialog.send(DialogEvent.TextEntered("ayarları aç"))
        assertEquals(MetricOutcome.TYPED, one(MetricKind.SESSION_START).outcome)
        assertTrue(MetricKind.LISTEN_TO_TRANSCRIPT !in kinds())
    }

    @Test
    fun confirmationYesNoAndTimeoutAreRecorded() {
        confirmRound(); dialog.send(DialogEvent.ConfirmTapped(yes = true))
        assertEquals(MetricOutcome.YES, records.last { it.kind == MetricKind.CONFIRMATION }.outcome)
        dialog.send(DialogEvent.ExecutionSucceeded(executeId(), null))

        confirmRound(); dialog.send(DialogEvent.Heard("hayır"))
        assertEquals(MetricOutcome.NO, records.last { it.kind == MetricKind.CONFIRMATION }.outcome)
        assertEquals(MetricOutcome.CANCELLED, records.last { it.kind == MetricKind.TASK_END }.outcome)

        confirmRound(); dialog.send(DialogEvent.SpeechFinished(lastSpeakId()))
        scheduler.advanceBy(60_000)
        assertEquals(MetricOutcome.TIMEOUT, records.last { it.kind == MetricKind.CONFIRMATION }.outcome)
    }

    @Test
    fun unclearAnswerIsRecorded() {
        confirmRound()
        dialog.send(DialogEvent.Heard("şey"))
        assertEquals(MetricOutcome.UNCLEAR, records.last { it.kind == MetricKind.CONFIRMATION }.outcome)
    }

    @Test
    fun clarificationQuestionsAreCountedPerSession() {
        dialog.send(DialogEvent.TextEntered("haritaları aç"))
        val question = DialogEvent.Replied(decideId(), "Hangisini?", expectsAnswer = true)
        metrics.decided(DecisionSource.RULE, question)
        dialog.send(question)
        dialog.send(DialogEvent.Heard("google"))
        val cannot = DialogEvent.Replied(decideId(), "Yapamıyorum.", expectsAnswer = false)
        metrics.decided(DecisionSource.NONE, cannot)
        dialog.send(cannot)

        assertEquals(listOf(MetricOutcome.CLARIFY, MetricOutcome.NO_ACTION), records.filter { it.kind == MetricKind.DECISION }.map { it.outcome })
        assertEquals(ErrorCode.NOT_UNDERSTOOD, one(MetricKind.ERROR).errorCode)
        val end = one(MetricKind.TASK_END)
        assertEquals(1, end.count)
        assertEquals(MetricOutcome.NO_ACTION, end.outcome)
    }

    @Test
    fun stopWhileDecidingCountsAsLeavingWhileWaiting() {
        dialog.send(DialogEvent.TextEntered("ayarları aç"))
        dialog.send(DialogEvent.StopPressed)
        assertEquals(MetricOutcome.STOPPED_WHILE_WAITING, one(MetricKind.TASK_END).outcome)
    }

    @Test
    fun stopWhileListeningIsAPlainStop() {
        dialog.send(DialogEvent.BubbleTapped)
        dialog.send(DialogEvent.StopPressed)
        assertEquals(MetricOutcome.STOPPED, one(MetricKind.TASK_END).outcome)
        dialog.send(DialogEvent.StopPressed) // closing sentence: no second task end
        assertEquals(1, records.count { it.kind == MetricKind.TASK_END })
    }

    @Test
    fun nothingHeardAndCannotListenEndings() {
        dialog.send(DialogEvent.BubbleTapped)
        dialog.send(DialogEvent.ListenFailed)
        dialog.send(DialogEvent.BubbleTapped)
        dialog.send(DialogEvent.CannotListen("izin yok"))
        assertEquals(
            listOf(MetricOutcome.NOT_HEARD, MetricOutcome.CANNOT_LISTEN),
            records.filter { it.kind == MetricKind.TASK_END }.map { it.outcome },
        )
        assertEquals(2, records.count { it.kind == MetricKind.SESSION_START })
    }

    @Test
    fun failedExecutionRecordsErrorWithCause() {
        dialog.send(DialogEvent.TextEntered("ayarları aç"))
        dialog.send(DialogEvent.Proceeding(decideId(), "Açıyorum."))
        metrics.executionStarted()
        metrics.executionFinished(success = false, error = ErrorCode.APP_LAUNCH_FAILED)
        dialog.send(DialogEvent.ExecutionFailed(executeId(), null))
        assertEquals(MetricOutcome.FAILED, one(MetricKind.EXECUTION).outcome)
        assertEquals(FailureCause.EXECUTION, one(MetricKind.ERROR).failureCause)
        assertEquals(MetricOutcome.FAILED, one(MetricKind.TASK_END).outcome)
        assertTrue(MetricKind.DECISION_TO_ACTION_DONE !in kinds())
    }

    private fun confirmRound() {
        dialog.send(DialogEvent.TextEntered("haritaları aç"))
        dialog.send(DialogEvent.Understood(decideId(), "Google Haritalar açayım mı?"))
    }
}
