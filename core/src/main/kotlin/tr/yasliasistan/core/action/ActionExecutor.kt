package tr.yasliasistan.core.action

import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot

/** Plans one step from the current screen; null = the needed element is not there. */
fun interface StepPlanner {
    fun plan(screen: ScreenSnapshot): ScreenAction?

    companion object {
        fun fixed(action: ScreenAction) = StepPlanner { action }
    }
}

/**
 * Runs [ScreenAction]s safely in "Sen yap" mode (PLAN G-20; FG-20, FG-23, NFG-38, TK-14).
 *
 * Before every screen action a fresh snapshot is read (never a stale one) and:
 * - the planned element must still be the same element (role, label, app, window, bounds; NFG-38);
 * - its app must pass the [gate] (FG-60) and, when given, be the expected app;
 * - password/sensitive fields are refused for every action (FG-78), typing only into editable fields;
 * - without an open task nothing is read or done (NFG-53).
 * After an action the screen is polled briefly until it reacts and settles (capped), so there is
 * no fixed delay between steps. [stop] ("Dur") empties the queue, cancels a running gesture and no
 * later step starts (FG-23).
 *
 * Blocking: call [execute] / [runSequence] from one worker thread; [stop] from any thread.
 * Risk confirmation (G-25/G-26) happens before a step reaches the executor.
 */
class ActionExecutor(
    private val screen: ScreenSource,
    private val performer: ActionPerformer,
    private val gate: PackageGate,
    private val pause: Pause,
    private val clock: () -> Long,
    private val metrics: ScreenMetrics = ScreenMetrics.NONE,
    private val timing: ExecutorTiming = ExecutorTiming(),
    private val targetCheck: TargetCheck = TargetCheck(),
) {
    private val lock = Any()
    private val queue = ArrayDeque<StepPlanner>()

    @Volatile
    var stopped: Boolean = false
        private set

    /** Steps still waiting in the running sequence. */
    val pendingSteps: Int get() = synchronized(lock) { queue.size }

    /** A new task starts: forget an earlier "Dur". */
    fun newTask() {
        synchronized(lock) { queue.clear() }
        stopped = false
        performer.reset()
    }

    /** "Dur" (FG-23): nothing more starts and a running gesture is cancelled. */
    fun stop() {
        stopped = true
        synchronized(lock) { queue.clear() }
        performer.cancel()
    }

    /**
     * Runs [steps] in order, each planned on the latest screen. Stops at the first step that does not
     * succeed (its result is returned) and empties the queue then.
     */
    fun runSequence(steps: List<StepPlanner>): SequenceResult {
        synchronized(lock) {
            if (stopped) return SequenceResult(ExecutionResult.of(ExecutionCode.STOPPED), 0)
            queue.addAll(steps)
        }
        var latest: ScreenSnapshot? = null
        var latestAt = 0L
        var last = ExecutionResult.of(ExecutionCode.SUCCESS)
        var done = 0
        while (true) {
            val planner = synchronized(lock) { if (stopped) null else queue.removeFirstOrNull() }
                ?: return SequenceResult(if (stopped) ExecutionResult.of(ExecutionCode.STOPPED) else last, done)
            if (latest == null || !isFresh(latestAt)) {
                latest = screen.snapshot() ?: return finish(ExecutionResult.refused(RefusalReason.NO_ACTIVE_TASK), done)
                latestAt = clock()
            }
            val action = planner.plan(latest) ?: run {
                metrics.elementNotFound()
                return finish(ExecutionResult.of(ExecutionCode.ELEMENT_NOT_FOUND), done)
            }
            // A quick planner leaves the screen just read live; a slow one makes execute read again.
            val result = executeOn(action, null, latest.takeIf { isFresh(latestAt) })
            if (!result.ok) return finish(result, done)
            done++
            last = result
            latest = result.screen
            latestAt = clock()
        }
    }

    private fun isFresh(readAt: Long) = clock() - readAt <= timing.freshMs

    fun runActions(actions: List<ScreenAction>): SequenceResult = runSequence(actions.map(StepPlanner::fixed))

    private fun finish(result: ExecutionResult, done: Int): SequenceResult {
        synchronized(lock) { queue.clear() }
        return SequenceResult(if (stopped) ExecutionResult.of(ExecutionCode.STOPPED) else result, done)
    }

    /** Runs one step. [expectedPackage]: the app that must be in front (G-22 plans pass it). */
    fun execute(action: ScreenAction, expectedPackage: String? = null): ExecutionResult =
        executeOn(action, expectedPackage, null)

    /** [live]: a screen read just now (within [ExecutorTiming.freshMs]); null reads a new one. */
    private fun executeOn(action: ScreenAction, expectedPackage: String?, live: ScreenSnapshot?): ExecutionResult {
        if (stopped) return ExecutionResult.of(ExecutionCode.STOPPED)
        when (action) {
            ScreenAction.Done -> return ExecutionResult.of(ExecutionCode.DONE)
            is ScreenAction.HandOver -> return ExecutionResult(ExecutionCode.HANDED_OVER, handOver = action.reason)
            is ScreenAction.Wait -> return waitFor(action.millis)
            else -> Unit
        }
        val before = live ?: screen.snapshot() ?: return ExecutionResult.refused(RefusalReason.NO_ACTIVE_TASK)
        if (stopped) return ExecutionResult.of(ExecutionCode.STOPPED)
        val step = prepare(action, before, expectedPackage)
        val run = when (step) {
            is Prepared.Blocked -> return step.result
            is Prepared.Ready -> step.run
        }
        val performed = run()
        if (stopped) return ExecutionResult.of(ExecutionCode.STOPPED)
        if (!performed) return ExecutionResult.of(ExecutionCode.FAILED)
        return if (action is ScreenAction.OpenApp) {
            settle(before, timing.settleMaxMs * LAUNCH_FACTOR) { activePackage(it) == action.app.packageName }
        } else {
            settle(before, timing.settleMaxMs, null)
        }
    }

    private sealed interface Prepared {
        class Ready(val run: () -> Boolean) : Prepared
        class Blocked(val result: ExecutionResult) : Prepared
    }

    private fun prepare(action: ScreenAction, live: ScreenSnapshot, expectedPackage: String?): Prepared = when (action) {
        ScreenAction.Home -> Prepared.Ready(performer::home)
        is ScreenAction.OpenApp ->
            if (gate.allows(action.app.packageName)) Prepared.Ready { performer.openApp(action.app) } else refuse(RefusalReason.PACKAGE_NOT_ALLOWED)
        ScreenAction.Back -> {
            val front = activePackage(live)
            when {
                expectedPackage != null && front != expectedPackage -> blocked(ExecutionCode.SCREEN_CHANGED)
                front == null || !gate.allows(front) -> refuse(RefusalReason.PACKAGE_NOT_ALLOWED)
                else -> Prepared.Ready(performer::back)
            }
        }
        is ScreenAction.Scroll -> {
            val target = if (action.target == null) largestScrollable(live) else resolve(action.target, live)
            when (target) {
                is Resolved.Element -> checked(target.element, action, expectedPackage) {
                    performer.scroll(target.element, action.direction, action.length)
                }
                is Resolved.Missing -> target.prepared
            }
        }
        is ScreenAction.Targeted -> when (val target = resolve(action.target, live)) {
            is Resolved.Element -> checked(target.element, action, expectedPackage) { perform(action, target.element) }
            is Resolved.Missing -> target.prepared
        }
        is ScreenAction.Wait, is ScreenAction.HandOver, ScreenAction.Done -> error("handled before")
    }

    private fun perform(action: ScreenAction.Targeted, element: ScreenElement): Boolean = when (action) {
        is ScreenAction.Tap -> performer.tap(element)
        is ScreenAction.LongPress -> performer.longPress(element)
        is ScreenAction.TypeText -> performer.type(element, action.text)
    }

    private sealed interface Resolved {
        class Element(val element: ScreenElement) : Resolved
        class Missing(val prepared: Prepared) : Resolved
    }

    private fun resolve(ref: ElementRef, live: ScreenSnapshot): Resolved = when (val match = targetCheck.check(ref, live)) {
        is TargetMatch.Same -> Resolved.Element(match.element)
        TargetMatch.Changed -> Resolved.Missing(blocked(ExecutionCode.SCREEN_CHANGED))
        TargetMatch.Gone -> {
            metrics.elementNotFound()
            Resolved.Missing(blocked(ExecutionCode.ELEMENT_NOT_FOUND))
        }
    }

    /** Untargeted scroll: the biggest scrollable area of the app in front. */
    private fun largestScrollable(live: ScreenSnapshot): Resolved {
        val front = activePackage(live)
        val best = live.elements
            .filter { it.scrollable && !it.secret && it.packageName == front }
            .maxByOrNull { it.bounds.width.toLong() * it.bounds.height }
        if (best != null) return Resolved.Element(best)
        metrics.elementNotFound()
        return Resolved.Missing(blocked(ExecutionCode.ELEMENT_NOT_FOUND))
    }

    /** Safety rules on the live element, in order of importance. */
    private fun checked(e: ScreenElement, action: ScreenAction, expectedPackage: String?, run: () -> Boolean): Prepared {
        val pkg = e.packageName
        return when {
            e.secret -> refuse(RefusalReason.SECRET_FIELD)
            pkg == null || !gate.allows(pkg) -> refuse(RefusalReason.PACKAGE_NOT_ALLOWED)
            expectedPackage != null && pkg != expectedPackage -> blocked(ExecutionCode.SCREEN_CHANGED)
            !e.enabled -> refuse(RefusalReason.DISABLED)
            action is ScreenAction.TypeText && !e.editable -> refuse(RefusalReason.NOT_EDITABLE)
            else -> Prepared.Ready(run)
        }
    }

    private fun refuse(reason: RefusalReason) = Prepared.Blocked(ExecutionResult.refused(reason))

    private fun blocked(code: ExecutionCode) = Prepared.Blocked(ExecutionResult.of(code))

    /**
     * Polls until the screen changed and then stayed the same for one poll (and [until] holds),
     * or [maxMs] passed. No fixed delay: a quick screen continues after two short polls.
     */
    private fun settle(before: ScreenSnapshot, maxMs: Long, until: ((ScreenSnapshot) -> Boolean)?): ExecutionResult {
        val start = clock()
        val base = signature(before)
        var lastSig = base
        var last: ScreenSnapshot? = null
        var changed = false
        while (clock() - start < maxMs) {
            if (stopped) return ExecutionResult.of(ExecutionCode.STOPPED)
            pause.sleep(timing.pollMs)
            val now = screen.snapshot() ?: break
            val sig = signature(now)
            if (sig != base) changed = true
            val stable = changed && sig == lastSig
            lastSig = sig
            last = now
            if (stable && (until == null || until(now))) break
        }
        if (stopped) return ExecutionResult.of(ExecutionCode.STOPPED)
        return ExecutionResult(ExecutionCode.SUCCESS, screenChanged = changed, screen = last)
    }

    private fun waitFor(millis: Long): ExecutionResult {
        val end = clock() + millis
        while (true) {
            if (stopped) return ExecutionResult.of(ExecutionCode.STOPPED)
            val left = end - clock()
            if (left <= 0) return ExecutionResult.of(ExecutionCode.SUCCESS)
            pause.sleep(minOf(left, timing.waitSliceMs))
        }
    }

    private companion object {
        /** Opening an app takes longer than an in-app reaction. */
        const val LAUNCH_FACTOR = 3

        fun activePackage(s: ScreenSnapshot): String? = s.frontPackage()

        /** Content fingerprint of a screen, compared in memory only. */
        fun signature(s: ScreenSnapshot): Int {
            var h = s.windows.hashCode()
            for (e in s.elements) {
                h = 31 * h + listOf(e.role, e.label, e.bounds, e.checked, e.selected, e.focused, e.enabled, e.windowId).hashCode()
            }
            return h
        }
    }
}
