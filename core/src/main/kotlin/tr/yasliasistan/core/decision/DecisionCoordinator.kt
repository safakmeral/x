package tr.yasliasistan.core.decision

import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.context.ConversationContext
import tr.yasliasistan.core.context.ExplainPhrases
import tr.yasliasistan.core.context.Reason
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.risk.Risk
import tr.yasliasistan.core.risk.RiskAssessor

/**
 * Connects the [DecisionEngine] to the dialog machine (PLAN G-10).
 *
 * Host usage (G-11): on `DialogEffect.Decide` send [decide]'s result back to the dialog session;
 * on `DialogEffect.Execute` run [takeAction]; on `DialogEffect.SessionStarted` call [reset].
 *
 * Confirmation follows the risk level (student's Table 3): a certain, Low-risk action is announced
 * and done at once (FG-71, [DialogEvent.Proceeding]); Medium/High risk or an uncertain (partial)
 * match gets the one-sentence readback question first (FG-07, FG-72, FG-73, [DialogEvent.Understood]).
 */
class DecisionCoordinator(
    private val engine: DecisionEngine,
    private val phrases: DecisionPhrases,
    private val risk: RiskAssessor = DefaultRiskAssessor,
    private val maxSpokenOptions: Int = Defaults.MAX_SPOKEN_OPTIONS,
    /**
     * G-39 short-term context ("neden?", "tekrar", "onu da ara", indirect phrases). On by default
     * when [phrases] also supplies the explanation sentences; it outlives [reset] (see the class).
     */
    private val context: ConversationContext? =
        (phrases as? ExplainPhrases)?.let { ConversationContext(it, risk = risk) },
) {
    private var pendingChoice: List<Action>? = null
    private var pendingQuestion: PendingQuestion? = null
    private var decidedId: Int? = null
    private var decidedAction: Action? = null

    fun decide(requestId: Int, transcript: String, partialOnly: Boolean = false): DialogEvent =
        decideTraced(requestId, transcript, partialOnly).event

    /**
     * The dialog event for a command plus the layer that answered it (metrics, G-18).
     * [unnecessaryQuestion]: a "which one?" question with a single candidate (FG-102, DEG-31).
     */
    data class Traced(val event: DialogEvent, val source: DecisionSource, val unnecessaryQuestion: Boolean = false)

    /**
     * [partialOnly]: the transcript is the recognizer's unconfirmed partial text, so even a certain,
     * Low-risk action is read back and asked first (NFG-18; UD-1 Ö-1).
     */
    @Synchronized
    fun decideTraced(requestId: Int, transcript: String, partialOnly: Boolean = false): Traced {
        val command = Command(transcript)
        // G-39: "neden?" explains the last answer; an open question stays open for its answer.
        context?.explanation(command)?.let { text ->
            val open = pendingQuestion != null || pendingChoice != null
            return Traced(DialogEvent.Replied(requestId, text, expectsAnswer = open), DecisionSource.RULE)
        }
        val choice = pendingChoice
        val question = pendingQuestion
        pendingChoice = null
        pendingQuestion = null
        var reason: Reason = Reason.Direct
        val newCommand = { decideNew(command).also { reason = it.second }.first }
        val sourced = if (question != null) {
            // The answer to the one clarification question; null = it was a new command instead.
            question.answer(command)?.let { SourcedDecision(noSecondQuestion(it), DecisionSource.RULE) }
                ?: newCommand()
        } else if (choice == null) {
            newCommand()
        } else {
            when (val answer = ChoiceResolver.resolve(command, choice)) {
                // An explicit pick from the offered names is certain; the choice resolver is an on-device rule.
                is ChoiceResolver.Result.Picked -> SourcedDecision(Decision.Act(answer.action, certain = true), DecisionSource.RULE)
                ChoiceResolver.Result.Rejected ->
                    return Traced(DialogEvent.Replied(requestId, phrases.cancelled(), false), DecisionSource.RULE)
                ChoiceResolver.Result.NotAnAnswer -> newCommand() // A new command instead.
            }
        }
        val decision = sourced.decision
        context?.record(decision, reason)
        val unnecessary = (decision is Decision.Ask && decision.candidates == 1) ||
            (decision is Decision.Choose && decision.options.size < 2)
        return Traced(toEvent(requestId, decision, partialOnly), sourced.source, unnecessary)
    }

    /**
     * A new command with its G-39 context: "tekrar" reuses the last action; a command with filled-in
     * references is tried on the rules only, otherwise the user's own words go through all layers.
     */
    private fun decideNew(command: Command): Pair<SourcedDecision, Reason> {
        val ctx = context ?: return engine.resolve(command) to Reason.Direct
        return when (val prepared = ctx.prepare(command)) {
            is ConversationContext.Prepared.Ready -> SourcedDecision(prepared.decision, DecisionSource.RULE) to prepared.reason
            is ConversationContext.Prepared.Decide ->
                if (prepared.rewritten) {
                    engine.resolveByRules(prepared.command)?.let { it to prepared.reason }
                        ?: (engine.resolve(command) to Reason.Direct)
                } else {
                    engine.resolve(prepared.command) to prepared.reason
                }
        }
    }

    /** FG-06: at most one clarification question per command; an answer never asks again. */
    private fun noSecondQuestion(decision: Decision): Decision = when (decision) {
        is Decision.Ask, is Decision.Choose -> Decision.CannotDo
        else -> decision
    }

    /** The action decided for [requestId], handed out once; null if none or already taken. */
    @Synchronized
    fun takeAction(requestId: Int): Action? {
        if (decidedId != requestId) return null
        return decidedAction.also {
            decidedId = null
            decidedAction = null
            if (it != null) context?.executed(it)
        }
    }

    /**
     * "Neden?" from outside a decision (G-17 fixed command): layered explanation of the last answer;
     * null when the G-39 context is off.
     */
    @Synchronized
    fun explainLast(detail: Boolean = false): String? = context?.explain(detail)

    /**
     * Forget the open question and pending action; call when a new session starts. The short-term
     * G-39 context is kept on purpose (each bubble tap is a new session) and expires by itself.
     */
    @Synchronized
    fun reset() {
        pendingChoice = null
        pendingQuestion = null
        decidedId = null
        decidedAction = null
    }

    private fun toEvent(requestId: Int, decision: Decision, partialOnly: Boolean): DialogEvent = when (decision) {
        is Decision.Act -> {
            decidedId = requestId
            decidedAction = decision.action
            val level = risk.assess(decision.action)
            val askFirst = !decision.certain || partialOnly || level.needsConfirmation
            if (askFirst) {
                DialogEvent.Understood(requestId, decision.readback ?: readback(decision.action), lowRisk = level == Risk.LOW)
            } else {
                DialogEvent.Proceeding(requestId, decision.announcement ?: announcement(decision.action))
            }
        }
        is Decision.Choose -> {
            pendingChoice = decision.options
            val text = if (decision.options.size > maxSpokenOptions) {
                phrases.tooManyMatches()
            } else {
                phrases.chooseApp(decision.options.map { it.spokenName })
            }
            DialogEvent.Replied(requestId, text, expectsAnswer = true)
        }
        is Decision.Ask -> {
            pendingQuestion = decision.pending
            DialogEvent.Replied(requestId, decision.text, expectsAnswer = true)
        }
        is Decision.Say -> DialogEvent.Replied(requestId, decision.text, expectsAnswer = false)
        Decision.CannotDo -> DialogEvent.Replied(requestId, phrases.cannotDoYet(), expectsAnswer = false)
    }

    // Other actions always carry their own sentences (Decision.Act init check).
    private fun readback(action: Action) = phrases.confirmOpenApp(action.spokenName)

    private fun announcement(action: Action) = phrases.openingApp(action.spokenName)
}
