package tr.yasliasistan.core.context

import tr.yasliasistan.core.clarify.Apps
import tr.yasliasistan.core.clarify.ClarificationRule
import tr.yasliasistan.core.clarify.Harness
import tr.yasliasistan.core.clarify.IndirectKind
import tr.yasliasistan.core.clarify.TestDecisionPhrases
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionLayer
import tr.yasliasistan.core.decision.DecisionPhrases
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.decision.rules.OpenAppRule

/** Tagged test wording; the app supplies the Turkish sentences from strings.xml. */
internal object TestExplainPhrases : ExplainPhrases {
    override fun because(why: String, done: String) = "$why => $done"
    override fun youAsked() = "ASKED"
    override fun indirectCue(kind: IndirectKind) = "INDIRECT:$kind"
    override fun previousReference(name: String) = "PREV:$name"
    override fun screenReference(label: String) = "SCREEN:$label"
    override fun repeated() = "REPEATED"
    override fun doneOpenApp(label: String) = "OPENED:$label"
    override fun doneCall(name: String) = "DIALED:$name"
    override fun doneMessage(name: String) = "COMPOSED:$name"
    override fun doneVolume(up: Boolean?) = "VOLUME_DONE:$up"
    override fun doneDisplaySettings() = "DISPLAY_DONE"
    override fun doneContactsPermission() = "PERMISSION_DONE"
    override fun doneOther() = "DONE"
    override fun askedSeveral() = "ASKED_SEVERAL"
    override fun askedMissing() = "ASKED_MISSING"
    override fun cannotDo() = "CANNOT_DO"
    override fun nothingRecent() = "NOTHING"
    override fun screenBasis(label: String) = "SEEN:$label"
    override fun appBasis(label: String) = "INSTALLED:$label"
    override fun contactBasis(name: String) = "CONTACT:$name"
    override fun contactBasisInexact(name: String) = "NEAREST:$name"
    override fun confirmNote() = "CONFIRM_NOTE"
    override fun undoNote() = "UNDO_NOTE"
    override fun askDetail() = "ASK_DETAIL"
    override fun cannotDoDetail() = "CANNOT_DETAIL"
    override fun noMoreDetail() = "NO_MORE"
}

/** The app's phrases implement both interfaces; the coordinator then switches the context on itself. */
internal object BothPhrases : DecisionPhrases by TestDecisionPhrases, ExplainPhrases by TestExplainPhrases {
    override fun cancelled() = TestDecisionPhrases.cancelled()
}

/** Coordinator wired like the app, with a fake clock, a settable screen focus and a recording language model. */
internal class ContextHarness {
    var now = 0L
    var focus: ScreenFocus? = null
    val modelInputs = mutableListOf<String>()
    val base = Harness(apps = listOf(Apps.settings, Apps.whatsapp))
    val context = ConversationContext(TestExplainPhrases, { focus }, clock = { now })
    private val rules = RuleLayer(listOf(ClarificationRule(base.engine), OpenAppRule { base.apps }))
    private val model = DecisionLayer { command: Command -> modelInputs += command.raw; null }
    val coordinator = DecisionCoordinator(
        DecisionEngine(rules, languageModel = model),
        TestDecisionPhrases,
        context = context,
    )
    private var id = 0

    /** Decide one command; returns the request id used. */
    fun say(text: String): Pair<Int, DecisionCoordinator.Traced> {
        id += 1
        return id to coordinator.decideTraced(id, text)
    }

    /** Decide and run the action, like the session after "evet" / for low risk. */
    fun sayAndRun(text: String): DecisionCoordinator.Traced {
        val (requestId, traced) = say(text)
        coordinator.takeAction(requestId)
        coordinator.reset() // the session ends; the next bubble tap starts a new one
        return traced
    }
}
