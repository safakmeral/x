package tr.yasliasistan.app.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.RawNode
import tr.yasliasistan.core.screen.ScreenLimits
import tr.yasliasistan.core.screen.SecretField

/**
 * Copies an accessibility node tree into Android-free [RawNode]s (PLAN G-19), bounded by
 * [ScreenLimits.maxDepth] and a shared node budget. Every child node is recycled after copying.
 *
 * Privacy (FG-78, NFG-52): a node's secret flags are read first; for a password or sensitive
 * field getText/getHintText/getContentDescription are never called. Invisible subtrees are not
 * descended into. Nothing here logs content.
 */
internal class NodeTreeCopier(private val limits: ScreenLimits) {

    var copied = 0
        private set
    var truncated = false
        private set

    private val rect = Rect()

    val budgetLeft: Boolean get() = copied < limits.maxNodes

    fun copy(node: AccessibilityNodeInfo, depth: Int = 0): RawNode {
        copied++
        val isPassword = node.isPassword
        val inputType = node.inputType
        val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && node.isAccessibilityDataSensitive
        val secret = SecretField.isSecret(isPassword, inputType, sensitive)
        val visible = node.isVisibleToUser
        val readText = visible && !secret
        node.getBoundsInScreen(rect)
        return RawNode(
            className = node.className?.toString(),
            // While a field shows its placeholder, getText returns the hint: keep it only as the hint.
            text = if (readText && !node.isShowingHintText) node.text?.toString() else null,
            description = if (readText) node.contentDescription?.toString() else null,
            hint = if (readText) node.hintText?.toString() else null,
            isPassword = isPassword,
            inputType = inputType,
            dataSensitive = sensitive,
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            checkable = node.isCheckable,
            checked = node.isChecked,
            enabled = node.isEnabled,
            selected = node.isSelected,
            focused = node.isFocused,
            visible = visible,
            heading = node.isHeading,
            collectionItem = node.collectionItemInfo != null,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            children = if (visible) children(node, depth) else emptyList(),
        )
    }

    private fun children(node: AccessibilityNodeInfo, depth: Int): List<RawNode> {
        val count = node.childCount
        if (count == 0) return emptyList()
        if (depth >= limits.maxDepth) {
            truncated = true
            return emptyList()
        }
        val out = ArrayList<RawNode>(count)
        for (i in 0 until count) {
            if (!budgetLeft) {
                truncated = true
                break
            }
            val child = node.getChild(i) ?: continue
            try {
                out += copy(child, depth + 1)
            } finally {
                recycleNode(child)
            }
        }
        return out
    }
}

/** recycle() is a no-op from API 33 but still frees pooled nodes on API 29-32. */
@Suppress("DEPRECATION")
internal fun recycleNode(node: AccessibilityNodeInfo) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) node.recycle()
}
