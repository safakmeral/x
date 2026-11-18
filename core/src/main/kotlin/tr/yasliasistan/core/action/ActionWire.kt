package tr.yasliasistan.core.action

import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot

/**
 * Flat, serialization-friendly form of a [ScreenAction] (G-35 maps the model's JSON onto it).
 * There are no coordinate fields: a model can only name an element id that was on the screen
 * summary it saw (TK-14). [text] is a typed value after unmasking (G-31); never logged.
 */
data class WireAction(
    val kind: String,
    val element: Int? = null,
    val direction: String? = null,
    val length: String? = null,
    val text: String? = null,
    val app: String? = null,
    val waitMs: Long? = null,
    val reason: String? = null,
) {
    override fun toString() = "WireAction(kind=$kind, element=$element)"
}

/** Why a proposed action is not accepted; the step is dropped and counted as a planning error. */
enum class WireError {
    UNKNOWN_KIND, MISSING_ELEMENT, UNKNOWN_ELEMENT, BAD_DIRECTION, BAD_LENGTH, BAD_TEXT,
    NOT_EDITABLE, SECRET_FIELD, UNKNOWN_APP, BAD_WAIT, BAD_REASON,
}

sealed interface WireResult {
    data class Ok(val action: ScreenAction) : WireResult
    data class Invalid(val error: WireError) : WireResult
}

/**
 * Turns a [WireAction] into a [ScreenAction] against the snapshot the proposal was made for.
 * Only element ids from that snapshot and launchable apps from [apps] are accepted; typing into a
 * password/sensitive or non-editable field is rejected here already (FG-78).
 */
object ActionSchema {

    /**
     * Version of the action schema (PLAN G-20). G-35's language-model answer format names this
     * version; bump it whenever a kind or field changes so old recipes and answers are rejected.
     */
    const val VERSION: Int = 1

    fun parse(wire: WireAction, snapshot: ScreenSnapshot, apps: List<InstalledApp>): WireResult {
        val kind = ActionKind.ofWire(wire.kind) ?: return invalid(WireError.UNKNOWN_KIND)
        return when (kind) {
            ActionKind.TAP -> element(wire, snapshot) { ScreenAction.Tap(ElementRef.of(it)) }
            ActionKind.LONG_PRESS -> element(wire, snapshot) { ScreenAction.LongPress(ElementRef.of(it)) }
            ActionKind.SCROLL -> scroll(wire, snapshot)
            ActionKind.TYPE -> type(wire, snapshot)
            ActionKind.BACK -> WireResult.Ok(ScreenAction.Back)
            ActionKind.HOME -> WireResult.Ok(ScreenAction.Home)
            ActionKind.OPEN_APP -> apps.firstOrNull { it.packageName == wire.app }
                ?.let { WireResult.Ok(ScreenAction.OpenApp(it)) } ?: invalid(WireError.UNKNOWN_APP)
            ActionKind.WAIT -> wire.waitMs?.takeIf { it in 1..ScreenAction.MAX_WAIT_MS }
                ?.let { WireResult.Ok(ScreenAction.Wait(it)) } ?: invalid(WireError.BAD_WAIT)
            ActionKind.HAND_OVER -> HandOverReason.ofWire(wire.reason)
                ?.let { WireResult.Ok(ScreenAction.HandOver(it)) } ?: invalid(WireError.BAD_REASON)
            ActionKind.DONE -> WireResult.Ok(ScreenAction.Done)
        }
    }

    /** Inverse of [parse] for recipes (G-41/G-42) and tests; [WireAction.text] carries the value. */
    fun toWire(action: ScreenAction): WireAction = when (action) {
        is ScreenAction.Tap -> WireAction(action.kind.wireName, element = action.target.id)
        is ScreenAction.LongPress -> WireAction(action.kind.wireName, element = action.target.id)
        is ScreenAction.Scroll -> WireAction(
            action.kind.wireName,
            element = action.target?.id,
            direction = action.direction.wireName,
            length = action.length.wireName,
        )
        is ScreenAction.TypeText -> WireAction(action.kind.wireName, element = action.target.id, text = action.text)
        ScreenAction.Back, ScreenAction.Home, ScreenAction.Done -> WireAction(action.kind.wireName)
        is ScreenAction.OpenApp -> WireAction(action.kind.wireName, app = action.app.packageName)
        is ScreenAction.Wait -> WireAction(action.kind.wireName, waitMs = action.millis)
        is ScreenAction.HandOver -> WireAction(action.kind.wireName, reason = action.reason.wireName)
    }

    private inline fun element(
        wire: WireAction,
        snapshot: ScreenSnapshot,
        build: (ScreenElement) -> ScreenAction,
    ): WireResult {
        val id = wire.element ?: return invalid(WireError.MISSING_ELEMENT)
        val element = snapshot.element(id) ?: return invalid(WireError.UNKNOWN_ELEMENT)
        if (element.secret) return invalid(WireError.SECRET_FIELD)
        return WireResult.Ok(build(element))
    }

    private fun scroll(wire: WireAction, snapshot: ScreenSnapshot): WireResult {
        val direction = ScrollDirection.ofWire(wire.direction) ?: return invalid(WireError.BAD_DIRECTION)
        val length = if (wire.length == null) ScrollLength.MEDIUM else {
            ScrollLength.ofWire(wire.length) ?: return invalid(WireError.BAD_LENGTH)
        }
        if (wire.element == null) return WireResult.Ok(ScreenAction.Scroll(null, direction, length))
        return element(wire, snapshot) { ScreenAction.Scroll(ElementRef.of(it), direction, length) }
    }

    private fun type(wire: WireAction, snapshot: ScreenSnapshot): WireResult {
        val text = wire.text
        if (text.isNullOrBlank() || text.length > ScreenAction.MAX_TEXT_CHARS) return invalid(WireError.BAD_TEXT)
        val id = wire.element ?: return invalid(WireError.MISSING_ELEMENT)
        val element = snapshot.element(id) ?: return invalid(WireError.UNKNOWN_ELEMENT)
        if (element.secret) return invalid(WireError.SECRET_FIELD)
        if (!element.editable) return invalid(WireError.NOT_EDITABLE)
        return WireResult.Ok(ScreenAction.TypeText(ElementRef.of(element), text))
    }

    private fun invalid(error: WireError) = WireResult.Invalid(error)
}
