package tr.yasliasistan.core.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Reads the proxy's answer envelope (proxy/README.md, contract v1):
 * `{v:1, ok:true, result:{...}}` or `{v:1, ok:false, error:"<CODE>", on_device?:true}`.
 * Anything else (HTML error page of Cloudflare, e.g. Error 1027) is a server failure.
 */
internal object ProxyEnvelope {

    const val CONTRACT_VERSION = 1

    fun read(response: TransportResult.Response): CloudOutcome {
        val envelope = parseObject(response.body)
            ?: return CloudOutcome.Failed(failureOfStatus(response.code))
        if (envelope["v"].intValue() != CONTRACT_VERSION) return CloudOutcome.Failed(CloudFailure.BAD_RESPONSE)
        val ok = (envelope["ok"] as? JsonPrimitive)?.takeUnless { it.isString }?.content
        if (ok == "true" && response.code in 200..299) {
            val result = envelope["result"] as? JsonObject ?: return CloudOutcome.Failed(CloudFailure.BAD_RESPONSE)
            return CloudOutcome.Answer(result)
        }
        if (ok != "false") return CloudOutcome.Failed(CloudFailure.BAD_RESPONSE)
        val code = (envelope["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val onDevice = (envelope["on_device"] as? JsonPrimitive)?.content == "true"
        return CloudOutcome.Failed(failureOfCode(code, onDevice, response.code))
    }

    /** Seconds to wait from the envelope's `retry_after_s`, or null. */
    fun retryAfterSeconds(response: TransportResult.Response): Long? =
        response.retryAfterSeconds ?: parseObject(response.body)?.get("retry_after_s")?.intValue()?.toLong()

    private fun failureOfCode(code: String?, onDevice: Boolean, status: Int): CloudFailure = when (code) {
        "QUOTA_EXHAUSTED" -> CloudFailure.QUOTA_EXHAUSTED
        "PROVIDERS_UNAVAILABLE", "UPSTREAM_REJECTED", "SERVER_MISCONFIGURED" -> CloudFailure.UNAVAILABLE
        "UNAUTHORIZED" -> CloudFailure.UNAUTHORIZED
        "RATE_LIMITED" -> CloudFailure.RATE_LIMITED
        "INVALID_REQUEST", "UNSUPPORTED_VERSION", "BINARY_NOT_ALLOWED", "PAYLOAD_TOO_LARGE",
        "UNSUPPORTED_MEDIA_TYPE", "NOT_FOUND", "METHOD_NOT_ALLOWED" -> CloudFailure.REQUEST_REJECTED
        else -> if (onDevice) CloudFailure.UNAVAILABLE else failureOfStatus(status)
    }

    private fun failureOfStatus(status: Int): CloudFailure = when {
        status == 401 -> CloudFailure.UNAUTHORIZED
        status == 429 || status >= 500 -> CloudFailure.UNAVAILABLE
        else -> CloudFailure.BAD_RESPONSE
    }

    private fun parseObject(body: String): JsonObject? = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonElement?.intValue(): Int? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
}
