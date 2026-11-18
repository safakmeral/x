package tr.yasliasistan.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragTrackerTest {

    private val tracker = DragTracker(slopPx = 40f)

    @Test
    fun smallWobbleStaysATap() {
        tracker.down(500f, 500f, BubblePosition(400, 400))
        assertNull(tracker.move(520f, 525f))
        assertTrue(tracker.up())
    }

    @Test
    fun movingPastSlopBecomesDragAndFollowsFinger() {
        tracker.down(500f, 500f, BubblePosition(400, 400))
        assertEquals(BubblePosition(400, 500), tracker.move(500f, 600f))
        assertEquals(BubblePosition(390, 510), tracker.move(490f, 610f))
        assertFalse(tracker.up())
    }

    @Test
    fun dragStaysDragEvenIfFingerReturnsToStart() {
        tracker.down(500f, 500f, BubblePosition(0, 0))
        tracker.move(600f, 500f)
        assertEquals(BubblePosition(0, 0), tracker.move(500f, 500f))
        assertFalse(tracker.up())
    }

    @Test
    fun newGestureResetsDragState() {
        tracker.down(0f, 0f, BubblePosition(0, 0))
        tracker.move(200f, 0f)
        tracker.down(0f, 0f, BubblePosition(0, 0))
        assertTrue(tracker.up())
    }
}
