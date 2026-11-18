package tr.yasliasistan.core.screen

/** Screen rectangle in absolute pixels (same convention as android.graphics.Rect). */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun intersects(other: Bounds): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/** Window types the reader distinguishes (mirrors AccessibilityWindowInfo.TYPE_*). */
enum class WindowKind { APPLICATION, SYSTEM, INPUT_METHOD, ACCESSIBILITY_OVERLAY, SPLIT_DIVIDER, MAGNIFICATION, OTHER }

/**
 * Android-free copy of one accessibility node, built by the app's thin tree walker (PLAN G-19).
 *
 * Privacy: for a secret field ([isSecret]) the walker never calls getText/getHintText/
 * getContentDescription, so [text], [hint] and [description] stay null. The compressor ignores
 * them anyway, so a buggy walker still cannot leak a password into the model (FG-78, NFG-52).
 * Lives in memory only for the running task; never written to disk or logs.
 */
data class RawNode(
    val className: String? = null,
    val text: String? = null,
    val description: String? = null,
    val hint: String? = null,
    val isPassword: Boolean = false,
    val inputType: Int = 0,
    /** API 34+ isAccessibilityDataSensitive; false on older systems. */
    val dataSensitive: Boolean = false,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val focused: Boolean = false,
    val visible: Boolean = true,
    val heading: Boolean = false,
    /** The node is an item of a list/grid (collectionItemInfo != null). */
    val collectionItem: Boolean = false,
    val bounds: Bounds = Bounds.EMPTY,
    val children: List<RawNode> = emptyList(),
) {
    val isSecret: Boolean get() = SecretField.isSecret(isPassword, inputType, dataSensitive)
}

/** One accessibility window with its (possibly truncated) node tree. */
data class RawWindow(
    val id: Int,
    val kind: WindowKind,
    val title: String? = null,
    /** Package of the window's root node. */
    val packageName: String? = null,
    val active: Boolean = false,
    val bounds: Bounds = Bounds.EMPTY,
    val root: RawNode? = null,
)
