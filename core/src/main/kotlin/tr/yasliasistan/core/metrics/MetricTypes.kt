package tr.yasliasistan.core.metrics

import tr.yasliasistan.core.decision.DecisionSource

/**
 * Kinds of metric records (PLAN G-18; FG-90, FG-92, NFG-27, DEG-19, DEG-20; V-06 thresholds).
 * Durations are in milliseconds in [MetricRecord.durationMs]. Kinds marked "later" are already
 * defined so the CSV columns stay stable; their task records them.
 */
enum class MetricKind {
    /** A bubble- or typing-started session began; [MetricRecord.outcome] is VOICE or TYPED. */
    SESSION_START,

    /** Bubble tap -> microphone open (V-06: <= 1 s). */
    TAP_TO_LISTENING,

    /** Last partial recognition result (proxy for end of speech) -> final transcript. */
    SPEECH_END_TO_TRANSCRIPT,

    /** Microphone open -> final transcript (includes the user's talking time). */
    LISTEN_TO_TRANSCRIPT,

    /** Command (spoken or typed) -> decision; with [MetricRecord.source] and the decision outcome. */
    DECISION,

    /** Decision -> first sound of the reply. */
    DECISION_TO_FIRST_SPEECH,

    /** Command -> first spoken feedback of any kind (V-06: <= 2 s). */
    COMMAND_TO_FIRST_SPEECH,

    /** Answer to the readback question: YES, NO, UNCLEAR or TIMEOUT (FG-90 "onay sonucu"). */
    CONFIRMATION,

    /** One action run (execution latency); outcome SUCCESS or FAILED. */
    EXECUTION,

    /** Decision -> action finished successfully (includes any confirmation wait). */
    DECISION_TO_ACTION_DONE,

    /** G-22: duration of one "Sen yap" step; outcome SUCCESS (screen as expected) or FAILED. */
    STEP,

    /** Later (G-34/G-38): one language model call; duration and SUCCESS/FAILED. */
    LLM_CALL,

    /** Later (G-43): one embedding computation. */
    EMBEDDING,

    /** G-22: a step led to an unexpected page and was undone (FG-25); later also G-41 wrong taps. */
    WRONG_TAP,

    /** Later (G-19/G-20): target element not found in the tree (DEG-20). */
    ELEMENT_NOT_FOUND,

    /** G-37: a clarification question asked with a single candidate (FG-102). */
    UNNECESSARY_QUESTION,

    /** An error with its [ErrorCode] and cause label (FG-92). */
    ERROR,

    /** Session over; outcome says how, count = clarification questions asked, duration = session length. */
    TASK_END,
}

/** Coded results; which values a kind uses is documented on [MetricKind]. */
enum class MetricOutcome {
    VOICE, TYPED,
    ACT, CONFIRM_FIRST, CLARIFY, NO_ACTION,
    YES, NO, UNCLEAR, TIMEOUT,
    SUCCESS, FAILED,
    COMPLETED, CANCELLED, NOT_HEARD, CANNOT_LISTEN,

    /** "Dur" pressed while the user was not waiting for the assistant. */
    STOPPED,

    /** "Dur" pressed while the assistant was deciding or acting (DEG-19: left while waiting). */
    STOPPED_WHILE_WAITING,
}

/** Error taxonomy of FG-92 / DEG-25. */
enum class FailureCause { INTENT, PLANNING, ENVIRONMENT, EXECUTION }

/** Fixed error codes; each belongs to one [FailureCause]. */
enum class ErrorCode(val cause: FailureCause) {
    NOT_UNDERSTOOD(FailureCause.INTENT),
    NOTHING_HEARD(FailureCause.ENVIRONMENT),
    MIC_PERMISSION_MISSING(FailureCause.ENVIRONMENT),
    SPEECH_MODEL_MISSING(FailureCause.ENVIRONMENT),
    MIC_BUSY(FailureCause.ENVIRONMENT),
    SPEECH_FAILED(FailureCause.ENVIRONMENT),
    ELEMENT_NOT_FOUND(FailureCause.ENVIRONMENT),
    LLM_UNAVAILABLE(FailureCause.ENVIRONMENT),
    PLAN_INVALID(FailureCause.PLANNING),
    ACTION_MISSING(FailureCause.PLANNING),
    APP_LAUNCH_FAILED(FailureCause.EXECUTION),

    /** G-37: dialer, message screen, volume or settings screen could not be opened. */
    DEVICE_ACTION_FAILED(FailureCause.EXECUTION),
}

/**
 * One metric event. Privacy by type (KVKK, FG-90 "kişisel içeriksiz"): every field is a number or
 * an enum, so no recognized text, contact name, app content or screen text can be stored.
 * MetricRecordTypeTest enforces this; do not add String/CharSequence/Any fields.
 */
data class MetricRecord(
    val kind: MetricKind,
    /** Wall-clock time of the event (epoch ms), used for the 30-day retention. */
    val atMs: Long,
    val durationMs: Long? = null,
    val source: DecisionSource? = null,
    val outcome: MetricOutcome? = null,
    val errorCode: ErrorCode? = null,
    val count: Int? = null,
) {
    val failureCause: FailureCause? get() = errorCode?.cause
}

/** A stored record: [session] is an anonymous running number, [appVersion] the version code. */
data class MetricRow(val session: Int, val appVersion: Int, val record: MetricRecord)

/** Receives metric records; implementations must return at once (no I/O on the caller's thread). */
fun interface MetricsSink {
    fun record(record: MetricRecord)

    companion object {
        val NONE = MetricsSink { }
    }
}
