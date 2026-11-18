package tr.yasliasistan.core.dialog

/** Delayed execution; the app backs it with a main-thread Handler, tests with virtual time. */
fun interface Scheduler {
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable

    fun interface Cancellable {
        fun cancel()
    }
}
