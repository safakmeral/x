package tr.yasliasistan.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.screen.Bounds

/** G-21: geometry of the touch dot and the scroll arrow. */
class TouchTraceTest {

    @Test
    fun ringBoxSurroundsTheTouchPointWithPadding() {
        val mark = TouchTrace.tap(Point(500, 800), radius = 40)
        assertEquals(Bounds(455, 755, 545, 845), TouchTrace.box(mark, pad = 5))
    }

    @Test
    fun upwardFingerPathGetsItsHeadAtTheTop() {
        // Scrolling DOWN moves the finger up: the arrow must point up, where the finger lifts.
        val swipe = ScrollGeometry.swipe(Bounds(0, 200, 1000, 1200), ScrollDirection.DOWN, ScrollLength.MEDIUM)
        val mark = TouchTrace.swipe(swipe, headLength = 50)!!
        assertEquals(swipe.end, mark.to)
        assertTrue(mark.to.y < mark.from.y)
        // Both head strokes go back down from the tip, one to each side.
        assertTrue(mark.headLeft.y > mark.to.y && mark.headRight.y > mark.to.y)
        assertTrue((mark.headLeft.x - mark.to.x) * (mark.headRight.x - mark.to.x) < 0)
        assertEquals(43, mark.headLeft.y - mark.to.y) // 50 * cos(30°)
        assertEquals(25, kotlin.math.abs(mark.headLeft.x - mark.to.x)) // 50 * sin(30°)
    }

    @Test
    fun sidewaysArrowPointsLeftWhenTheFingerMovesLeft() {
        val mark = TouchTrace.swipe(Swipe(Point(800, 500), Point(200, 500)), headLength = 40)!!
        assertTrue(mark.headLeft.x > mark.to.x && mark.headRight.x > mark.to.x)
        assertEquals(mark.to.x + 35, mark.headLeft.x)
    }

    @Test
    fun shortSwipeGetsAShorterHead() {
        val mark = TouchTrace.swipe(Swipe(Point(100, 100), Point(100, 140)), headLength = 50)!!
        assertEquals(123, mark.headLeft.y) // head = half of 40 px: 140 - 20 * cos(30°)
    }

    @Test
    fun fingerThatDoesNotMoveDrawsNoArrow() {
        assertNull(TouchTrace.swipe(Swipe(Point(10, 10), Point(10, 10)), headLength = 30))
    }

    @Test
    fun arrowBoxHoldsShaftAndHead() {
        val mark = TouchTrace.swipe(Swipe(Point(100, 900), Point(100, 300)), headLength = 40)!!
        val box = TouchTrace.box(mark, pad = 6)
        assertEquals(Bounds(100 - 20 - 6, 300 - 6, 100 + 20 + 6, 900 + 6), box)
    }

    @Test
    fun tipMovesAlongThePathAndStaysOnIt() {
        val mark = TouchTrace.swipe(Swipe(Point(0, 1000), Point(0, 0)), headLength = 40)!!
        assertEquals(Point(0, 500), TouchTrace.along(mark, 0.5f))
        assertEquals(Point(0, 0), TouchTrace.along(mark, 2f))
        assertEquals(Point(0, 1000), TouchTrace.along(mark, -1f))
    }

    @Test
    fun screenPointsBecomeWindowPoints() {
        assertEquals(Point(30, 40), TouchTrace.local(Point(130, 540), Point(100, 500)))
    }
}
