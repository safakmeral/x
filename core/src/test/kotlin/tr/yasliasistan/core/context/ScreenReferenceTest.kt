package tr.yasliasistan.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.clarify.Apps
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.LabelSource
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.ScreenWindow
import tr.yasliasistan.core.screen.TextKind
import tr.yasliasistan.core.screen.WindowKind

/** G-39 / FG-08: "bu / şu" resolved with the focused element and the active app. */
class ScreenReferenceTest {

    private val h = ContextHarness()

    private fun text(event: DialogEvent) = when (event) {
        is DialogEvent.Understood -> event.readback
        is DialogEvent.Proceeding -> event.announcement
        is DialogEvent.Replied -> event.text
        else -> error("unexpected $event")
    }

    @Test
    fun focusedContactRowIsTheOneToCall() {
        h.focus = ScreenFocus(elementLabel = "Mehmet Test", appLabel = "Kişiler")
        assertEquals("CALL?Mehmet Test", text(h.say("bunu ara").second.event))
        // Layer 1 names the reference, layer 2 what is seen on the screen (NFG-09).
        assertEquals("SCREEN:Mehmet Test", text(h.say("neden").second.event))
        assertEquals("SEEN:Mehmet Test CONFIRM_NOTE", text(h.say("neden").second.event))
    }

    @Test
    fun thisAppIsTheActiveApp() {
        h.focus = ScreenFocus(elementLabel = null, appLabel = "WhatsApp")
        assertEquals(DialogEvent.Proceeding(1, "OPENING:WhatsApp"), h.say("bu uygulamayı aç").second.event)
    }

    @Test
    fun noFocusLeavesTheCommandAsItIs() {
        assertEquals("WHOM_CALL?", text(h.say("şunu ara").second.event))
    }

    @Test
    fun rewriteThatNoRuleUnderstandsSendsTheOriginalWordsOn() {
        h.focus = ScreenFocus(elementLabel = "Gönder", appLabel = "Mesajlar")
        h.say("şuna bas")
        assertEquals(listOf("şuna bas"), h.modelInputs) // the label never reaches the next layers
    }

    @Test
    fun focusFromTheCompressedScreen() {
        val snapshot = ScreenSnapshot(
            windows = listOf(
                ScreenWindow(1, WindowKind.APPLICATION, "com.whatsapp", active = true),
                ScreenWindow(2, WindowKind.SYSTEM, "com.android.systemui", active = false),
            ),
            elements = listOf(
                element(1, label = null, focused = true, secret = true), // password field: never a referent
                element(2, label = "Ara", focused = true),
                element(3, label = "Mesaj", focused = false),
            ),
            truncated = false,
        )
        val focus = ScreenFocus.of(snapshot, listOf(Apps.settings, Apps.whatsapp))
        assertEquals("Ara", focus.elementLabel)
        assertEquals("WhatsApp", focus.appLabel)
        assertTrue(!focus.toString().contains("Ara"))
        assertNull(ScreenFocus.of(ScreenSnapshot.EMPTY, emptyList()).elementLabel)
    }

    private fun element(id: Int, label: String?, focused: Boolean, secret: Boolean = false) = ScreenElement(
        id = id,
        role = if (secret) ElementRole.PASSWORD_FIELD else ElementRole.BUTTON,
        label = label,
        labelSource = if (label == null) LabelSource.NONE else LabelSource.TEXT,
        textKind = if (label == null) TextKind.NONE else TextKind.UI_LABEL,
        clickable = true,
        longClickable = false,
        scrollable = false,
        editable = secret,
        checkable = false,
        checked = false,
        enabled = true,
        selected = false,
        focused = focused,
        heading = false,
        inListItem = false,
        secret = secret,
        unlabeledIcon = false,
        bounds = Bounds(0, 0, 10, 10),
        windowId = 1,
        packageName = "com.whatsapp",
    )
}
