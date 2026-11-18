package tr.yasliasistan.core.decision

import tr.yasliasistan.core.log.SafeLog

/** One stage of the decision order; returns null when it cannot handle the command. */
fun interface DecisionLayer {
    fun decide(command: Command): Decision?
}

/** An on-device rule (uses device data only: installed apps, later contacts and settings). */
fun interface Rule {
    fun apply(command: Command): Decision?
}

/** The rule layer: the first rule that answers wins. */
class RuleLayer(private val rules: List<Rule>) : DecisionLayer {
    override fun decide(command: Command): Decision? = rules.firstNotNullOfOrNull { it.apply(command) }
}

/** Saved task recipes (PLAN G-44..G-46); empty until then. */
object NoRecipes : DecisionLayer {
    override fun decide(command: Command): Decision? = null
}

/** Language model through the proxy (PLAN G-34, G-38); switched off until then. */
object LanguageModelOff : DecisionLayer {
    override fun decide(command: Command): Decision? = null
}

/**
 * Decision engine (FG-40, NFG-24): rules first, then saved recipes, the language model last,
 * so the cloud is called only when nothing on the device can answer.
 * Stateless; conversation state (pending choice, chosen action) lives in [DecisionCoordinator].
 */
class DecisionEngine(
    private val rules: DecisionLayer,
    private val recipes: DecisionLayer = NoRecipes,
    private val languageModel: DecisionLayer = LanguageModelOff,
) {
    fun decide(command: Command): Decision = resolve(command).decision

    /** Like [decide], but also tells which layer answered (metrics, G-18). */
    fun resolve(command: Command): SourcedDecision {
        val layers = listOf(DecisionSource.RULE to rules, DecisionSource.RECIPE to recipes, DecisionSource.LLM to languageModel)
        for ((source, layer) in layers) {
            val decision = layer.decide(command) ?: continue
            SafeLog.event(TAG, "layer=$source result=${decision::class.simpleName}")
            return SourcedDecision(decision, source)
        }
        SafeLog.event(TAG, "layer=NONE result=CannotDo")
        return SourcedDecision(Decision.CannotDo, DecisionSource.NONE)
    }

    /**
     * Rules only (G-39): a command with filled-in references is tried on the device rules; when no
     * rule answers, the caller decides the user's original words with [resolve] instead, so recipes
     * and the language model never receive the rewritten text.
     */
    fun resolveByRules(command: Command): SourcedDecision? = rules.decide(command)?.let { decision ->
        SafeLog.event(TAG, "layer=${DecisionSource.RULE} result=${decision::class.simpleName}")
        SourcedDecision(decision, DecisionSource.RULE)
    }

    private companion object {
        const val TAG = "Decision"
    }
}
