package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.Bounds
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ring drawn where the assistant touches (G-21, FG-21; V-05: yellow ring with a dark edge). */
data class TapMark(val center: Point, val radius: Int)

/**
 * Arrow drawn along the finger path of a scroll (G-21, FG-21). The head sits at [to], where the
 * finger lifts; [headLeft] and [headRight] are the ends of the two head strokes.
 */
data class SwipeMark(val from: Point, val to: Point, val headLeft: Point, val headRight: Point)

/**
 * Pure geometry of the touch trace overlay (PLAN G-21). The overlay window is only as big as
 * the mark ([box]), so it covers as little of the app as possible; it never takes touches.
 */
object TouchTrace {

    /** Angle between the shaft and each head stroke. */
    private const val HEAD_ANGLE_RAD = Math.PI / 6

    fun tap(at: Point, radius: Int): TapMark {
        require(radius > 0)
        return TapMark(at, radius)
    }

    /**
     * Arrow for [swipe] with head strokes of [headLength] (shortened for very short swipes).
     * Null when the finger does not move: there is nothing to show then.
     */
    fun swipe(swipe: Swipe, headLength: Int): SwipeMark? {
        require(headLength > 0)
        val dx = (swipe.end.x - swipe.start.x).toDouble()
        val dy = (swipe.end.y - swipe.start.y).toDouble()
        val length = hypot(dx, dy)
        if (length < 1.0) return null
        val head = min(headLength.toDouble(), length / 2)
        // Unit vector pointing back from the tip towards the start.
        val bx = -dx / length
        val by = -dy / length
        fun stroke(angle: Double) = Point(
            swipe.end.x + (head * (bx * cos(angle) - by * sin(angle))).roundToInt(),
            swipe.end.y + (head * (bx * sin(angle) + by * cos(angle))).roundToInt(),
        )
        return SwipeMark(swipe.start, swipe.end, stroke(HEAD_ANGLE_RAD), stroke(-HEAD_ANGLE_RAD))
    }

    /** Screen area the ring needs, with [pad] for the stroke width. */
    fun box(mark: TapMark, pad: Int): Bounds = Bounds(
        mark.center.x - mark.radius - pad,
        mark.center.y - mark.radius - pad,
        mark.center.x + mark.radius + pad,
        mark.center.y + mark.radius + pad,
    )

    /** Screen area the arrow needs, with [pad] for the stroke width. */
    fun box(mark: SwipeMark, pad: Int): Bounds {
        val xs = listOf(mark.from.x, mark.to.x, mark.headLeft.x, mark.headRight.x)
        val ys = listOf(mark.from.y, mark.to.y, mark.headLeft.y, mark.headRight.y)
        return Bounds(xs.min() - pad, ys.min() - pad, xs.max() + pad, ys.max() + pad)
    }

    /** Where the arrow's growing tip is at [fraction] (0..1) of the animation. */
    fun along(mark: SwipeMark, fraction: Float): Point {
        val f = max(0f, min(1f, fraction))
        return Point(
            mark.from.x + ((mark.to.x - mark.from.x) * f).roundToInt(),
            mark.from.y + ((mark.to.y - mark.from.y) * f).roundToInt(),
        )
    }

    /** [p] in the coordinates of a window whose top-left corner is at [origin]. */
    fun local(p: Point, origin: Point): Point = Point(p.x - origin.x, p.y - origin.y)
}
