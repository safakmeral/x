package tr.yasliasistan.core.metrics

/**
 * Metric hooks for screen reading (G-19) and element targeting (DEG-20, NFG-30).
 * Content-free: no label or screen text is passed.
 */
interface ScreenMetrics {
    /** A requested element was not found in the accessibility tree. */
    fun elementNotFound()

    /** G-22: one "Sen yap" step ended; [success] = done and the screen was as expected. */
    fun stepFinished(durationMs: Long, success: Boolean) = Unit

    /** G-22: a step led to an unexpected page (FG-25; recorded as WRONG_TAP). */
    fun wrongStep() = Unit

    companion object {
        val NONE: ScreenMetrics = object : ScreenMetrics {
            override fun elementNotFound() = Unit
        }
    }
}
