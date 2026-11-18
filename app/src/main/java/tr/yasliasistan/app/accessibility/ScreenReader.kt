package tr.yasliasistan.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import tr.yasliasistan.app.overlay.OverlayController
import tr.yasliasistan.app.overlay.OwnOverlayWindows
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.OwnWindowFilter
import tr.yasliasistan.core.screen.RawWindow
import tr.yasliasistan.core.screen.ScreenCompressor
import tr.yasliasistan.core.screen.ScreenLimits
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.WindowKind

/**
 * Takes one compressed snapshot of the screen on request (PLAN G-19; NFG-29, NFG-40, NFG-53).
 *
 * - Only while a task runs: [isTaskActive] false -> null, nothing is read (NFG-53). There is no
 *   background observation; the service ignores all accessibility events.
 * - Own windows (bubble, "Dur", typing screen) and the keyboard are skipped before their nodes
 *   are fetched where possible (NFG-40).
 * - The result lives in memory for the running task; only counts and timings are logged.
 *
 * Node queries are blocking IPC: call from the main thread or one worker thread, not both at once.
 */
class ScreenReader(
    private val service: AccessibilityService,
    private val isTaskActive: () -> Boolean,
    private val limits: ScreenLimits = ScreenLimits(),
) {
    private val compressor = ScreenCompressor(limits)

    fun snapshot(): ScreenSnapshot? {
        if (!isTaskActive()) return null
        val started = SystemClock.elapsedRealtime()
        val filter = OwnWindowFilter(service.packageName, OwnOverlayWindows.snapshot(), OverlayController.OVERLAY_TITLE)
        val copier = NodeTreeCopier(limits)
        val raw = ArrayList<RawWindow>()
        val windows = service.windows
        try {
            for (window in windows) {
                if (!copier.budgetLeft) break
                readWindow(window, filter, copier)?.let { raw += it }
            }
        } finally {
            windows.forEach(::recycleWindow)
        }
        val result = compressor.compress(raw, filter).let {
            if (copier.truncated) it.copy(truncated = true) else it
        }
        SafeLog.event(
            TAG,
            "snapshot windows=${result.windows.size} nodes=${copier.copied} elements=${result.elements.size} " +
                "truncated=${result.truncated} ms=${SystemClock.elapsedRealtime() - started}",
        )
        return result
    }

    private fun readWindow(window: AccessibilityWindowInfo, filter: OwnWindowFilter, copier: NodeTreeCopier): RawWindow? {
        val kind = kindOf(window.type)
        val title = window.title?.toString()
        if (filter.skipBeforeRoot(window.id, kind, title)) return null
        val root = window.root ?: return null
        try {
            val packageName = root.packageName?.toString()
            val rect = Rect().also(window::getBoundsInScreen)
            val shell = RawWindow(
                id = window.id,
                kind = kind,
                title = title,
                packageName = packageName,
                active = window.isActive,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            )
            if (filter.skip(shell)) return null // own package: do not copy its nodes at all
            return shell.copy(root = copier.copy(root))
        } finally {
            recycleNode(root)
        }
    }

    private fun kindOf(type: Int): WindowKind = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.APPLICATION
        AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.SYSTEM
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.INPUT_METHOD
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowKind.ACCESSIBILITY_OVERLAY
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> WindowKind.SPLIT_DIVIDER
        TYPE_MAGNIFICATION_OVERLAY -> WindowKind.MAGNIFICATION
        else -> WindowKind.OTHER
    }

    @Suppress("DEPRECATION")
    private fun recycleWindow(window: AccessibilityWindowInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) window.recycle()
    }

    private companion object {
        const val TAG = "ScreenReader"

        /** AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY (API 33); literal so API 29 still compiles cleanly. */
        const val TYPE_MAGNIFICATION_OVERLAY = 6
    }
}
