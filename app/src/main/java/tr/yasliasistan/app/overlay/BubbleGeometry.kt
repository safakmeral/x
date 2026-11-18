package tr.yasliasistan.app.overlay

import kotlin.math.roundToInt

/** Screen area (pixels) the bubble may occupy: the screen minus system bars and cutouts. */
data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Top-left corner of the bubble window, in screen pixels. */
data class BubblePosition(val x: Int, val y: Int)

enum class BubbleEdge { LEFT, RIGHT }

/**
 * Position remembered across sessions, rotations and screen sizes:
 * which edge the bubble sticks to and how far down it sits (0 = top, 1 = bottom).
 */
data class StoredBubblePosition(val edge: BubbleEdge, val verticalFraction: Float) {
    companion object {
        /** First-run spot: right edge, a bit below the middle, away from the status bar. */
        val DEFAULT = StoredBubblePosition(BubbleEdge.RIGHT, 0.6f)
    }
}

/** Pure placement maths for the floating bubble (no Android types, unit tested). */
object BubbleGeometry {

    /** Keeps a [size]-pixel square bubble fully inside [area] (FG-01: never lost off-screen). */
    fun clamp(position: BubblePosition, size: Int, area: SafeArea): BubblePosition =
        BubblePosition(
            x = clampAxis(position.x, area.left, area.right - size),
            y = clampAxis(position.y, area.top, area.bottom - size),
        )

    /** The edge whose side the bubble's centre is on (ties go right). */
    fun nearestEdge(position: BubblePosition, size: Int, area: SafeArea): BubbleEdge {
        val centreX = position.x + size / 2
        val midX = area.left + area.width / 2
        return if (centreX < midX) BubbleEdge.LEFT else BubbleEdge.RIGHT
    }

    /** Clamps, then sticks the bubble to the nearest side edge, keeping its height. */
    fun snapToEdge(position: BubblePosition, size: Int, area: SafeArea): BubblePosition {
        val clamped = clamp(position, size, area)
        return place(StoredBubblePosition(nearestEdge(clamped, size, area), 0f), size, area)
            .copy(y = clamped.y)
    }

    /** Converts a snapped on-screen position into the screen-independent stored form. */
    fun toStored(position: BubblePosition, size: Int, area: SafeArea): StoredBubblePosition {
        val clamped = clamp(position, size, area)
        val travel = area.height - size
        val fraction = if (travel <= 0) 0f else (clamped.y - area.top).toFloat() / travel
        return StoredBubblePosition(nearestEdge(clamped, size, area), fraction.coerceIn(0f, 1f))
    }

    /** Converts a stored position back to screen pixels for the current [area]. */
    fun place(stored: StoredBubblePosition, size: Int, area: SafeArea): BubblePosition {
        val x = when (stored.edge) {
            BubbleEdge.LEFT -> area.left
            BubbleEdge.RIGHT -> area.right - size
        }
        val travel = (area.height - size).coerceAtLeast(0)
        val y = area.top + (travel * stored.verticalFraction.coerceIn(0f, 1f)).roundToInt()
        return clamp(BubblePosition(x, y), size, area)
    }

    // If the bubble is larger than the area, pin it to the start rather than throwing.
    private fun clampAxis(value: Int, min: Int, max: Int): Int =
        if (max < min) min else value.coerceIn(min, max)
}
