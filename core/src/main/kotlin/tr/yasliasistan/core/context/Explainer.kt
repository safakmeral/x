package tr.yasliasistan.core.context

import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.VolumeChange
import tr.yasliasistan.core.risk.RiskAssessor

/**
 * Layered answer to "neden?" (PLAN G-39, FG-15): layer 1 is one line (why + what was done),
 * layer 2 the grounds, taken from what the assistant saw on the screen or in the device data
 * (NFG-09), plus why it did or did not ask first (risk level).
 */
internal class Explainer(private val phrases: ExplainPhrases, private val risk: RiskAssessor) {

    fun layer1(turn: Turn?): String {
        turn ?: return phrases.nothingRecent()
        return when (val d = turn.decision) {
            is Decision.Act -> {
                val why = why(turn.reason)
                if (turn.executed) phrases.because(why, done(d.action)) else why
            }
            is Decision.Ask -> if (d.candidates > 1) phrases.askedSeveral() else phrases.askedMissing()
            is Decision.Choose -> phrases.askedSeveral()
            is Decision.Say -> d.text
            Decision.CannotDo -> phrases.cannotDo()
        }
    }

    fun layer2(turn: Turn?): String {
        turn ?: return phrases.nothingRecent()
        return when (val d = turn.decision) {
            is Decision.Act -> listOfNotNull(basis(turn.reason, d), riskNote(d.action)).joinToString(" ")
            is Decision.Ask, is Decision.Choose -> phrases.askDetail()
            is Decision.Say -> phrases.noMoreDetail()
            Decision.CannotDo -> phrases.cannotDoDetail()
        }
    }

    private fun why(reason: Reason): String = when (reason) {
        Reason.Direct -> phrases.youAsked()
        is Reason.Indirect -> phrases.indirectCue(reason.kind)
        is Reason.Previous -> phrases.previousReference(reason.name)
        is Reason.OnScreen -> phrases.screenReference(reason.label)
        Reason.Repeat -> phrases.repeated()
    }

    // The else branch keeps the build green while G-20 adds new actions.
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun done(action: Action): String = when (action) {
        is Action.OpenApp -> phrases.doneOpenApp(action.app.label)
        is Action.CallContact -> phrases.doneCall(action.contact.displayName)
        is Action.MessageContact -> phrases.doneMessage(action.contact.displayName)
        is Action.AdjustVolume -> phrases.doneVolume(
            when (action.change) {
                VolumeChange.UP -> true
                VolumeChange.DOWN -> false
                VolumeChange.SHOW -> null
            },
        )
        is Action.OpenSystemScreen -> phrases.doneDisplaySettings()
        Action.RequestContactsPermission -> phrases.doneContactsPermission()
        else -> phrases.doneOther() // actions added later (G-20) until they get their own sentence
    }

    /** What the decision was based on: the screen first (NFG-09), then device data. */
    private fun basis(reason: Reason, act: Decision.Act): String? {
        if (reason is Reason.OnScreen) return phrases.screenBasis(reason.label)
        return when (val action = act.action) {
            is Action.OpenApp -> phrases.appBasis(action.app.label)
            is Action.CallContact -> contactBasis(action.contact.displayName, act.certain)
            is Action.MessageContact -> contactBasis(action.contact.displayName, act.certain)
            else -> null
        }
    }

    private fun contactBasis(name: String, exact: Boolean) =
        if (exact) phrases.contactBasis(name) else phrases.contactBasisInexact(name)

    private fun riskNote(action: Action): String =
        if (risk.assess(action).needsConfirmation) phrases.confirmNote() else phrases.undoNote()
}
