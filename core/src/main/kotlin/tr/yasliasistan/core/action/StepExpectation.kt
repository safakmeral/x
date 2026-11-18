package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.WindowKind

/**
 * An element that must be on the screen after a step (G-22). [label] may be personal data:
 * memory only, hidden from toString.
 */
data class ElementExpect(val label: String, val role: ElementRole? = null) {
    override fun toString() = "ElementExpect(role=$role)"
}

/**
 * What the screen must look like after a step (PLAN G-22, FG-24). Every part is optional:
 * - [packageName]: the app in front;
 * - [element]: an element that must be visible;
 * - [screenChanges]: the step must make the screen react (a tap that opens a page).
 */
data class StepExpectation(
    val packageName: String? = null,
    val element: ElementExpect? = null,
    val screenChanges: Boolean = false,
) {
    companion object {
        /**
         * Default when a step names nothing: opening an app must bring that app to the front, "ana
         * ekran" may land anywhere, every other action must stay in the app it started in.
         */
        fun derive(action: ScreenAction, before: ScreenSnapshot): StepExpectation = when (action) {
            is ScreenAction.OpenApp -> StepExpectation(packageName = action.app.packageName)
            ScreenAction.Home -> StepExpectation()
            else -> StepExpectation(packageName = before.frontPackage())
        }
    }
}

/** Result of comparing the screen after a step with its [StepExpectation]. */
enum class StepVerdict {
    /** The screen is as expected. */
    MATCH,

    /** Nothing (or not enough) happened: still the page the step started on. */
    NO_REACTION,

    /** The step led somewhere else (another app or page): undo it (FG-25). */
    UNEXPECTED_PAGE,
}

/** Compares the screen after a step with what the step expected (FG-24). Pure. */
object StepVerifier {

    fun verify(expect: StepExpectation, before: ScreenSnapshot, after: ScreenSnapshot, changed: Boolean): StepVerdict {
        val front = after.frontPackage()
        if (expect.packageName != null && front != expect.packageName) {
            return if (front == before.frontPackage()) StepVerdict.NO_REACTION else StepVerdict.UNEXPECTED_PAGE
        }
        expect.element?.let { wanted ->
            val present = after.elements.any { e ->
                e.label == wanted.label && (wanted.role == null || e.role == wanted.role)
            }
            if (!present) return if (changed) StepVerdict.UNEXPECTED_PAGE else StepVerdict.NO_REACTION
        }
        if (expect.screenChanges && !changed) return StepVerdict.NO_REACTION
        return StepVerdict.MATCH
    }
}

/** Package of the app window in front (the active one, else the first app window). */
fun ScreenSnapshot.frontPackage(): String? {
    val apps = windows.filter { it.kind == WindowKind.APPLICATION }
    return (apps.firstOrNull { it.active } ?: apps.firstOrNull())?.packageName
}
