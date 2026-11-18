package tr.yasliasistan.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStateTest {

    @Test
    fun idleShowsNoStopAndIdleBubble() {
        assertFalse(OverlayState.IDLE.stopVisible)
        assertEquals(BubbleLook.IDLE, OverlayState.IDLE.bubbleLook)
    }

    @Test
    fun activeSessionAlwaysShowsStop() {
        assertTrue(OverlayState(sessionActive = true).stopVisible)
    }

    @Test
    fun listeningIsVisibleAndShowsStopEvenWithoutSessionFlag() {
        val s = OverlayState(sessionActive = false, listening = true)
        assertTrue(s.stopVisible)
        assertEquals(BubbleLook.LISTENING, s.bubbleLook)
    }
}
