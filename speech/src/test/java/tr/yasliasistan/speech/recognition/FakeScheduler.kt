package tr.yasliasistan.speech.recognition

/** Virtual clock: [advanceBy] runs every task whose time has come, in order. */
class FakeScheduler : TurnScheduler {
    private data class Task(val at: Long, val seq: Int, val action: () -> Unit, var cancelled: Boolean = false)

    private val tasks = mutableListOf<Task>()
    private var seq = 0
    var now = 0L
        private set

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val task = Task(now + delayMs, seq++, action)
        tasks += task
        return Cancellable { task.cancelled = true }
    }

    fun advanceBy(ms: Long) {
        val target = now + ms
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= target }.minWithOrNull(compareBy({ it.at }, { it.seq }))
                ?: break
            tasks.remove(next)
            now = next.at
            next.action()
        }
        now = target
    }

    val pendingCount: Int get() = tasks.count { !it.cancelled }
}
