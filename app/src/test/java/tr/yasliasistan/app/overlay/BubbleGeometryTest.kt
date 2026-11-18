package tr.yasliasistan.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleGeometryTest {

    // A 1080x2400 phone with a 100px status bar and 150px navigation bar.
    private val area = SafeArea(left = 0, top = 100, right = 1080, bottom = 2250)
    private val size = 200

    @Test
    fun clampKeepsInsidePositionUnchanged() {
        val p = BubblePosition(300, 800)
        assertEquals(p, BubbleGeometry.clamp(p, size, area))
    }

    @Test
    fun clampPullsBubbleBackFromEveryEdge() {
        assertEquals(BubblePosition(0, 100), BubbleGeometry.clamp(BubblePosition(-500, -500), size, area))
        assertEquals(BubblePosition(880, 2050), BubbleGeometry.clamp(BubblePosition(5000, 9000), size, area))
    }

    @Test
    fun clampNeverPutsBubbleUnderStatusOrNavigationBar() {
        assertEquals(100, BubbleGeometry.clamp(BubblePosition(10, 50), size, area).y)
        assertEquals(2050, BubbleGeometry.clamp(BubblePosition(10, 2200), size, area).y)
    }

    @Test
    fun clampHandlesBubbleLargerThanArea() {
        val tiny = SafeArea(0, 0, 100, 100)
        assertEquals(BubblePosition(0, 0), BubbleGeometry.clamp(BubblePosition(50, 50), size, tiny))
    }

    @Test
    fun snapGoesToNearestSideAndKeepsHeight() {
        assertEquals(BubblePosition(0, 700), BubbleGeometry.snapToEdge(BubblePosition(200, 700), size, area))
        assertEquals(BubblePosition(880, 700), BubbleGeometry.snapToEdge(BubblePosition(700, 700), size, area))
    }

    @Test
    fun snapAlsoClampsVertically() {
        assertEquals(BubblePosition(880, 2050), BubbleGeometry.snapToEdge(BubblePosition(900, 3000), size, area))
    }

    @Test
    fun storedPositionRoundTripsOnSameScreen() {
        val snapped = BubblePosition(880, 1075) // halfway down the travel range
        val stored = BubbleGeometry.toStored(snapped, size, area)
        assertEquals(BubbleEdge.RIGHT, stored.edge)
        assertEquals(0.5f, stored.verticalFraction, 0.001f)
        assertEquals(snapped, BubbleGeometry.place(stored, size, area))
    }

    @Test
    fun storedPositionAdaptsToRotatedScreen() {
        val landscape = SafeArea(left = 100, top = 50, right = 2300, bottom = 1030)
        val placed = BubbleGeometry.place(StoredBubblePosition(BubbleEdge.RIGHT, 1f), size, landscape)
        assertEquals(BubblePosition(2100, 830), placed)
    }

    @Test
    fun placeIgnoresOutOfRangeFraction() {
        val placed = BubbleGeometry.place(StoredBubblePosition(BubbleEdge.LEFT, 7f), size, area)
        assertEquals(BubblePosition(0, 2050), placed)
    }
}
