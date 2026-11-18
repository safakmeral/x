package tr.yasliasistan.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.SecretField

/** What the executor wants to do with the node it looks for. */
internal enum class NodeNeed { CLICK, LONG_CLICK, SCROLL, EDIT }

/**
 * Finds the live node behind a checked [tr.yasliasistan.core.screen.ScreenElement]: same window,
 * same bounds, and able to do [NodeNeed] (PLAN G-20). Reads only flags and bounds, never text.
 * EDIT never matches a password or sensitive field (FG-78).
 */
internal class NodeFinder(private val service: AccessibilityService, private val maxNodes: Int = 800) {

    private val rect = Rect()

    /** The caller must [recycleNode] the returned node. Call from the executor's thread only. */
    fun find(windowId: Int, bounds: Bounds, need: NodeNeed): AccessibilityNodeInfo? {
        val windows = service.windows
        try {
            val root = windows.firstOrNull { it.id == windowId }?.root ?: return null
            return search(root, bounds, need)
        } finally {
            windows.forEach(::recycleWindowInfo)
        }
    }

    private fun search(root: AccessibilityNodeInfo, target: Bounds, need: NodeNeed): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        try {
            while (queue.isNotEmpty() && visited < maxNodes) {
                val node = queue.removeFirst()
                visited++
                node.getBoundsInScreen(rect)
                val here = Bounds(rect.left, rect.top, rect.right, rect.bottom)
                if (here == target && can(node, need)) return node
                if (here.intersects(target)) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                }
                recycleNode(node)
            }
            return null
        } finally {
            queue.forEach(::recycleNode)
        }
    }

    private fun can(node: AccessibilityNodeInfo, need: NodeNeed): Boolean = node.isEnabled && when (need) {
        NodeNeed.CLICK -> node.isClickable
        NodeNeed.LONG_CLICK -> node.isLongClickable
        NodeNeed.SCROLL -> node.isScrollable
        NodeNeed.EDIT -> node.isEditable && !isSecret(node)
    }
}

/** Secret flags only; no text is touched (FG-78, NFG-52). */
internal fun isSecret(node: AccessibilityNodeInfo): Boolean {
    val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && node.isAccessibilityDataSensitive
    return SecretField.isSecret(node.isPassword, node.inputType, sensitive)
}

@Suppress("DEPRECATION")
internal fun recycleWindowInfo(window: AccessibilityWindowInfo) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) window.recycle()
}
