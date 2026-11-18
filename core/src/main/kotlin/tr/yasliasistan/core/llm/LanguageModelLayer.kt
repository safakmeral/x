package tr.yasliasistan.core.llm

import tr.yasliasistan.core.cloud.CloudClient
import tr.yasliasistan.core.cloud.CloudFailure
import tr.yasliasistan.core.cloud.CloudOutcome
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.DecisionLayer
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.PendingQuestion
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.masking.AuditResult
import tr.yasliasistan.core.masking.AuditedRequest
import tr.yasliasistan.core.masking.CommandMaskResult
import tr.yasliasistan.core.masking.InputSource
import tr.yasliasistan.core.masking.MaskedScreen
import tr.yasliasistan.core.masking.MaskingSession
import tr.yasliasistan.core.masking.ScreenMaskResult
import tr.yasliasistan.core.masking.ScreenScope
import tr.yasliasistan.core.screen.ScreenSnapshot

/** Sentences of the language-model layer; the app implements them with strings.xml. */
interface LlmPhrases {
    /** "Bu iş için internet yardımı gerekiyor, izin vermediniz." (FG-103) */
    fun noConsent(): String

    /** "Bu iş için internet yardımı gerekiyor ama bu telefonda henüz kurulmadı. Bildiğim işlerle devam edebilirim." */
    fun notConfigured(): String

    /** "İnternet yardımı şu an dolu, bildiğim işlerle devam edebilirim." (NFG-25) */
    fun quotaExhausted(): String

    /** "İnternete şu an ulaşamadım. Hiçbir şey yapmadım; biraz sonra yeniden deneyebilirsiniz." (NFG-32) */
    fun unavailable(): String

    /** "İnternet yardımının erişim kodu kabul edilmedi. Hiçbir şey yapmadım." */
    fun unauthorized(): String

    /** "Ekranda ya da sözünüzde gizli bilgi var, bunu internete soramam." (FG-79) */
    fun privateData(): String

    /** "Cevabı anlayamadım, hiçbir şey yapmadım." (NFG-31) */
    fun notUnderstood(): String

    /** "Bu işi henüz kendim yapamıyorum." (until G-38 runs plans) */
    fun planNotRunnable(): String
}

/**
 * The third decision layer (FG-40): language model through the proxy, used only when rules and
 * recipes could not answer (DecisionEngine order). It replaces [tr.yasliasistan.core.decision.LanguageModelOff].
 *
 * One call per command: mask command (G-31) → mask screen (G-32) → final audit → [CloudClient]
 * → strict parse (G-35) → unmask and validate on the device. The answer is a plan, one
 * clarification question, or "cannot do" in the same call (FG-100). The answer to the question
 * makes one more call that may not ask again (FG-06). Plans are handed to [onPlan]; G-38 plugs
 * the "Sen yap" executor, readback and risk check (G-25, FG-74) in there.
 *
 * Every failure ends with a spoken sentence and no action (NFG-32). Runs on the worker thread.
 */
class LanguageModelLayer(
    private val client: CloudClient,
    private val phrases: LlmPhrases,
    private val newMasking: () -> MaskingSession,
    private val screen: () -> ScreenSnapshot?,
    private val scope: () -> ScreenScope,
    private val installedApps: () -> List<InstalledApp>,
    private val inputSource: () -> InputSource = { InputSource.SPEECH },
    private val onPlan: (PlannedTask) -> Decision = { Decision.Say(phrases.planNotRunnable()) },
) : DecisionLayer {

    override fun decide(command: Command): Decision = ask(command.raw, followUp = false)

    private fun ask(text: String, followUp: Boolean): Decision {
        client.precheck()?.let { return say(it) }
        val masking = newMasking()
        try {
            val prepared = prepare(masking, text) ?: return Decision.Say(phrases.privateData())
            val outcome = client.generate(prepared.request, PromptExtras(prepared.apps, followUp))
            val answer = when (outcome) {
                is CloudOutcome.Failed -> return say(outcome.failure)
                is CloudOutcome.Answer -> outcome.result
            }
            val reply = when (val parsed = ModelReplyParser.parse(answer)) {
                is ParseResult.Rejected -> return rejected(parsed.error)
                is ParseResult.Ok -> parsed.reply
            }
            val decoded = ReplyDecoder(masking, prepared.snapshot, prepared.sentScreen, prepared.apps).decode(reply)
            return toDecision(decoded, text, followUp)
        } finally {
            masking.clear() // forget every real value of this call
        }
    }

    private class Prepared(
        val request: AuditedRequest,
        val snapshot: ScreenSnapshot,
        val sentScreen: MaskedScreen?,
        val apps: List<InstalledApp>,
    )

    /** Masks and audits; null when anything must not leave the phone (nothing is sent then). */
    private fun prepare(masking: MaskingSession, text: String): Prepared? {
        val command = when (val m = masking.maskCommand(text, inputSource())) {
            is CommandMaskResult.Blocked -> return null
            is CommandMaskResult.Masked -> m.text
        }
        val currentScope = scope()
        val snapshot = screen()
        val masked = snapshot?.let {
            when (val s = masking.maskScreen(it, currentScope)) {
                is ScreenMaskResult.Blocked -> return null
                is ScreenMaskResult.Masked -> s.screen
            }
        }
        val request = when (val a = masking.audit(command, masked)) {
            is AuditResult.Blocked -> return null
            is AuditResult.Passed -> a.request
        }
        val apps = installedApps().filter { it.packageName in currentScope.allowedPackages }
        return Prepared(request, snapshot ?: ScreenSnapshot.EMPTY, masked, apps)
    }

    private fun toDecision(reply: DecodedReply, text: String, followUp: Boolean): Decision = when (reply) {
        is DecodedReply.Plan -> onPlan(reply.task)
        is DecodedReply.CannotDo -> Decision.Say(reply.reason)
        is DecodedReply.Rejected -> rejected(reply.error)
        is DecodedReply.Question ->
            if (followUp) {
                rejected(ReplyError.NOT_EXACTLY_ONE) // a second question is never asked (FG-06)
            } else {
                Decision.Ask(reply.text, FollowUp(text, reply.text), candidates = 0)
            }
    }

    /** The user's answer goes back with the original command and the question, masked again. */
    private inner class FollowUp(private val original: String, private val question: String) : PendingQuestion {
        override fun answer(command: Command): Decision =
            ask("$original\nSoru: $question\nCevap: ${command.raw}", followUp = true)
    }

    private fun rejected(error: ReplyError): Decision {
        SafeLog.event(TAG, "answer rejected: $error")
        return Decision.Say(phrases.notUnderstood())
    }

    private fun say(failure: CloudFailure): Decision = Decision.Say(
        when (failure) {
            CloudFailure.NO_CONSENT -> phrases.noConsent()
            CloudFailure.NOT_CONFIGURED -> phrases.notConfigured()
            CloudFailure.QUOTA_EXHAUSTED -> phrases.quotaExhausted()
            CloudFailure.UNAUTHORIZED -> phrases.unauthorized()
            CloudFailure.BAD_RESPONSE -> phrases.notUnderstood()
            CloudFailure.RATE_LIMITED, CloudFailure.UNAVAILABLE, CloudFailure.NETWORK, CloudFailure.TIMEOUT,
            CloudFailure.REQUEST_REJECTED -> phrases.unavailable()
        },
    )

    private companion object {
        const val TAG = "LanguageModel"
    }
}
