package tr.yasliasistan.app.overlay

import android.view.View
import java.util.concurrent.ConcurrentHashMap

/**
 * Accessibility window ids of the assistant's own overlays (bubble, "Dur", later dot/trail).
 * NFG-40: the screen reader (G-19) skips these windows so the assistant never observes itself.
 * G-19 may additionally skip every window of type TYPE_ACCESSIBILITY_OVERLAY as a fallback.
 */
object OwnOverlayWindows {

    // View -> window id; views are removed again when their overlay window is removed.
    private val idsByView = ConcurrentHashMap<View, Int>()

    fun contains(windowId: Int): Boolean = idsByView.containsValue(windowId)

    fun snapshot(): Set<Int> = idsByView.values.toSet()

    /** Records [view]'s window id once Android has assigned it (after attach). */
    fun register(view: View) {
        view.post { windowIdOf(view)?.let { idsByView[view] = it } }
    }

    fun unregister(view: View) {
        idsByView.remove(view)
    }

    private fun windowIdOf(view: View): Int? {
        if (!view.isAttachedToWindow) return null
        val info = view.createAccessibilityNodeInfo()
        val id = info.windowId
        @Suppress("DEPRECATION") // recycle() is a no-op from API 33 but still useful on 29-32.
        info.recycle()
        return id.takeIf { it >= 0 }
    }
}
