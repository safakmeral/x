package tr.yasliasistan.core.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import tr.yasliasistan.core.action.ActionKind
import tr.yasliasistan.core.action.ActionSchema

/**
 * The answer format of the language model (PLAN G-35), sent to the proxy as JSON Schema and
 * enforced by the providers (Gemini `responseJsonSchema`, Groq strict `json_schema`). Strict mode
 * needs every property listed in `required` and `additionalProperties: false`; optional values are
 * `null`. One answer is exactly one of: a plan, one clarification question, or "cannot do".
 *
 * ```
 * { geri_okuma, netlestirme_sorusu | null, yapamam | null, guven: dusuk|yuksek,
 *   adimlar: [ { eylem, oge, yon, uzunluk, metin, uygulama, bekleme_ms, neden, beklenen_durum } ] }
 * ```
 * Step fields map 1:1 onto [tr.yasliasistan.core.action.WireAction] (action schema
 * v[ActionSchema.VERSION]); [ModelReplyParser] re-checks everything, the schema is only a hint.
 */
object ResponseSchema {

    /** Bump with any field change; recorded in the prompt version of audit copies. */
    const val VERSION = 1

    /** The action schema version these step fields follow. */
    const val ACTION_SCHEMA_VERSION = ActionSchema.VERSION

    const val READBACK = "geri_okuma"
    const val QUESTION = "netlestirme_sorusu"
    const val CANNOT = "yapamam"
    const val CONFIDENCE = "guven"
    const val STEPS = "adimlar"

    const val KIND = "eylem"
    const val ELEMENT = "oge"
    const val DIRECTION = "yon"
    const val LENGTH = "uzunluk"
    const val TEXT = "metin"
    const val APP = "uygulama"
    const val WAIT_MS = "bekleme_ms"
    const val REASON = "neden"
    const val EXPECTED = "beklenen_durum"

    const val CONFIDENCE_LOW = "dusuk"
    const val CONFIDENCE_HIGH = "yuksek"

    val topFields = listOf(READBACK, QUESTION, CANNOT, CONFIDENCE, STEPS)
    val stepFields = listOf(KIND, ELEMENT, DIRECTION, LENGTH, TEXT, APP, WAIT_MS, REASON, EXPECTED)

    val json: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put(READBACK, type("string"))
            put(QUESTION, type("string", "null"))
            put(CANNOT, type("string", "null"))
            put(CONFIDENCE, enumOf(CONFIDENCE_LOW, CONFIDENCE_HIGH))
            putJsonObject(STEPS) {
                put("type", "array")
                put("items", step())
            }
        }
        put("required", strings(topFields))
        put("additionalProperties", false)
    }

    private fun step(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put(KIND, enumOf(*ActionKind.entries.map { it.wireName }.toTypedArray()))
            put(ELEMENT, type("integer", "null"))
            put(DIRECTION, type("string", "null"))
            put(LENGTH, type("string", "null"))
            put(TEXT, type("string", "null"))
            put(APP, type("string", "null"))
            put(WAIT_MS, type("integer", "null"))
            put(REASON, type("string", "null"))
            put(EXPECTED, type("string"))
        }
        put("required", strings(stepFields))
        put("additionalProperties", false)
    }

    private fun type(vararg types: String): JsonObject = buildJsonObject {
        put("type", if (types.size == 1) JsonPrimitive(types[0]) else strings(types.toList()))
    }

    private fun enumOf(vararg values: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("enum", strings(values.toList()))
    }

    private fun strings(values: List<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
}
