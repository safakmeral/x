package tr.yasliasistan.core.decision

/** Which layer of the decision order answered a command (FG-90 "çözen katman", DEG-19). */
enum class DecisionSource {
    /** On-device rule (installed apps, contacts, settings), including picking from offered options. */
    RULE,

    /** Saved task recipe (G-44..G-46). */
    RECIPE,

    /** Language model through the proxy (G-34, G-38). */
    LLM,

    /** No layer could handle the command. */
    NONE,
}

/** A decision together with the layer that produced it. */
data class SourcedDecision(val decision: Decision, val source: DecisionSource)
