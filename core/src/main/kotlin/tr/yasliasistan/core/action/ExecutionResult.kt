package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.ScreenSnapshot

/** Outcome codes of one executed step (content-free; safe for metrics). */
enum class ExecutionCode {
    /** The action was done. */
    SUCCESS,

    /** The step was "bitti": the task is finished. */
    DONE,

    /** The step was "devret": the user takes over ([ExecutionResult.handOver]). */
    HANDED_OVER,

    /** The planned element is not on the screen any more (NFG-30). */
    ELEMENT_NOT_FOUND,

    /** The screen differs from the plan (element moved, other app in front): nothing was done. */
    SCREEN_CHANGED,

    /** A safety rule forbids the action ([ExecutionResult.refusal]). */
    REFUSED,

    /** The system did not perform the action. */
    FAILED,

    /** "Dur" was pressed: nothing more runs (FG-23). */
    STOPPED,
}

enum class RefusalReason {
    /** No task is open, so the screen may not be read or touched (NFG-53). */
    NO_ACTIVE_TASK,

    /** Password or sensitive field: neither read nor touched (FG-78; handed over in G-26). */
    SECRET_FIELD,

    /** Typing into something that is not an editable field. */
    NOT_EDITABLE,

    /** The element is disabled. */
    DISABLED,

    /** The app in front is not on the allowed list or is blocked (FG-60, G-24). */
    PACKAGE_NOT_ALLOWED,
}

/**
 * Result of one step. [screen] is the last screen read after the action (null if none), so the
 * next step and G-22's comparison need no extra read. [screenChanged] tells whether the screen
 * reacted within the settle time.
 */
data class ExecutionResult(
    val code: ExecutionCode,
    val refusal: RefusalReason? = null,
    val handOver: HandOverReason? = null,
    val screenChanged: Boolean = false,
    val screen: ScreenSnapshot? = null,
) {
    val ok: Boolean get() = code == ExecutionCode.SUCCESS

    override fun toString() = "ExecutionResult($code, refusal=$refusal, handOver=$handOver, changed=$screenChanged)"

    companion object {
        fun of(code: ExecutionCode) = ExecutionResult(code)
        fun refused(reason: RefusalReason) = ExecutionResult(ExecutionCode.REFUSED, refusal = reason)
    }
}

/** Result of a sequence: the last step's result and how many steps succeeded before it. */
data class SequenceResult(val last: ExecutionResult, val stepsDone: Int)
