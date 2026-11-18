package tr.yasliasistan.core.action

import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.core.screen.ScreenSnapshot
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs a "Sen yap" task step by step on top of [ActionExecutor] (PLAN G-22, G-23).
 *
 * For each step: plan on the latest screen; if the element is not visible, scroll in the step's
 * direction to look for it (capped, FG-28); a "devret" step gives the task to the user (FG-26);
 * every screen action counts against the step budget (V-17, FG-27). After an action the screen is
 * compared with what the step expected (FG-24): on a match the step is narrated in one line
 * (FG-22) and remembered for "baştan anlat"; otherwise the task stops with one question and the
 * screen read again. A step that led to an unexpected page is undone with "geri" and is not
 * remembered (FG-25). "Dur" ([ActionExecutor.stop]) ends the task at once (FG-23).
 *
 * Blocking: call [run] from the executor's worker thread. Only codes reach [metrics].
 */
class TaskRunner(
    private val executor: ActionExecutor,
    private val screen: ScreenSource,
    private val texts: StepTexts,
    private val clock: () -> Long,
    private val narrator: Narrator = Narrator.NONE,
    private val metrics: ScreenMetrics = ScreenMetrics.NONE,
    private val budget: Int = Defaults.STEP_BUDGET,
    private val maxExploreScrolls: Int = Defaults.EXPLORE_MAX_SCROLLS,
) {
    // Narration lines of the verified steps of the current task (memory only).
    private val told = CopyOnWriteArrayList<String>()

    /** Lines of the steps done so far, first step first. */
    fun narratedSteps(): List<String> = told.toList()

    /** "Baştan anlat" (FG-12): says the finished steps again from the first one. */
    fun retell() = told.forEach(narrator::say)

    fun run(steps: List<TaskStep>): TaskOutcome {
        told.clear()
        return Run().go(steps)
    }

    private sealed interface Planned {
        class Act(val action: ScreenAction, val screen: ScreenSnapshot) : Planned
        class End(val outcome: TaskOutcome) : Planned
    }

    private inner class Run {
        var actions = 0
        var done = 0

        fun go(steps: List<TaskStep>): TaskOutcome {
            if (executor.stopped) return TaskOutcome.Stopped(0)
            var current = screen.snapshot() ?: return refused(RefusalReason.NO_ACTIVE_TASK)
            for (step in steps) {
                if (executor.stopped) return TaskOutcome.Stopped(done)
                if (step.inPackage != null && current.frontPackage() != step.inPackage) {
                    return ask(StepProblem.SCREEN_CHANGED, current)
                }
                val planned = when (val p = plan(step, current)) {
                    is Planned.End -> return p.outcome
                    is Planned.Act -> p
                }
                val action = planned.action
                when (action) {
                    ScreenAction.Done -> return TaskOutcome.Completed(done, planned.screen)
                    is ScreenAction.HandOver ->
                        return TaskOutcome.HandedOver(action.reason, done, texts.handOver(action.reason), planned.screen)
                    is ScreenAction.Wait -> {
                        if (!executor.execute(action).ok) return TaskOutcome.Stopped(done)
                        current = screen.snapshot() ?: return refused(RefusalReason.NO_ACTIVE_TASK)
                        continue
                    }
                    else -> Unit
                }
                if (actions >= budget) return overBudget(planned.screen)
                current = when (val r = act(step, action, planned.screen)) {
                    is Acted.Next -> r.screen
                    is Acted.End -> return r.outcome
                }
            }
            return TaskOutcome.Completed(done, current)
        }

        /** The step's action on [now]; when the element is not visible, scroll to find it (G-23). */
        private fun plan(step: TaskStep, now: ScreenSnapshot): Planned {
            step.planner.plan(now)?.let { return Planned.Act(it, now) }
            val direction = step.explore ?: return notFound(step, now)
            narrator.say(texts.searching(direction))
            var latest = now
            repeat(maxExploreScrolls) {
                if (actions >= budget) return Planned.End(overBudget(latest))
                actions++
                val scroll = ScreenAction.Scroll(null, direction, ScrollLength.MEDIUM)
                val r = executor.execute(scroll, step.inPackage ?: latest.frontPackage())
                when (r.code) {
                    ExecutionCode.SUCCESS -> Unit
                    ExecutionCode.STOPPED -> return Planned.End(TaskOutcome.Stopped(done))
                    // Nothing to scroll: the executor already counted the miss.
                    ExecutionCode.ELEMENT_NOT_FOUND -> return Planned.End(ask(StepProblem.NOT_FOUND, fresh() ?: latest, step.name))
                    ExecutionCode.REFUSED -> return Planned.End(refused(r.refusal ?: RefusalReason.PACKAGE_NOT_ALLOWED))
                    else -> return Planned.End(ask(StepProblem.SCREEN_CHANGED, fresh() ?: latest))
                }
                latest = r.screen ?: fresh() ?: return Planned.End(refused(RefusalReason.NO_ACTIVE_TASK))
                if (!r.screenChanged) return notFound(step, latest) // end of the list
                step.planner.plan(latest)?.let { return Planned.Act(it, latest) }
            }
            return notFound(step, latest)
        }

        private fun notFound(step: TaskStep, now: ScreenSnapshot): Planned {
            metrics.elementNotFound() // NFG-30 rate
            return Planned.End(ask(StepProblem.NOT_FOUND, now, step.name))
        }

        private fun act(step: TaskStep, action: ScreenAction, before: ScreenSnapshot): Acted {
            val start = clock()
            actions++
            val result = executor.execute(action, step.inPackage ?: before.frontPackage())
            fun failedStep() = metrics.stepFinished(clock() - start, success = false)
            when (result.code) {
                ExecutionCode.SUCCESS -> Unit
                ExecutionCode.STOPPED -> return Acted.End(TaskOutcome.Stopped(done))
                ExecutionCode.SCREEN_CHANGED -> {
                    failedStep()
                    return Acted.End(ask(StepProblem.SCREEN_CHANGED, fresh() ?: before))
                }
                ExecutionCode.ELEMENT_NOT_FOUND -> {
                    failedStep()
                    val now = fresh() ?: before
                    // The element vanished because another app came to the front (the user moved on).
                    if (now.frontPackage() != (step.inPackage ?: before.frontPackage())) {
                        return Acted.End(ask(StepProblem.SCREEN_CHANGED, now))
                    }
                    return Acted.End(ask(StepProblem.NOT_FOUND, now, nameOf(action, before) ?: step.name))
                }
                ExecutionCode.REFUSED -> {
                    failedStep()
                    return Acted.End(refused(result.refusal ?: RefusalReason.PACKAGE_NOT_ALLOWED))
                }
                else -> {
                    failedStep()
                    return Acted.End(TaskOutcome.Failed(done, texts.failed()))
                }
            }
            val after = result.screen ?: fresh() ?: return Acted.End(refused(RefusalReason.NO_ACTIVE_TASK))
            val derived = StepExpectation.derive(action, before)
            // A step's own expectation still has to stay in the derived app unless it names one.
            val expect = step.expect?.let { if (it.packageName == null) it.copy(packageName = derived.packageName) else it } ?: derived
            val verdict = if (step.verify) StepVerifier.verify(expect, before, after, result.screenChanged) else StepVerdict.MATCH
            return when (verdict) {
                StepVerdict.MATCH -> {
                    done++
                    metrics.stepFinished(clock() - start, success = true)
                    val line = narration(action, before)
                    told += line
                    narrator.say(line)
                    Acted.Next(after)
                }
                StepVerdict.NO_REACTION -> {
                    failedStep()
                    Acted.End(ask(StepProblem.NO_REACTION, after))
                }
                StepVerdict.UNEXPECTED_PAGE -> {
                    failedStep()
                    metrics.wrongStep()
                    Acted.End(undo(action, before))
                }
            }
        }

        /** FG-25: "geri" back to the page the step started on; the step is not remembered. */
        private fun undo(action: ScreenAction, before: ScreenSnapshot): TaskOutcome {
            val canUndo = action !is ScreenAction.OpenApp && action != ScreenAction.Home
            var undone = false
            var now: ScreenSnapshot? = null
            if (canUndo) {
                val back = executor.execute(ScreenAction.Back)
                if (back.code == ExecutionCode.STOPPED) return TaskOutcome.Stopped(done)
                now = back.screen ?: fresh()
                undone = back.ok && back.screenChanged && now?.frontPackage() == before.frontPackage()
            }
            return TaskOutcome.NeedsUser(StepProblem.UNEXPECTED_PAGE, undone, done, texts.unexpectedPage(undone), now ?: fresh())
        }

        private fun ask(problem: StepProblem, now: ScreenSnapshot?, name: String? = null): TaskOutcome {
            val message = when (problem) {
                StepProblem.SCREEN_CHANGED -> texts.screenChanged()
                StepProblem.NOT_FOUND -> texts.notFound(name)
                StepProblem.NO_REACTION -> texts.noReaction()
                StepProblem.UNEXPECTED_PAGE -> texts.unexpectedPage(undone = false)
            }
            return TaskOutcome.NeedsUser(problem, undone = false, stepsDone = done, message = message, screen = now)
        }

        private fun overBudget(now: ScreenSnapshot) = TaskOutcome.BudgetExceeded(done, texts.budgetExceeded(), now)

        private fun refused(reason: RefusalReason) = TaskOutcome.Refused(reason, done, texts.refused(reason))
    }

    private sealed interface Acted {
        class Next(val screen: ScreenSnapshot) : Acted
        class End(val outcome: TaskOutcome) : Acted
    }

    /** FG-24 "ekranı yeniden oku": a fresh screen after stopping (null when no task is open). */
    private fun fresh(): ScreenSnapshot? = screen.snapshot()

    private fun narration(action: ScreenAction, before: ScreenSnapshot): String = when (action) {
        is ScreenAction.Tap -> texts.tapped(nameOf(action, before))
        is ScreenAction.LongPress -> texts.longPressed(nameOf(action, before))
        is ScreenAction.TypeText -> texts.typed(nameOf(action, before))
        is ScreenAction.Scroll -> texts.scrolled(action.direction)
        ScreenAction.Back -> texts.wentBack()
        ScreenAction.Home -> texts.wentHome()
        is ScreenAction.OpenApp -> texts.opened(action.app.label)
        is ScreenAction.Wait, is ScreenAction.HandOver, ScreenAction.Done -> error("not narrated")
    }

    /** Speakable name of the action's target as the user saw it when the step was planned. */
    private fun nameOf(action: ScreenAction, before: ScreenSnapshot): String? {
        val ref = (action as? ScreenAction.Targeted)?.target ?: return null
        val element = before.element(ref.id)?.takeIf { it.role == ref.role && it.label == ref.label }
        return SpokenName.of(element)
    }
}
