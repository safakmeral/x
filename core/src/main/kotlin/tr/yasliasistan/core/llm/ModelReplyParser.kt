package tr.yasliasistan.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import tr.yasliasistan.core.action.ScreenAction
import tr.yasliasistan.core.action.WireAction
import tr.yasliasistan.core.llm.ResponseSchema as S

/**
 * Strict parser of the model answer (PLAN G-35, NFG-31). Anything outside [ResponseSchema] is
 * rejected and produces no action: unknown fields, wrong types, overlong texts, more than one
 * answer kind, more than one question. Two tolerances, both harmless: a nullable field that is
 * missing or blank counts as null.
 *
 * A reasoning/"thought" field (some models add one) is **dropped unread**: it is never spoken,
 * stored or logged (NFG-44, NFG-28), and the rest of the answer is judged on its own.
 */
object ModelReplyParser {

    const val MAX_READBACK_CHARS = 300
    const val MAX_QUESTION_CHARS = 200
    const val MAX_REASON_CHARS = 200
    const val MAX_EXPECTED_CHARS = 200

    /** Field names models use for reasoning text; removed before validation. */
    val REASONING_FIELDS = setOf(
        "reasoning", "reasoning_content", "reason_text", "thought", "thoughts", "thinking", "analysis",
        "chain_of_thought", "dusunce", "dusunceler", "akil_yurutme",
    )

    fun parse(text: String): ParseResult {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return reject(ReplyError.NOT_AN_OBJECT)
        }
        return (element as? JsonObject)?.let(::parse) ?: reject(ReplyError.NOT_AN_OBJECT)
    }

    fun parse(answer: JsonObject): ParseResult = try {
        ParseResult.Ok(read(answer))
    } catch (e: Invalid) {
        reject(e.error)
    }

    private fun read(raw: JsonObject): ModelReply {
        val o = fieldsOf(raw, S.topFields)
        val readback = string(o, S.READBACK, MAX_READBACK_CHARS, nullable = false).orEmpty()
        val question = string(o, S.QUESTION, MAX_QUESTION_CHARS, nullable = true)
        val cannot = string(o, S.CANNOT, MAX_REASON_CHARS, nullable = true)
        val confident = when (string(o, S.CONFIDENCE, 10, nullable = false)) {
            S.CONFIDENCE_HIGH -> true
            S.CONFIDENCE_LOW -> false
            else -> throw Invalid(ReplyError.BAD_CONFIDENCE)
        }
        val stepsJson = o[S.STEPS] as? JsonArray ?: throw Invalid(ReplyError.WRONG_TYPE)
        if (stepsJson.size > PromptTemplate.MAX_STEPS) throw Invalid(ReplyError.TOO_MANY_STEPS)
        val steps = stepsJson.map { step(it as? JsonObject ?: throw Invalid(ReplyError.WRONG_TYPE)) }

        val kinds = listOf(question != null, cannot != null, steps.isNotEmpty()).count { it }
        if (kinds != 1) throw Invalid(ReplyError.NOT_EXACTLY_ONE)
        return when {
            question != null -> {
                if (question.count { it == '?' } > 1) throw Invalid(ReplyError.MORE_THAN_ONE_QUESTION)
                ModelReply.Question(question)
            }
            cannot != null -> ModelReply.CannotDo(cannot)
            else -> {
                if (readback.isBlank()) throw Invalid(ReplyError.EMPTY_TEXT)
                ModelReply.Plan(readback, confident, steps)
            }
        }
    }

    private fun step(raw: JsonObject): WireStep {
        val o = fieldsOf(raw, S.stepFields)
        val kind = string(o, S.KIND, 20, nullable = false).orEmpty()
        val action = WireAction(
            kind = kind,
            element = int(o, S.ELEMENT)?.let { if (it in 1..Int.MAX_VALUE) it.toInt() else throw Invalid(ReplyError.WRONG_TYPE) },
            direction = string(o, S.DIRECTION, 10, nullable = true),
            length = string(o, S.LENGTH, 10, nullable = true),
            text = string(o, S.TEXT, ScreenAction.MAX_TEXT_CHARS, nullable = true),
            app = string(o, S.APP, 200, nullable = true),
            waitMs = int(o, S.WAIT_MS),
            reason = string(o, S.REASON, 20, nullable = true),
        )
        val expected = string(o, S.EXPECTED, MAX_EXPECTED_CHARS, nullable = false).orEmpty()
        return WireStep(action, expected)
    }

    /** Drops reasoning fields, rejects any other unknown field. */
    private fun fieldsOf(raw: JsonObject, allowed: List<String>): Map<String, JsonElement> {
        val kept = raw.filterKeys { it !in REASONING_FIELDS }
        if (kept.keys.any { it !in allowed }) throw Invalid(ReplyError.UNKNOWN_FIELD)
        return kept
    }

    private fun string(o: Map<String, JsonElement>, key: String, max: Int, nullable: Boolean): String? {
        val value = o[key]
        if (value == null || value is JsonNull) {
            if (nullable) return null
            throw Invalid(ReplyError.MISSING_FIELD)
        }
        val p = value as? JsonPrimitive ?: throw Invalid(ReplyError.WRONG_TYPE)
        if (!p.isString) throw Invalid(ReplyError.WRONG_TYPE)
        val text = p.content.trim()
        if (text.length > max) throw Invalid(ReplyError.TOO_LONG)
        return if (nullable && text.isEmpty()) null else text
    }

    private fun int(o: Map<String, JsonElement>, key: String): Long? {
        val value = o[key]
        if (value == null || value is JsonNull) return null
        val p = value as? JsonPrimitive ?: throw Invalid(ReplyError.WRONG_TYPE)
        if (p.isString) throw Invalid(ReplyError.WRONG_TYPE)
        return p.longOrNull ?: throw Invalid(ReplyError.WRONG_TYPE)
    }

    private fun reject(error: ReplyError) = ParseResult.Rejected(error)

    private class Invalid(val error: ReplyError) : Exception(null, null, false, false)
}
