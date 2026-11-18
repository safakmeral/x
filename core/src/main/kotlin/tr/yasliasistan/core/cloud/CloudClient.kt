package tr.yasliasistan.core.cloud

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.consent.CloudTransferGate
import tr.yasliasistan.core.llm.PromptExtras
import tr.yasliasistan.core.llm.PromptTemplate
import tr.yasliasistan.core.llm.ResponseSchema
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.masking.AuditedRequest

/**
 * The cloud client (PLAN G-34). Order of gates, each one before anything is built or sent:
 * 1. consent ([gate], FG-103/NFG-78): without it no request object even exists;
 * 2. configuration (proxy address + access code entered): otherwise the cloud is off;
 * 3. a quota pause after `QUOTA_EXHAUSTED` (no repeated slow calls, NFG-25).
 * Then one request (never retried, no usage counter on the device, V-19), with the bubble
 * indicator on while it travels (NFG-58) and a masked audit copy kept on the device (FG-91).
 *
 * It accepts only an [AuditedRequest] (masked command + masked screen that passed the final audit,
 * G-31/G-32); raw text cannot reach it. Call it from a worker thread: it blocks.
 * Logs carry outcome codes only, never text (NFG-46).
 */
class CloudClient(
    private val gate: CloudTransferGate,
    private val config: CloudConfigSource,
    private val transport: CloudTransport,
    private val audit: AuditCopySink = AuditCopySink.NONE,
    private val indicator: CloudIndicator = CloudIndicator.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var pausedUntil = 0L

    @Volatile
    private var pauseReason = CloudFailure.QUOTA_EXHAUSTED

    /**
     * Why a call would certainly not be made right now, or null. Callers check it first, so not even
     * masking runs without consent. Configuration is checked before consent: an install without a
     * proxy should not push the user to grant a consent that cannot be used.
     */
    fun precheck(): CloudFailure? = when {
        config.current() == null -> CloudFailure.NOT_CONFIGURED
        !gate.cloudTransferAllowed() -> CloudFailure.NO_CONSENT
        clock() < pausedUntil -> pauseReason
        else -> null
    }

    fun generate(request: AuditedRequest, extras: PromptExtras = PromptExtras()): CloudOutcome {
        if (!gate.cloudTransferAllowed()) return failed(CloudFailure.NO_CONSENT)
        val settings = config.current() ?: return failed(CloudFailure.NOT_CONFIGURED)
        if (clock() < pausedUntil) return failed(pauseReason)

        val user = PromptTemplate.userMessage(request, extras)
        val body = buildJsonObject {
            put("v", ProxyEnvelope.CONTRACT_VERSION)
            put("system", PromptTemplate.SYSTEM)
            put("user", user)
            put("schema", ResponseSchema.json)
        }.toString()

        // Consent can be withdrawn while the screen was being masked: check once more right before sending.
        if (!gate.cloudTransferAllowed()) return failed(CloudFailure.NO_CONSENT)
        audit.record(AuditCopy(clock(), PromptTemplate.VERSION, user))

        indicator.busy(true)
        val result = try {
            transport.post(settings.address.endpoint, settings.accessCode.value, body)
        } catch (e: Exception) {
            TransportResult.NetworkError // a transport must not throw; fail safe if it does
        } finally {
            indicator.busy(false)
        }

        val outcome = when (result) {
            is TransportResult.Response -> ProxyEnvelope.read(result).also { pauseIfNeeded(it, result) }
            TransportResult.Timeout -> CloudOutcome.Failed(CloudFailure.TIMEOUT)
            TransportResult.NetworkError -> CloudOutcome.Failed(CloudFailure.NETWORK)
            TransportResult.Blocked -> CloudOutcome.Failed(CloudFailure.NOT_CONFIGURED)
        }
        SafeLog.event(TAG, "outcome=${(outcome as? CloudOutcome.Failed)?.failure ?: "ANSWER"}")
        return outcome
    }

    private fun pauseIfNeeded(outcome: CloudOutcome, response: TransportResult.Response) {
        val failure = (outcome as? CloudOutcome.Failed)?.failure ?: return
        val pauseMs = when (failure) {
            CloudFailure.QUOTA_EXHAUSTED -> Defaults.CLOUD_QUOTA_PAUSE_MS
            CloudFailure.RATE_LIMITED ->
                (ProxyEnvelope.retryAfterSeconds(response) ?: DEFAULT_RETRY_AFTER_S).coerceIn(1, MAX_RETRY_AFTER_S) * 1000
            else -> return
        }
        pauseReason = failure
        pausedUntil = clock() + pauseMs
    }

    private fun failed(failure: CloudFailure): CloudOutcome {
        SafeLog.event(TAG, "not sent: $failure")
        return CloudOutcome.Failed(failure)
    }

    private companion object {
        const val TAG = "Cloud"
        const val DEFAULT_RETRY_AFTER_S = 10L
        const val MAX_RETRY_AFTER_S = 60L
    }
}
