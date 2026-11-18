package tr.yasliasistan.core.action

import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.ScreenElement

/** Kinds of screen actions with their wire names (the only actions a model may propose, TK-14). */
enum class ActionKind(val wireName: String) {
    TAP("tap"),
    LONG_PRESS("long_press"),
    SCROLL("scroll"),
    TYPE("type"),
    BACK("back"),
    HOME("home"),
    OPEN_APP("open_app"),
    WAIT("wait"),
    HAND_OVER("hand_over"),
    DONE("done"),
    ;

    companion object {
        fun ofWire(name: String?): ActionKind? = entries.firstOrNull { it.wireName == name }
    }
}

/** Direction in which the content moves into view: DOWN shows what is further down (finger moves up). */
enum class ScrollDirection(val wireName: String) {
    UP("up"), DOWN("down"), LEFT("left"), RIGHT("right");

    val vertical: Boolean get() = this == UP || this == DOWN

    companion object {
        fun ofWire(name: String?): ScrollDirection? = entries.firstOrNull { it.wireName == name }
    }
}

/** V-10 / TK-14: scroll length as a share of the scrolled element's visible size. */
enum class ScrollLength(val wireName: String, val fraction: Float) {
    SHORT("short", 0.3f), MEDIUM("medium", 0.5f), LONG("long", 0.75f);

    companion object {
        fun ofWire(name: String?): ScrollLength? = entries.firstOrNull { it.wireName == name }
    }
}

/** Why the assistant gives the task back to the user (FG-26..FG-28 use these in G-23, G-26). */
enum class HandOverReason(val wireName: String) {
    /** The user must choose (which photo, which message). */
    USER_CHOICE("user_choice"),

    /** A password or sensitive field: only the user types there (FG-78, G-26). */
    SECRET_FIELD("secret_field"),

    /** The target could not be found or recognized (NFG-30). */
    NOT_FOUND("not_found"),

    /** The model or rule is not sure what to do next. */
    UNSURE("unsure"),
    ;

    companion object {
        fun ofWire(name: String?): HandOverReason? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * The element an action was planned on, as it looked in the snapshot used for planning (NFG-38).
 * The executor acts only if the live screen still shows this element at (almost) the same place.
 * [label] may be personal data: kept in memory only, hidden from toString.
 */
data class ElementRef(
    val id: Int,
    val role: ElementRole,
    val label: String?,
    val bounds: Bounds,
    val windowId: Int,
    val packageName: String?,
) {
    override fun toString() = "ElementRef(id=$id, role=$role)"

    companion object {
        fun of(element: ScreenElement) = ElementRef(
            id = element.id,
            role = element.role,
            label = element.label,
            bounds = element.bounds,
            windowId = element.windowId,
            packageName = element.packageName,
        )
    }
}

/**
 * One step of "Sen yap" mode (PLAN G-20; TK-14: element id + scroll direction/length, no free
 * coordinates). Taps and scrolls always aim at a numbered element; the executor derives any
 * gesture point from that element's live bounds (coordinates only as a fallback, V-10).
 */
sealed interface ScreenAction {
    val kind: ActionKind

    /** Actions aimed at one element. */
    sealed interface Targeted : ScreenAction {
        val target: ElementRef
    }

    data class Tap(override val target: ElementRef) : Targeted {
        override val kind get() = ActionKind.TAP
    }

    data class LongPress(override val target: ElementRef) : Targeted {
        override val kind get() = ActionKind.LONG_PRESS
    }

    /** [target] null: the largest scrollable element of the app in front. */
    data class Scroll(val target: ElementRef?, val direction: ScrollDirection, val length: ScrollLength) : ScreenAction {
        override val kind get() = ActionKind.SCROLL
    }

    /** Replace the text of an editable, non-secret field. [text] is never logged. */
    data class TypeText(override val target: ElementRef, val text: String) : Targeted {
        override val kind get() = ActionKind.TYPE

        init {
            require(text.isNotBlank() && text.length <= MAX_TEXT_CHARS) { "invalid text length" }
        }

        override fun toString() = "TypeText(target=$target, chars=${text.length})"
    }

    data object Back : ScreenAction {
        override val kind get() = ActionKind.BACK
    }

    data object Home : ScreenAction {
        override val kind get() = ActionKind.HOME
    }

    data class OpenApp(val app: InstalledApp) : ScreenAction {
        override val kind get() = ActionKind.OPEN_APP
    }

    /** Let a slow screen finish loading; capped at [MAX_WAIT_MS]. */
    data class Wait(val millis: Long) : ScreenAction {
        override val kind get() = ActionKind.WAIT

        init {
            require(millis in 1..MAX_WAIT_MS) { "wait out of range" }
        }
    }

    /** Stop and give the task back to the user ("devret"). */
    data class HandOver(val reason: HandOverReason) : ScreenAction {
        override val kind get() = ActionKind.HAND_OVER
    }

    /** The task is finished ("bitti"). */
    data object Done : ScreenAction {
        override val kind get() = ActionKind.DONE
    }

    companion object {
        /** Longest text the assistant types for the user (a short message or search). */
        const val MAX_TEXT_CHARS: Int = 500
        const val MAX_WAIT_MS: Long = 3_000
    }
}
