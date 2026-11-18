package tr.yasliasistan.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionGeometryTest {

    private val area = SafeArea(0, 100, 1000, 2000)
    private val bubble = 200
    private val gap = 10

    private fun place(x: Int, y: Int, w: Int = 600, h: Int = 300) =
        CaptionGeometry.place(BubblePosition(x, y), bubble, w, h, area, gap)

    @Test
    fun belowTheBubbleWhenThereIsRoom() {
        assertEquals(BubblePosition(400, 710), place(x = 800, y = 500))
    }

    @Test
    fun aboveTheBubbleNearTheBottom() {
        assertEquals(1800 - 10 - 300, place(x = 800, y = 1800).y)
    }

    @Test
    fun keptInsideHorizontally() {
        assertEquals(0, place(x = 0, y = 500).x)
        assertEquals(400, place(x = 800, y = 500).x)
    }

    @Test
    fun tallCaptionThatFitsNowhereStaysInsideTheArea() {
        val pos = place(x = 0, y = 900, h = 1500)
        assertEquals(true, pos.y >= area.top && pos.y + 1500 <= area.bottom)
    }

    @Test
    fun captionWiderThanAreaPinsToLeft() {
        assertEquals(0, place(x = 400, y = 500, w = 1200).x)
    }
}
