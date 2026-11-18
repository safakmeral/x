package tr.yasliasistan.core.llm

import tr.yasliasistan.core.action.ScreenAction
import tr.yasliasistan.core.action.WireAction

/** One proposed step, still in wire form and still masked. */
data class WireStep(val action: WireAction, val expected: String) {
    override fun toString() = "WireStep(action=$action)"
}

/**
 * A model answer that passed [ModelReplyParser]: exactly one of plan / question / cannot-do.
 * Texts are still **masked** here (placeholders); [ReplyDecoder] restores them on the device.
 */
sealed interface ModelReply {
    data class Plan(val readback: String, val confident: Boolean, val steps: List<WireStep>) : ModelReply {
        override fun toString() = "Plan(steps=${steps.size}, confident=$confident)"
    }

    data class Question(val text: String) : ModelReply {
        override fun toString() = "Question(<${text.length} chars>)"
    }

    data class CannotDo(val reason: String) : ModelReply {
        override fun toString() = "CannotDo(<${reason.length} chars>)"
    }
}

/** Why an answer was rejected (counted, never spoken or stored in full). */
enum class ReplyError {
    NOT_AN_OBJECT, UNKNOWN_FIELD, MISSING_FIELD, WRONG_TYPE, BAD_CONFIDENCE, TOO_LONG, TOO_MANY_STEPS,
    NOT_EXACTLY_ONE, EMPTY_TEXT, MORE_THAN_ONE_QUESTION, UNKNOWN_PLACEHOLDER, INVALID_ACTION, ELEMENT_NOT_SENT,
}

sealed interface ParseResult {
    data class Ok(val reply: ModelReply) : ParseResult
    data class Rejected(val error: ReplyError) : ParseResult
}

/** One executable step: an action checked against the planning snapshot, with its expected state. */
data class PlannedStep(val action: ScreenAction, val expected: String) {
    override fun toString() = "PlannedStep(action=$action)"
}

/**
 * A decoded, validated plan for G-38 (unmasked, on the device only, never logged). Every action
 * must still pass the risk classifier (G-25, FG-74) before it runs; nothing here executes.
 */
data class PlannedTask(val readback: String, val confident: Boolean, val steps: List<PlannedStep>) {
    override fun toString() = "PlannedTask(steps=${steps.size}, confident=$confident)"
}

/** Unmasked answer kinds handed to the decision layer. */
sealed interface DecodedReply {
    data class Plan(val task: PlannedTask) : DecodedReply
    data class Question(val text: String) : DecodedReply {
        override fun toString() = "Question(<${text.length} chars>)"
    }

    data class CannotDo(val reason: String) : DecodedReply {
        override fun toString() = "CannotDo(<${reason.length} chars>)"
    }

    data class Rejected(val error: ReplyError) : DecodedReply
}
