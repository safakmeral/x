package tr.yasliasistan.core.risk

import tr.yasliasistan.core.decision.Action

/**
 * Risk level of a planned action (FG-70, student's Table 3).
 * Low: tell and do (FG-71). Medium and High: ask and wait; silence means "no" (FG-72, FG-73).
 */
enum class Risk {
    LOW, MEDIUM, HIGH;

    val needsConfirmation: Boolean get() = this != LOW
}

/**
 * Gives the risk of one action. Every decision, whatever layer produced it (rule, recipe, language
 * model), passes through this single point (FG-47). The rule-based classifier of PLAN G-25 plugs in here.
 */
fun interface RiskAssessor {
    fun assess(action: Action): Risk
}
