package tr.yasliasistan.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import tr.yasliasistan.app.BuildConfig
import tr.yasliasistan.app.overlay.TouchIndicator
import tr.yasliasistan.core.action.ActionExecutor
import tr.yasliasistan.core.action.ExecutionCode
import tr.yasliasistan.core.action.ExecutionResult
import tr.yasliasistan.core.action.Narrator
import tr.yasliasistan.core.action.PackageGate
import tr.yasliasistan.core.action.Pause
import tr.yasliasistan.core.action.ScreenSource
import tr.yasliasistan.core.action.SequenceResult
import tr.yasliasistan.core.action.StepPlanner
import tr.yasliasistan.core.action.StepTexts
import tr.yasliasistan.core.action.TaskOutcome
import tr.yasliasistan.core.action.TaskRunner
import tr.yasliasistan.core.action.TaskStep
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.metrics.ScreenMetrics
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs "Sen yap" steps on one worker thread (PLAN G-20). The same thread is the only caller of
 * the [ScreenReader] (its node queries are blocking IPC) and of the gestures, which wait for their
 * callbacks on the main thread. Results come back on the main thread.
 *
 * [stop] ("Dur", FG-23) is safe from the main thread: the queue empties, a running swipe ends
 * after its current piece and no later step starts. Only codes and counts are logged.
 *
 * G-21/G-22/G-23: [runTask] runs verified, narrated steps ([TaskRunner]); touches show a dot or an
 * arrow through [indicator]; [narrator] must return at once (it is called on the worker thread).
 */
class ScreenActionRunner internal constructor(
    service: AccessibilityService,
    reader: ScreenReader,
    gate: PackageGate,
    metrics: ScreenMetrics,
    texts: StepTexts,
    narrator: Narrator,
    indicator: TouchIndicator = TouchIndicator.NONE,
) {
    private val thread: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "assistant-act") }
    private val main = Handler(Looper.getMainLooper())

    val executor = ActionExecutor(
        screen = ScreenSource(reader::snapshot),
        performer = AndroidActionPerformer(service, indicator),
        gate = gate,
        pause = Pause(::sleep),
        clock = SystemClock::elapsedRealtime,
        metrics = metrics,
    )

    private val tasks = TaskRunner(
        executor = executor,
        screen = ScreenSource(reader::snapshot),
        texts = texts,
        clock = SystemClock::elapsedRealtime,
        narrator = narrator,
        metrics = metrics,
    )

    /**
     * Debug builds only: a fixed sequence started from the debug screen opens its own short task, so
     * the screen may be read without a spoken command (G-20 device check). Always false in release.
     */
    @Volatile
    var debugTaskOpen: Boolean = false
        private set

    /** Runs [steps] as a new task; [onDone] gets the result on the main thread. */
    fun run(steps: List<StepPlanner>, onDone: (SequenceResult) -> Unit) = start(steps, debug = false, onDone)

    /** G-20 device check from the debug screen; ignored in release builds. */
    fun runDebug(steps: List<StepPlanner>, onDone: (SequenceResult) -> Unit) {
        if (BuildConfig.DEBUG) start(steps, debug = true, onDone)
    }

    private fun start(steps: List<StepPlanner>, debug: Boolean, onDone: (SequenceResult) -> Unit) = launch(
        debug,
        work = { executor.runSequence(steps) },
        crashed = { SequenceResult(ExecutionResult.of(ExecutionCode.FAILED), 0) },
        describe = { "sequence ${it.last.code} refusal=${it.last.refusal} steps=${it.stepsDone}" },
        onDone = onDone,
    )

    /**
     * G-22/G-23: runs [steps] as a new task, each verified and narrated; [onDone] gets the outcome
     * on the main thread (its message is the one sentence to say then).
     */
    fun runTask(steps: List<TaskStep>, onDone: (TaskOutcome) -> Unit) = startTask(steps, debug = false, onDone)

    /** G-22 device check from the debug screen; ignored in release builds. */
    fun runTaskDebug(steps: List<TaskStep>, onDone: (TaskOutcome) -> Unit) {
        if (BuildConfig.DEBUG) startTask(steps, debug = true, onDone)
    }

    /** "Baştan anlat" (FG-12): the finished steps are said again from the first one. */
    fun retell() = tasks.retell()

    private fun startTask(steps: List<TaskStep>, debug: Boolean, onDone: (TaskOutcome) -> Unit) = launch(
        debug,
        work = { tasks.run(steps) },
        crashed = { TaskOutcome.Stopped(0) },
        describe = { "task $it" }, // content-free toString
        onDone = onDone,
    )

    private fun <T> launch(debug: Boolean, work: () -> T, crashed: () -> T, describe: (T) -> String, onDone: (T) -> Unit) {
        executor.newTask()
        thread.execute {
            if (debug) debugTaskOpen = true
            val result = try {
                work()
            } catch (e: RuntimeException) {
                SafeLog.event(TAG, "run crashed: ${e.javaClass.simpleName}", SafeLog.Level.ERROR)
                crashed()
            } finally {
                debugTaskOpen = false
            }
            SafeLog.event(TAG, describe(result))
            main.post { onDone(result) }
        }
    }

    fun stop() = executor.stop()

    fun shutdown() {
        executor.stop()
        thread.shutdownNow()
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            executor.stop() // service shutting down
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val TAG = "ScreenActions"
    }
}

/** The running service's action runner (null while the service is off); used by the debug screen. */
object ScreenActions {
    @Volatile
    var runner: ScreenActionRunner? = null
        internal set

    /** Screen reader of the running service, for the "Ben yapayım" guide (reads only while a task or guide runs). */
    @Volatile
    var reader: ScreenReader? = null
        internal set

    /** "Ben yapayım" ring overlay of the running service (null while the service is off). */
    @Volatile
    var guide: tr.yasliasistan.app.overlay.TouchTraceOverlay? = null
        internal set
}

/**
 * Interim app gate until G-24 brings the V-14 allowlist and the bank block: only the system
 * Settings app. Deny by default, so nothing else can be touched before G-24.
 */
internal val InterimPackageGate = PackageGate { it in DEMO_ALLOWED_PACKAGES }

/**
 * Demo allowlist (18.11.2026, minimal G-24 / V-14): only these apps may be touched in "Sen yap".
 * Everything else, banking apps included, is refused by default (FG-60, deny by default).
 */
internal val DEMO_ALLOWED_PACKAGES = setOf(
    "com.android.settings",
    "com.whatsapp",
    "com.whatsapp.w4b",
    "com.sec.android.gallery3d",
    "com.google.android.apps.photos",
    "com.sec.android.app.camera",
    "com.samsung.android.dialer",
    "com.google.android.dialer",
    "com.samsung.android.messaging",
    "com.google.android.apps.messaging",
)
