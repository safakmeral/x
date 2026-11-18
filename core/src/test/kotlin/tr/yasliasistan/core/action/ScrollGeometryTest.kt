package tr.yasliasistan.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.screen.Bounds

class ScrollGeometryTest {

    private val area = Bounds(0, 200, 1000, 2200) // 1000 x 2000

    @Test
    fun scrollingDownMovesTheFingerUp() {
        val s = ScrollGeometry.swipe(area, ScrollDirection.DOWN, ScrollLength.MEDIUM)
        assertEquals(Point(500, 1700), s.start)
        assertEquals(Point(500, 700), s.end)
    }

    @Test
    fun scrollingUpMovesTheFingerDown() {
        val s = ScrollGeometry.swipe(area, ScrollDirection.UP, ScrollLength.SHORT)
        assertEquals(Point(500, 900), s.start)
        assertEquals(Point(500, 1500), s.end)
    }

    @Test
    fun horizontalDirections() {
        val right = ScrollGeometry.swipe(area, ScrollDirection.RIGHT, ScrollLength.MEDIUM)
        assertEquals(Point(750, 1200), right.start)
        assertEquals(Point(250, 1200), right.end)
        val left = ScrollGeometry.swipe(area, ScrollDirection.LEFT, ScrollLength.MEDIUM)
        assertEquals(Point(250, 1200), left.start)
    }

    @Test
    fun longSwipeStaysAwayFromTheEdges() {
        for (direction in ScrollDirection.entries) {
            val s = ScrollGeometry.swipe(area, direction, ScrollLength.LONG)
            for (p in listOf(s.start, s.end)) {
                assertTrue(p.y >= area.top + 200 && p.y <= area.bottom - 200)
                assertTrue(p.x >= area.left + 100 && p.x <= area.right - 100)
            }
        }
    }

    @Test
    fun lengthsGrow() {
        val travel = ScrollLength.entries.map {
            val s = ScrollGeometry.swipe(area, ScrollDirection.DOWN, it)
            s.start.y - s.end.y
        }
        assertEquals(travel.sorted(), travel)
        assertEquals(travel.size, travel.toSet().size)
    }

    @Test
    fun pointsSplitTheSwipeEvenly() {
        val pts = ScrollGeometry.points(Swipe(Point(0, 1000), Point(0, 0)), 4)
        assertEquals(listOf(1000, 750, 500, 250, 0), pts.map { it.y })
    }

    @Test
    fun visiblePartIsClippedToTheScreen() {
        val screen = Bounds(0, 0, 1080, 2340)
        assertEquals(Bounds(0, 2000, 1080, 2340), ScrollGeometry.visible(Bounds(0, 2000, 1080, 3000), screen))
        assertEquals(Bounds.EMPTY, ScrollGeometry.visible(Bounds(0, 2400, 1080, 3000), screen))
    }
}
