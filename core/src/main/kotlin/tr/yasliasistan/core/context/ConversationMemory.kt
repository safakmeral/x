package tr.yasliasistan.core.context

import tr.yasliasistan.core.clarify.IndirectKind
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.masking.ContactName

/** Why the assistant understood a command the way it did (layer 1 of "neden?", FG-15). */
internal sealed interface Reason {
    data object Direct : Reason

    data class Indirect(val kind: IndirectKind) : Reason

    /** "onu / ona / tekrar" pointed at the previous person or app. */
    data class Previous(val name: String) : Reason {
        override fun toString() = "Previous"
    }

    /** "bu / şu" pointed at the focused element or the active app. */
    data class OnScreen(val label: String) : Reason {
        override fun toString() = "OnScreen"
    }

    data object Repeat : Reason

    val code: String get() = this::class.simpleName ?: "Reason"
}

/** The last answered command: its decision, why, and whether its action ran. */
internal class Turn(val decision: Decision, val reason: Reason) {
    var executed = false
}

/**
 * Short-term conversation memory (PLAN G-39, FG-08). Each bubble tap is its own dialog session, so
 * "onu da ara", "tekrar" and "neden?" refer to the previous session: the memory therefore outlives
 * one session but forgets everything [ttlMs] after the last command, and lives only in RAM (never
 * stored or logged; see docs/SAPMALAR.md). Not thread-safe: the decision coordinator serializes calls.
 */
internal class ConversationMemory(private val clock: () -> Long, private val ttlMs: Long) {
    private var touchedAt = 0L
    private var turn: Turn? = null
    private var lastExecuted: Decision.Act? = null
    private var lastContact: ContactName? = null
    private var lastApp: InstalledApp? = null

    /** 0 = not explained yet, 1 = one-line answer given, 2 = grounds given. */
    var explainLevel = 0

    fun record(next: Turn) {
        expireIfOld()
        turn = next
        explainLevel = 0
        (next.decision as? Decision.Act)?.action?.let(::rememberReferent)
        touchedAt = clock()
    }

    /** The action handed out for execution has run (or at least started). */
    fun executed(action: Action) {
        val current = turn ?: return
        val act = current.decision as? Decision.Act ?: return
        if (act.action != action) return
        current.executed = true
        lastExecuted = act
        touchedAt = clock()
    }

    fun turn(): Turn? = fresh { turn }

    fun lastExecuted(): Decision.Act? = fresh { lastExecuted }

    fun lastContact(): ContactName? = fresh { lastContact }

    fun lastApp(): InstalledApp? = fresh { lastApp }

    fun clear() {
        turn = null
        lastExecuted = null
        lastContact = null
        lastApp = null
        explainLevel = 0
    }

    private fun rememberReferent(action: Action) {
        when (action) {
            is Action.CallContact -> lastContact = action.contact
            is Action.MessageContact -> lastContact = action.contact
            is Action.OpenApp -> lastApp = action.app
            else -> Unit
        }
    }

    private fun <T> fresh(read: () -> T): T? {
        expireIfOld()
        return read()
    }

    private fun expireIfOld() {
        if (turn != null && clock() - touchedAt > ttlMs) clear()
    }
}
