package tr.yasliasistan.core.context

import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.screen.ScreenSnapshot

/**
 * What "bu / şu" can point at (FG-08): the focused element's label and the active app's name.
 * Read on request, kept only for one decision, never logged (labels may be personal).
 */
class ScreenFocus(val elementLabel: String?, val appLabel: String?) {
    override fun toString() = "ScreenFocus(element=${elementLabel != null}, app=${appLabel != null})"

    companion object {
        /**
         * Focus from a compressed screen: the first focused, labelled, non-secret element (FG-78:
         * secret fields have no label) and the active window's app as named in the launcher list.
         */
        fun of(snapshot: ScreenSnapshot, apps: List<InstalledApp>): ScreenFocus {
            val element = snapshot.elements.firstOrNull { it.focused && !it.secret && !it.label.isNullOrBlank() }
            val pkg = snapshot.windows.firstOrNull { it.active }?.packageName
            val app = pkg?.let { p -> apps.firstOrNull { it.packageName == p }?.label }
            return ScreenFocus(element?.label, app)
        }
    }
}

/** Supplies the current [ScreenFocus]; the app reads the screen only while a task is open (NFG-53). */
fun interface ScreenFocusSource {
    fun current(): ScreenFocus?

    companion object {
        val NONE = ScreenFocusSource { null }
    }
}
