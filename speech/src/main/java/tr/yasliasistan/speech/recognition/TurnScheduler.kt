package tr.yasliasistan.speech.recognition

/** Runs [action] once after [delayMs] on the engine's thread; replaced by a fake clock in tests. */
fun interface TurnScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}
