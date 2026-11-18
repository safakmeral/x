package tr.yasliasistan.core.dialog

/** Virtual-time scheduler for tests: tasks run only when [advanceBy] passes their due time. */
class FakeScheduler : Scheduler {

    private class Task(val dueMs: Long, val seq: Long, val action: () -> Unit) {
        var cancelled = false
    }

    var nowMs: Long = 0
        private set
    private var seq = 0L
    private val tasks = mutableListOf<Task>()

    val pendingCount: Int get() = tasks.count { !it.cancelled }

    override fun schedule(delayMs: Long, action: () -> Unit): Scheduler.Cancellable {
        val task = Task(nowMs + delayMs, seq++, action)
        tasks += task
        return Scheduler.Cancellable { task.cancelled = true }
    }

    fun advanceBy(ms: Long) {
        val target = nowMs + ms
        while (true) {
            val next = tasks.filter { !it.cancelled && it.dueMs <= target }
                .minWithOrNull(compareBy<Task>({ it.dueMs }, { it.seq })) ?: break
            tasks -= next
            nowMs = next.dueMs
            next.action()
        }
        tasks.removeAll { it.cancelled }
        nowMs = target
    }
}
