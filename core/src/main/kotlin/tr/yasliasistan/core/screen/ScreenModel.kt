package tr.yasliasistan.core.screen

/** What kind of control an element is (TK-14: the language model targets elements by id and role). */
enum class ElementRole {
    BUTTON, ICON_BUTTON, TEXT, TEXT_FIELD, PASSWORD_FIELD, CHECKBOX, SWITCH, RADIO, SLIDER,
    TAB, LIST, LIST_ITEM, IMAGE, WEB, OTHER,
}

/** Where an element's [ScreenElement.label] came from. */
enum class LabelSource { TEXT, DESCRIPTION, HINT, CHILD_TEXT, NONE }

/**
 * Conservative hint for G-32 masking about who wrote the label. It is only a hint: G-32 decides.
 * - UI_LABEL: label of an app control (button, tab, switch, field placeholder) outside lists.
 * - USER_INPUT: text inside an editable field (typed by someone).
 * - LIST_CONTENT: text inside a list item (contacts, messages, chat previews, but also settings rows).
 * - OTHER_TEXT: any other text (titles, headings, body text): may be personal.
 * - NONE: no label.
 */
enum class TextKind { UI_LABEL, USER_INPUT, LIST_CONTENT, OTHER_TEXT, NONE }

/**
 * One compressed, numbered screen element (PLAN G-19; TK-14 numbered element ids).
 * [label] is null for password/sensitive fields (only the role says "password field").
 * Kept in memory for the running task only; never logged or stored.
 */
data class ScreenElement(
    /** 1-based number in reading order, unique within one snapshot. */
    val id: Int,
    val role: ElementRole,
    val label: String?,
    val labelSource: LabelSource,
    val textKind: TextKind,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val selected: Boolean,
    val focused: Boolean,
    val heading: Boolean,
    /** The element is, or lies inside, an item of a list/grid. */
    val inListItem: Boolean,
    /** Text of a password or sensitive field: never read (FG-78, NFG-52). */
    val secret: Boolean,
    /** A clickable icon without any label (TK-04): the assistant must ask the user, not guess. */
    val unlabeledIcon: Boolean,
    val bounds: Bounds,
    val windowId: Int,
    val packageName: String?,
) {
    val actionable: Boolean get() = clickable || longClickable || scrollable || editable || checkable
}

/** A window that was read, without content. */
data class ScreenWindow(val id: Int, val kind: WindowKind, val packageName: String?, val active: Boolean)

/** The compressed screen of one moment of a task. */
data class ScreenSnapshot(
    val windows: List<ScreenWindow>,
    val elements: List<ScreenElement>,
    /** Some nodes were left out because a limit ([ScreenLimits]) was reached. */
    val truncated: Boolean,
) {
    fun element(id: Int): ScreenElement? = elements.getOrNull(id - 1)?.takeIf { it.id == id }

    val unlabeledIconCount: Int get() = elements.count { it.unlabeledIcon }

    companion object {
        val EMPTY = ScreenSnapshot(emptyList(), emptyList(), truncated = false)
    }
}

/**
 * Bounds that keep reading fast and small on a 4 GB Galaxy A15 and keep the summary short
 * (a full tree can be ~40k tokens, MAKALE-09). Not V-xx decisions; tune after G-54.
 */
data class ScreenLimits(
    /** Deepest node level the walker descends to. */
    val maxDepth: Int = 40,
    /** Total nodes the walker copies across all windows. */
    val maxNodes: Int = 800,
    /** Elements kept in the compressed snapshot. */
    val maxElements: Int = 120,
    /** Longer labels are cut (message bodies, long descriptions). */
    val maxLabelChars: Int = 100,
    /** Child texts merged into an unlabeled clickable row. */
    val maxMergedParts: Int = 3,
)
