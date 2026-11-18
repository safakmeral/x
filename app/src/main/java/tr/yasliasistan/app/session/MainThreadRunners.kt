package tr.yasliasistan.app.session

import android.os.Handler
import android.os.Looper
import tr.yasliasistan.core.dialog.Scheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Dialog timers on the main thread (confirmation timeout, "Bakıyorum…"). */
class HandlerScheduler(private val handler: Handler = Handler(Looper.getMainLooper())) : Scheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): Scheduler.Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return Scheduler.Cancellable { handler.removeCallbacks(runnable) }
    }
}

/**
 * One worker thread for decision work (app list query, rules), so the main thread never waits.
 * A single thread keeps order: the app-list prefetch finishes before the first decision uses it.
 */
class WorkerThread : Background {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "assistant-decide") }
    private val main = Handler(Looper.getMainLooper())

    override fun <T> run(work: () -> T, deliver: (T) -> Unit) {
        executor.execute {
            val result = work()
            main.post { deliver(result) }
        }
    }

    fun execute(task: () -> Unit) = executor.execute(task)

    fun shutdown() = executor.shutdownNow()
}
