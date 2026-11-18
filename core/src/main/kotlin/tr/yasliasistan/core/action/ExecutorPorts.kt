package tr.yasliasistan.core.action

import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot

/** Fresh screen reads for the executor; null when no task is open (NFG-53). Blocking. */
fun interface ScreenSource {
    fun snapshot(): ScreenSnapshot?
}

/**
 * Thin Android layer (accessibility actions and gestures, `:app`). Each call blocks until the
 * system accepted or finished the action and returns false if it did not. The executor has
 * already checked the element; implementations still refuse secret fields themselves.
 */
interface ActionPerformer {
    fun tap(target: ScreenElement): Boolean
    fun longPress(target: ScreenElement): Boolean
    fun scroll(target: ScreenElement, direction: ScrollDirection, length: ScrollLength): Boolean
    fun type(target: ScreenElement, text: String): Boolean
    fun back(): Boolean
    fun home(): Boolean
    fun openApp(app: InstalledApp): Boolean

    /** "Dur": stop a gesture in progress as soon as possible (FG-23). Any thread. */
    fun cancel()

    /** A new task starts after a "Dur": gestures are allowed again. */
    fun reset() = Unit
}

/**
 * Which apps the assistant may act in (FG-60). G-24 supplies the allowlist and the bank block;
 * a null package (unknown window) is never allowed.
 */
fun interface PackageGate {
    fun allows(packageName: String): Boolean
}

/** Sleeping, replaceable in tests. */
fun interface Pause {
    fun sleep(millis: Long)
}

/** Timing of the executor. Not V-xx decisions; tune after the A15 measurement (G-54). */
data class ExecutorTiming(
    /** Between two screen reads while waiting for the screen to react. */
    val pollMs: Long = 60,
    /** Longest wait for a reaction; then the next step checks the screen anyway. */
    val settleMaxMs: Long = 1_500,
    /** Slices of a Wait action, so "Dur" ends it quickly. */
    val waitSliceMs: Long = 100,
    /** A screen read at most this long ago counts as live; older ones are read again before acting. */
    val freshMs: Long = 100,
)
