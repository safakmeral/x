package tr.yasliasistan.core.action

import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.LabelSource
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.ScreenWindow
import tr.yasliasistan.core.screen.TextKind
import tr.yasliasistan.core.screen.WindowKind

/** Made-up screens for executor tests (no real personal data). */
object ActionFixtures {
    const val SETTINGS = "com.android.settings"
    const val BANK = "com.example.bank"

    val settingsApp = InstalledApp("Ayarlar", SETTINGS, "$SETTINGS.Settings")

    fun el(
        id: Int,
        label: String?,
        bounds: Bounds,
        role: ElementRole = ElementRole.BUTTON,
        pkg: String? = SETTINGS,
        window: Int = 1,
        clickable: Boolean = true,
        editable: Boolean = false,
        scrollable: Boolean = false,
        secret: Boolean = false,
        enabled: Boolean = true,
    ) = ScreenElement(
        id = id, role = role, label = label,
        labelSource = if (label == null) LabelSource.NONE else LabelSource.TEXT,
        textKind = TextKind.UI_LABEL,
        clickable = clickable, longClickable = false, scrollable = scrollable, editable = editable,
        checkable = false, checked = false, enabled = enabled, selected = false, focused = false,
        heading = false, inListItem = false, secret = secret, unlabeledIcon = false,
        bounds = bounds, windowId = window, packageName = pkg,
    )

    fun screen(vararg elements: ScreenElement, pkg: String = SETTINGS) = ScreenSnapshot(
        windows = listOf(ScreenWindow(1, WindowKind.APPLICATION, pkg, active = true)),
        elements = elements.toList(),
        truncated = false,
    )

    val list = el(1, null, Bounds(0, 200, 1080, 2000), role = ElementRole.LIST, clickable = false, scrollable = true)
    val display = el(2, "Ekran", Bounds(0, 300, 1080, 420))
    val sound = el(3, "Ses", Bounds(0, 420, 1080, 540))
    val search = el(4, "Ara", Bounds(0, 100, 1080, 200), role = ElementRole.TEXT_FIELD, editable = true)
    val password = el(5, null, Bounds(0, 540, 1080, 660), role = ElementRole.PASSWORD_FIELD, editable = true, secret = true)

    val home = screen(list, display, sound, search, password)
    val displayPage = screen(el(1, "Parlaklık", Bounds(0, 300, 1080, 420)))
}

/** Screen source that returns [screens] in order and then keeps returning the last one. */
class ScriptedScreen(vararg screens: ScreenSnapshot?) : ScreenSource {
    private val queue = ArrayDeque(screens.toList())
    var reads = 0
        private set
    var active = true

    override fun snapshot(): ScreenSnapshot? {
        reads++
        if (!active) return null
        return if (queue.size > 1) queue.removeFirst() else queue.firstOrNull()
    }
}

/** Records every call; [onCall] lets a test press "Dur" in the middle. */
class RecordingPerformer(var result: Boolean = true) : ActionPerformer {
    val calls = ArrayList<String>()
    var cancelled = 0
    var onCall: (() -> Unit)? = null

    private fun call(name: String): Boolean {
        calls += name
        onCall?.invoke()
        return result
    }

    override fun tap(target: ScreenElement) = call("tap:${target.id}")
    override fun longPress(target: ScreenElement) = call("long:${target.id}")
    override fun scroll(target: ScreenElement, direction: ScrollDirection, length: ScrollLength) =
        call("scroll:${target.id}:${direction.wireName}:${length.wireName}")
    override fun type(target: ScreenElement, text: String) = call("type:${target.id}")
    override fun back() = call("back")
    override fun home() = call("home")
    override fun openApp(app: InstalledApp) = call("open:${app.packageName}")
    override fun cancel() {
        cancelled++
    }
}

/** A clock that only moves when the executor sleeps. */
class FakeTime : Pause {
    var now = 0L
    var onSleep: (() -> Unit)? = null

    override fun sleep(millis: Long) {
        now += millis
        onSleep?.invoke()
    }
}
