package tr.yasliasistan.core.context

import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.clarify.IndirectPhrases
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.DefaultRiskAssessor
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.risk.RiskAssessor

/**
 * Short-term context of the conversation (PLAN G-39; FG-08, FG-09, FG-15, NFG-09), used by the
 * decision coordinator:
 * - [explanation]: "neden?" -> one line; asked again or "biraz daha anlat" -> the grounds;
 * - [prepare]: "tekrar / aynısını yap" -> the last action again; "onu da ara", "bunu aç" -> the
 *   command with the person / app / screen element filled in;
 * - [record] / [executed]: what was decided and done, for the next command.
 *
 * Privacy: everything stays in RAM and is forgotten [ttlMs] after the last command; logs carry
 * only reason codes (NFG-46, KVKK). Calls are serialized by the coordinator.
 */
class ConversationContext(
    private val phrases: ExplainPhrases,
    private val focus: ScreenFocusSource = ScreenFocusSource.NONE,
    risk: RiskAssessor = DefaultRiskAssessor,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    ttlMs: Long = Defaults.CONTEXT_TTL_MS,
) {
    private val memory = ConversationMemory(clock, ttlMs)
    private val explainer = Explainer(phrases, risk)

    /** A command prepared for the decision engine. */
    internal sealed interface Prepared {
        /** Decide [command] as usual; [rewritten] = references were filled in (rules only, see coordinator). */
        class Decide(val command: Command, val reason: Reason, val rewritten: Boolean) : Prepared

        /** "Tekrar": the decision is already known. */
        class Ready(val decision: Decision, val reason: Reason) : Prepared
    }

    /** The answer to "neden?" / "biraz daha anlat"; null when [command] is not such a question. */
    internal fun explanation(command: Command): String? {
        val tokens = command.tokens
        val why = ContextWords.isWhy(tokens)
        val detail = !why && ContextWords.isDetail(tokens)
        if (!why && !detail) return null
        return explain(detail = detail)
    }

    /**
     * Layered explanation of the last answer: layer 1 first, the grounds when [detail] is asked or
     * "neden?" comes again. Entry point for the fixed "neden" command of G-17 as well.
     */
    fun explain(detail: Boolean = false): String {
        val turn = memory.turn()
        val level = if (detail || memory.explainLevel >= 1) 2 else 1
        if (turn != null) memory.explainLevel = level
        SafeLog.event(TAG, "why level=$level reason=${turn?.reason?.code ?: "NONE"}")
        return if (level == 1) explainer.layer1(turn) else explainer.layer2(turn)
    }

    internal fun prepare(command: Command): Prepared {
        if (ContextWords.isRepeat(command.tokens)) {
            val last = memory.lastExecuted()
            SafeLog.event(TAG, "repeat found=${last != null}")
            return Prepared.Ready(last ?: Decision.Say(phrases.nothingRecent()), Reason.Repeat)
        }
        val rewrite = References.rewrite(command, focus::current, memory.lastContact(), memory.lastApp())
        if (rewrite != null) {
            SafeLog.event(TAG, "reference=${rewrite.reason.code}")
            return Prepared.Decide(rewrite.command, rewrite.reason, rewritten = true)
        }
        val indirect = IndirectPhrases.match(command.tokens)
        if (indirect != null) SafeLog.event(TAG, "indirect=$indirect")
        return Prepared.Decide(command, indirect?.let(Reason::Indirect) ?: Reason.Direct, rewritten = false)
    }

    internal fun record(decision: Decision, reason: Reason) {
        val fitting = if (reason is Reason.Indirect && !isSettingsAction(decision)) Reason.Direct else reason
        memory.record(Turn(decision, fitting))
    }

    internal fun executed(action: Action) = memory.executed(action)

    private fun isSettingsAction(decision: Decision): Boolean {
        val action = (decision as? Decision.Act)?.action
        return action is Action.AdjustVolume || action is Action.OpenSystemScreen
    }

    private companion object {
        const val TAG = "Context"
    }
}
