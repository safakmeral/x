package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.Bounds
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A point on the screen in absolute pixels. */
data class Point(val x: Int, val y: Int)

/** A straight finger path from [start] to [end]. G-21 draws its arrow trail from the same values. */
data class Swipe(val start: Point, val end: Point)

/**
 * Gesture geometry derived from an element's live bounds (V-10: coordinates only as a fallback,
 * never from the model). Pure, so G-20 tests and G-21's trail share it.
 */
object ScrollGeometry {

    /** Keeps the finger away from the edges, where system back/notification gestures live. */
    private const val EDGE_MARGIN = 0.1f

    /**
     * Finger path that scrolls [area] by [length] in [direction] (content moves into view from
     * that side, so DOWN means the finger moves up). Centered in [area]; never leaves it.
     */
    fun swipe(area: Bounds, direction: ScrollDirection, length: ScrollLength): Swipe {
        val cx = area.centerX
        val cy = area.centerY
        return if (direction.vertical) {
            val half = travel(area.height, length) / 2
            val (from, to) = if (direction == ScrollDirection.DOWN) cy + half to cy - half else cy - half to cy + half
            Swipe(Point(cx, from), Point(cx, to))
        } else {
            val half = travel(area.width, length) / 2
            val (from, to) = if (direction == ScrollDirection.RIGHT) cx + half to cx - half else cx - half to cx + half
            Swipe(Point(from, cy), Point(to, cy))
        }
    }

    private fun travel(extent: Int, length: ScrollLength): Int {
        val usable = (extent * (1 - 2 * EDGE_MARGIN)).roundToInt()
        return min((extent * length.fraction).roundToInt(), usable).coerceAtLeast(0)
    }

    /**
     * Splits [swipe] into [count] equal pieces, so a "Dur" can stop between pieces (FG-23).
     * Returns count + 1 points from start to end.
     */
    fun points(swipe: Swipe, count: Int): List<Point> {
        require(count >= 1)
        return (0..count).map { i ->
            Point(
                swipe.start.x + ((swipe.end.x - swipe.start.x) * i.toFloat() / count).roundToInt(),
                swipe.start.y + ((swipe.end.y - swipe.start.y) * i.toFloat() / count).roundToInt(),
            )
        }
    }

    /** Part of [a] that is on [screen]; empty when they do not overlap. */
    fun visible(a: Bounds, screen: Bounds): Bounds {
        val r = Bounds(max(a.left, screen.left), max(a.top, screen.top), min(a.right, screen.right), min(a.bottom, screen.bottom))
        return if (r.isEmpty) Bounds.EMPTY else r
    }
}
