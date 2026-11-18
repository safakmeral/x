package tr.yasliasistan.core.llm

import tr.yasliasistan.core.action.ActionSchema
import tr.yasliasistan.core.action.WireResult
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.masking.MaskedScreen
import tr.yasliasistan.core.masking.MaskingSession
import tr.yasliasistan.core.masking.UnmaskResult
import tr.yasliasistan.core.screen.ScreenSnapshot

/**
 * Turns a parsed, still-masked answer into device values (PLAN G-35):
 * - every text field is unmasked with this task's [MaskingSession]; an unknown placeholder rejects
 *   the whole answer (docs/MASKELEME.md §8);
 * - every step must name an element that was in the summary actually sent ([sentScreen]) and pass
 *   [ActionSchema.parse] against the planning [snapshot] and the allowed [apps] (no secret fields,
 *   no unknown apps). One bad step rejects the whole plan: a partial plan is never run (NFG-32).
 */
class ReplyDecoder(
    private val masking: MaskingSession,
    private val snapshot: ScreenSnapshot,
    private val sentScreen: MaskedScreen?,
    private val apps: List<InstalledApp>,
) {
    fun decode(reply: ModelReply): DecodedReply = try {
        when (reply) {
            is ModelReply.Question -> DecodedReply.Question(unmask(reply.text))
            is ModelReply.CannotDo -> DecodedReply.CannotDo(unmask(reply.reason))
            is ModelReply.Plan -> DecodedReply.Plan(
                PlannedTask(unmask(reply.readback), reply.confident, reply.steps.map(::step)),
            )
        }
    } catch (e: Invalid) {
        DecodedReply.Rejected(e.error)
    }

    private fun step(step: WireStep): PlannedStep {
        val wire = step.action
        val sentIds = sentScreen?.elements?.mapTo(HashSet()) { it.id }.orEmpty()
        if (wire.element != null && wire.element !in sentIds) throw Invalid(ReplyError.ELEMENT_NOT_SENT)
        val unmasked = wire.copy(text = wire.text?.let(::unmask))
        val action = when (val r = ActionSchema.parse(unmasked, snapshot, apps)) {
            is WireResult.Ok -> r.action
            is WireResult.Invalid -> throw Invalid(ReplyError.INVALID_ACTION)
        }
        return PlannedStep(action, unmask(step.expected))
    }

    private fun unmask(text: String): String = when (val r = masking.unmask(text)) {
        is UnmaskResult.Restored -> r.text
        is UnmaskResult.Rejected -> throw Invalid(ReplyError.UNKNOWN_PLACEHOLDER)
    }

    private class Invalid(val error: ReplyError) : Exception(null, null, false, false)
}
