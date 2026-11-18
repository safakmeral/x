package tr.yasliasistan.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import tr.yasliasistan.app.overlay.TouchIndicator
import tr.yasliasistan.app.session.launchApp
import tr.yasliasistan.core.action.ActionPerformer
import tr.yasliasistan.core.action.Point
import tr.yasliasistan.core.action.ScrollDirection
import tr.yasliasistan.core.action.ScrollGeometry
import tr.yasliasistan.core.action.ScrollLength
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ScreenElement

/**
 * Thin Android layer of the executor (PLAN G-20). Prefers the element's own accessibility action
 * (exact, unaffected by overlays); a gesture on the element's live, on-screen bounds is the
 * fallback (V-10: coordinates only as a backup, always derived from a checked element).
 * Typing uses ACTION_SET_TEXT only, never into a secret field (FG-78). Worker thread only.
 * G-21: every tap shows a dot and every scroll an arrow at the same place ([indicator], FG-21),
 * whichever way the action is then done.
 */
internal class AndroidActionPerformer(
    private val service: AccessibilityService,
    private val indicator: TouchIndicator = TouchIndicator.NONE,
    private val finder: NodeFinder = NodeFinder(service),
    private val gestures: GestureRunner = GestureRunner(service),
) : ActionPerformer {

    override fun tap(target: ScreenElement): Boolean {
        val at = center(target)
        at?.let(indicator::showTap)
        return nodeAction(target, NodeNeed.CLICK, AccessibilityNodeInfo.ACTION_CLICK) || at?.let(gestures::tap) == true
    }

    override fun longPress(target: ScreenElement): Boolean {
        val at = center(target)
        at?.let(indicator::showTap)
        return nodeAction(target, NodeNeed.LONG_CLICK, AccessibilityNodeInfo.ACTION_LONG_CLICK) ||
            at?.let { gestures.press(it, ViewConfiguration.getLongPressTimeout() + LONG_PRESS_EXTRA_MS) } == true
    }

    override fun scroll(target: ScreenElement, direction: ScrollDirection, length: ScrollLength): Boolean {
        val area = ScrollGeometry.visible(target.bounds, screenBounds())
        val swipe = if (area.isEmpty || target.secret) null else ScrollGeometry.swipe(area, direction, length)
        swipe?.let(indicator::showSwipe)
        if (swipe != null && gestures.swipe(swipe)) return true
        // Fallback: one page by the list's own action (length cannot be chosen then).
        val forward = direction == ScrollDirection.DOWN || direction == ScrollDirection.RIGHT
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return nodeAction(target, NodeNeed.SCROLL, action)
    }

    override fun type(target: ScreenElement, text: String): Boolean {
        if (target.secret || !target.editable) return false
        val node = finder.find(target.windowId, target.bounds, NodeNeed.EDIT) ?: return false
        return try {
            // Checked again on the live node: the field may have turned into a password field.
            !isSecret(node) && node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
            )
        } finally {
            recycleNode(node)
        }
    }

    override fun back(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    override fun home(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    override fun openApp(app: InstalledApp): Boolean = service.launchApp(app)

    override fun cancel() = gestures.cancel()

    override fun reset() = gestures.reset()

    private fun nodeAction(target: ScreenElement, need: NodeNeed, action: Int): Boolean {
        if (target.secret) return false
        val node = finder.find(target.windowId, target.bounds, need) ?: return false
        return try {
            !isSecret(node) && node.performAction(action)
        } finally {
            recycleNode(node)
        }
    }

    /** Center of the element's on-screen part; null if it is off screen. */
    private fun center(target: ScreenElement): Point? {
        if (target.secret) return null
        val visible = ScrollGeometry.visible(target.bounds, screenBounds())
        return if (visible.isEmpty) null else Point(visible.centerX, visible.centerY)
    }

    private fun screenBounds(): Bounds {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val r = wm.maximumWindowMetrics.bounds
            return Bounds(r.left, r.top, r.right, r.bottom)
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Bounds(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private companion object {
        /** Beyond the system's (user-adjustable) long-press delay, so the press surely counts. */
        const val LONG_PRESS_EXTRA_MS = 300L
    }
}
