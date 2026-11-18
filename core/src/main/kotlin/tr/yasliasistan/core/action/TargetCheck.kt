package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot
import kotlin.math.abs

/** Result of comparing a planned [ElementRef] with the live screen. */
sealed interface TargetMatch {
    /** The same element is still there (possibly under a new number); act on [element]. */
    data class Same(val element: ScreenElement) : TargetMatch

    /** Something with that name is there but moved, resized, doubled or in another window. */
    data object Changed : TargetMatch

    /** Nothing like the planned element is on the screen any more. */
    data object Gone : TargetMatch
}

/**
 * NFG-38: before every action the target must still be the element the step was planned on:
 * same role, same label, same app and window, bounds within [tolerancePx]. A renumbered element
 * (something appeared above it) is accepted only when exactly one element matches.
 */
class TargetCheck(private val tolerancePx: Int = DEFAULT_TOLERANCE_PX) {

    fun check(ref: ElementRef, live: ScreenSnapshot): TargetMatch {
        live.element(ref.id)?.takeIf { matches(ref, it) }?.let { return TargetMatch.Same(it) }
        val moved = live.elements.filter { matches(ref, it) }
        if (moved.size == 1) return TargetMatch.Same(moved[0])
        if (moved.size > 1) return TargetMatch.Changed
        val similar = live.elements.any {
            it.role == ref.role && it.label == ref.label && it.packageName == ref.packageName
        }
        return if (similar) TargetMatch.Changed else TargetMatch.Gone
    }

    private fun matches(ref: ElementRef, e: ScreenElement): Boolean =
        e.role == ref.role && e.label == ref.label && e.packageName == ref.packageName &&
            e.windowId == ref.windowId && near(ref, e)

    private fun near(ref: ElementRef, e: ScreenElement): Boolean {
        val a = ref.bounds
        val b = e.bounds
        return abs(a.left - b.left) <= tolerancePx && abs(a.top - b.top) <= tolerancePx &&
            abs(a.right - b.right) <= tolerancePx && abs(a.bottom - b.bottom) <= tolerancePx
    }

    companion object {
        /** A few pixels absorb rounding and ripple animations, not a scrolled list. */
        const val DEFAULT_TOLERANCE_PX: Int = 6
    }
}
