package tr.yasliasistan.app.overlay

/** Pure placement maths for the caption box shown next to the bubble (NFG-04, unit tested). */
object CaptionGeometry {

    /**
     * Top-left corner for a [width] x [height] caption next to the bubble at [bubble]:
     * horizontally centred on the bubble, below it if there is room there, otherwise above it,
     * otherwise on the roomier side; always kept inside [area].
     */
    fun place(bubble: BubblePosition, bubbleSize: Int, width: Int, height: Int, area: SafeArea, gap: Int): BubblePosition {
        val x = clampAxis(bubble.x + bubbleSize / 2 - width / 2, area.left, area.right - width)
        val below = bubble.y + bubbleSize + gap
        val above = bubble.y - gap - height
        val y = when {
            below + height <= area.bottom -> below
            above >= area.top -> above
            area.bottom - below >= bubble.y - area.top -> below
            else -> above
        }
        return BubblePosition(x, clampAxis(y, area.top, area.bottom - height))
    }

    private fun clampAxis(value: Int, min: Int, max: Int): Int =
        if (max < min) min else value.coerceIn(min, max)
}
