package tr.yasliasistan.core.clarify

import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.Rule

/**
 * Puts the [ClarificationEngine] into the rule layer: "Unresolved" becomes null, so the decision
 * engine goes on to the next rule, then recipes, then the language model (FG-100, NFG-24).
 */
class ClarificationRule(private val engine: ClarificationEngine) : Rule {
    override fun apply(command: Command): Decision? = when (val result = engine.clarify(command)) {
        is ClarificationEngine.Result.Resolved -> result.decision
        ClarificationEngine.Result.Unresolved -> null
    }
}
