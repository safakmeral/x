package tr.yasliasistan.app.metrics

import android.content.Context
import android.os.Process
import tr.yasliasistan.app.BuildConfig
import java.util.concurrent.Executors

/** Process-wide metric store (G-18). The database opens lazily on the metrics thread, never on the main thread. */
object AppMetrics {
    @Volatile
    private var instance: MetricsStore? = null

    val store: MetricsStore
        get() = instance ?: error("AppMetrics.init was not called")

    /** Called once from Application.onCreate; also deletes expired records. */
    fun init(context: Context) {
        if (instance != null) return
        val app = context.applicationContext
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "metrics")
        }
        val database = lazy { MetricsDatabase.open(app) }
        instance = MetricsStore(dao = { database.value.metrics() }, appVersion = BuildConfig.VERSION_CODE, executor = executor)
            .also { it.pruneNow() }
    }
}
