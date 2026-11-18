package tr.yasliasistan.app.overlay

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Tells a tap from a drag and computes where the bubble should follow the finger.
 * Pure logic so it can be unit tested; the view feeds it raw screen coordinates.
 *
 * Elderly users' fingers often wobble while tapping, so a move only counts as a drag
 * after it leaves [slopPx] (we pass a generous value, larger than the system default).
 */
class DragTracker(private val slopPx: Float) {

    private var downX = 0f
    private var downY = 0f
    private var startPosition = BubblePosition(0, 0)

    var isDragging: Boolean = false
        private set

    fun down(rawX: Float, rawY: Float, bubbleAt: BubblePosition) {
        downX = rawX
        downY = rawY
        startPosition = bubbleAt
        isDragging = false
    }

    /** Returns the new unclamped bubble position while dragging, or null while still a tap. */
    fun move(rawX: Float, rawY: Float): BubblePosition? {
        val dx = rawX - downX
        val dy = rawY - downY
        if (!isDragging && hypot(dx, dy) > slopPx) isDragging = true
        if (!isDragging) return null
        return BubblePosition(startPosition.x + dx.roundToInt(), startPosition.y + dy.roundToInt())
    }

    /** True when the gesture that just ended was a tap (no drag happened). */
    fun up(): Boolean = !isDragging
}
