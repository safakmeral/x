package tr.yasliasistan.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.action.ActionFixtures.BANK
import tr.yasliasistan.core.action.ActionFixtures.SETTINGS
import tr.yasliasistan.core.action.ActionFixtures.display
import tr.yasliasistan.core.action.ActionFixtures.displayPage
import tr.yasliasistan.core.action.ActionFixtures.el
import tr.yasliasistan.core.action.ActionFixtures.home
import tr.yasliasistan.core.action.ActionFixtures.list
import tr.yasliasistan.core.action.ActionFixtures.screen
import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ScreenSnapshot

/** G-22/G-23 with made-up screens: verification, undo, search, hand-over and step budget. */
class TaskRunnerTest {

    /** The phone's screen; performer calls move it according to [moves]. */
    private class Device(var current: ScreenSnapshot?) : ScreenSource {
        val moves = HashMap<String, () -> ScreenSnapshot?>()
        override fun snapshot() = current
    }

    private val device = Device(home)
    private val performer = RecordingPerformer().apply {
        onCall = { device.moves[calls.last()]?.let { move -> device.current = move() } }
    }
    private val time = FakeTime()
    private val said = ArrayList<String>()
    private var notFound = 0
    private var wrongSteps = 0
    private val steps = ArrayList<Boolean>()
    private val metrics = object : ScreenMetrics {
        override fun elementNotFound() {
            notFound++
        }

        override fun stepFinished(durationMs: Long, success: Boolean) {
            steps += success
        }

        override fun wrongStep() {
            wrongSteps++
        }
    }
    private val executor = ActionExecutor(
        screen = device,
        performer = performer,
        gate = { it == SETTINGS },
        pause = time,
        clock = { time.now },
        metrics = metrics,
    )

    private fun runner(budget: Int = 10, explore: Int = 5) = TaskRunner(
        executor = executor,
        screen = device,
        texts = FakeStepTexts,
        clock = { time.now },
        narrator = { said += it },
        metrics = metrics,
        budget = budget,
        maxExploreScrolls = explore,
    )

    /** Taps the element labelled [label] if it is on the screen. */
    private fun tapLabel(label: String) = StepPlanner { s ->
        s.elements.firstOrNull { it.label == label }?.let { ScreenAction.Tap(ElementRef.of(it)) }
    }

    private val tapDisplay = TaskStep(tapLabel("Ekran"), expect = StepExpectation(element = ElementExpect("Parlaklık")))
    private val back = TaskStep(StepPlanner.fixed(ScreenAction.Back), expect = StepExpectation(element = ElementExpect("Ekran")))

    // --- G-22: narration and verification (FG-22, FG-24) ---

    @Test
    fun verifiedStepsAreNarratedOnceEachAndRemembered() {
        device.moves["tap:2"] = { displayPage }
        device.moves["back"] = { home }
        val r = runner()
        val outcome = r.run(listOf(tapDisplay, back))
        assertTrue(outcome is TaskOutcome.Completed)
        assertEquals(2, outcome.stepsDone)
        assertEquals(listOf("tapped:Ekran", "back"), said)
        assertEquals(said, r.narratedSteps())
        assertEquals(listOf(true, true), steps)
    }

    @Test
    fun retellSaysTheFinishedStepsFromTheFirst() {
        device.moves["tap:2"] = { displayPage }
        device.moves["back"] = { home }
        val r = runner()
        r.run(listOf(tapDisplay, back))
        said.clear()
        r.retell()
        assertEquals(listOf("tapped:Ekran", "back"), said)
    }

    @Test
    fun noReactionStopsWithOneQuestionAndNoUndo() {
        val outcome = runner().run(listOf(tapDisplay, back))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.NO_REACTION, outcome.problem)
        assertEquals("noReaction", outcome.message)
        assertEquals(listOf("tap:2"), performer.calls) // second step never started, no "geri"
        assertEquals(home, outcome.screen)
        assertTrue(said.isEmpty())
        assertEquals(listOf(false), steps)
    }

    @Test
    fun unexpectedPageIsUndoneAndNotRemembered() {
        val bluetooth = screen(el(1, "Bluetooth", Bounds(0, 300, 1080, 420)))
        device.moves["tap:2"] = { bluetooth }
        device.moves["back"] = { home }
        val r = runner()
        val outcome = r.run(listOf(tapDisplay, back))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.UNEXPECTED_PAGE, outcome.problem)
        assertTrue(outcome.undone)
        assertEquals("unexpected:true", outcome.message)
        assertEquals(listOf("tap:2", "back"), performer.calls)
        assertEquals(0, outcome.stepsDone)
        assertTrue(r.narratedSteps().isEmpty()) // FG-25: not written into the history
        assertEquals(1, wrongSteps)
        assertEquals(home, outcome.screen) // FG-24: the screen is read again
    }

    @Test
    fun unexpectedAppIsNotTouchedAgainAndSaysSo() {
        val bank = screen(el(1, "Hesaplar", Bounds(0, 300, 1080, 420), pkg = BANK), pkg = BANK)
        device.moves["tap:2"] = { bank }
        val outcome = runner().run(listOf(tapDisplay))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.UNEXPECTED_PAGE, outcome.problem)
        assertFalse(outcome.undone)
        assertEquals(listOf("tap:2"), performer.calls) // "geri" in a not allowed app is refused
    }

    @Test
    fun anotherAppInFrontStopsBeforeAnyAction() {
        device.current = screen(el(1, "Ekran", Bounds(0, 300, 1080, 420), pkg = "com.example.launcher"), pkg = "com.example.launcher")
        val outcome = runner().run(listOf(TaskStep(tapLabel("Ekran"), inPackage = SETTINGS)))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.SCREEN_CHANGED, outcome.problem)
        assertEquals("screenChanged", outcome.message)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun userLeavingTheAppAfterPlanningIsAScreenChange() {
        val launcher = screen(el(1, "Saat", Bounds(0, 300, 1080, 420), pkg = "com.example.launcher"), pkg = "com.example.launcher")
        val planner = StepPlanner { s ->
            val action = s.elements.firstOrNull { it.label == "Ekran" }?.let { ScreenAction.Tap(ElementRef.of(it)) }
            device.current = launcher // the user pressed "ana ekran" meanwhile
            action
        }
        val outcome = runner().run(listOf(TaskStep(planner)))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.SCREEN_CHANGED, outcome.problem)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun elementMissingWithoutSearchAsksAndCountsIt() {
        val outcome = runner().run(listOf(TaskStep(tapLabel("Wi-Fi"), explore = null, name = "Wi-Fi")))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.NOT_FOUND, outcome.problem)
        assertEquals("notFound:Wi-Fi", outcome.message)
        assertEquals(1, notFound)
        assertTrue(performer.calls.isEmpty())
    }

    // --- G-23: scroll search (FG-28) ---

    private fun page(n: Int, vararg extra: tr.yasliasistan.core.screen.ScreenElement) =
        screen(list, el(2, "Satır $n", Bounds(0, 300, 1080, 420)), *extra)

    @Test
    fun searchScrollsUntilTheElementAppears() {
        var scrolls = 0
        device.moves["scroll:1:down:medium"] = {
            scrolls++
            if (scrolls < 2) page(scrolls) else page(scrolls, el(3, "Wi-Fi", Bounds(0, 420, 1080, 540)))
        }
        device.moves["tap:3"] = { displayPage }
        val outcome = runner().run(listOf(TaskStep(tapLabel("Wi-Fi"))))
        assertTrue(outcome is TaskOutcome.Completed)
        assertEquals(listOf("scroll:1:down:medium", "scroll:1:down:medium", "tap:3"), performer.calls)
        assertEquals(listOf("searching:down", "tapped:Wi-Fi"), said)
    }

    @Test
    fun searchStopsAtItsLimit() {
        var scrolls = 0
        device.moves["scroll:1:down:medium"] = { page(++scrolls) }
        val outcome = runner(explore = 5).run(listOf(TaskStep(tapLabel("Wi-Fi"))))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.NOT_FOUND, outcome.problem)
        assertEquals(5, performer.calls.size)
        assertEquals(1, notFound)
    }

    @Test
    fun searchStopsAtTheEndOfTheList() {
        // Scrolling changes nothing: the list is at its end.
        val outcome = runner().run(listOf(TaskStep(tapLabel("Wi-Fi"))))
        outcome as TaskOutcome.NeedsUser
        assertEquals(StepProblem.NOT_FOUND, outcome.problem)
        assertEquals(listOf("scroll:1:down:medium"), performer.calls)
    }

    @Test
    fun searchGoesSidewaysWhenTheStepSaysSo() {
        runner(explore = 1).run(listOf(TaskStep(tapLabel("Wi-Fi"), explore = ScrollDirection.RIGHT)))
        assertEquals(listOf("scroll:1:right:medium"), performer.calls)
    }

    // --- G-23: hand-over and budget (FG-26, FG-27) ---

    @Test
    fun userChoiceHandsOverWithASpokenMessage() {
        val outcome = runner().run(listOf(TaskStep(StepPlanner.fixed(ScreenAction.HandOver(HandOverReason.USER_CHOICE)))))
        outcome as TaskOutcome.HandedOver
        assertEquals(HandOverReason.USER_CHOICE, outcome.reason)
        assertEquals("handOver:user_choice", outcome.message)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun budgetExceededHandsOverBeforeTheExtraAction() {
        device.moves["tap:2"] = { displayPage }
        device.moves["back"] = { home }
        val many = List(6) { listOf(tapDisplay, back) }.flatten() // 12 actions
        val outcome = runner(budget = 10).run(many)
        outcome as TaskOutcome.BudgetExceeded
        assertEquals("budget", outcome.message)
        assertEquals(10, performer.calls.size)
        assertEquals(10, outcome.stepsDone)
    }

    @Test
    fun searchScrollsCountAgainstTheBudget() {
        var scrolls = 0
        device.moves["scroll:1:down:medium"] = { page(++scrolls) }
        val outcome = runner(budget = 3).run(listOf(TaskStep(tapLabel("Wi-Fi"))))
        assertTrue(outcome is TaskOutcome.BudgetExceeded)
        assertEquals(3, performer.calls.size)
    }

    @Test
    fun doneEndsTheTask() {
        val outcome = runner().run(listOf(TaskStep(StepPlanner.fixed(ScreenAction.Done)), tapDisplay))
        assertTrue(outcome is TaskOutcome.Completed)
        assertTrue(performer.calls.isEmpty())
    }

    // --- FG-23 ---

    @Test
    fun durDuringAStepEndsTheTask() {
        device.moves["tap:2"] = { executor.stop(); displayPage }
        val outcome = runner().run(listOf(tapDisplay, back))
        assertTrue(outcome is TaskOutcome.Stopped)
        assertEquals(listOf("tap:2"), performer.calls)
        assertTrue(said.isEmpty())
    }

    @Test
    fun noOpenTaskReadsNothing() {
        device.current = null
        val outcome = runner().run(listOf(tapDisplay))
        outcome as TaskOutcome.Refused
        assertEquals(RefusalReason.NO_ACTIVE_TASK, outcome.reason)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun outcomeTextIsNeverPrinted() {
        val outcome = TaskOutcome.NeedsUser(StepProblem.NOT_FOUND, false, 0, "Ayşe Test bulunamadı", null)
        assertFalse(outcome.toString().contains("Ayşe"))
    }
}

/** Codes instead of sentences, so tests see which text was chosen. */
object FakeStepTexts : StepTexts {
    override fun tapped(name: String?) = "tapped:$name"
    override fun longPressed(name: String?) = "long:$name"
    override fun typed(name: String?) = "typed:$name"
    override fun scrolled(direction: ScrollDirection) = "scrolled:${direction.wireName}"
    override fun wentBack() = "back"
    override fun wentHome() = "home"
    override fun opened(appName: String) = "opened:$appName"
    override fun searching(direction: ScrollDirection) = "searching:${direction.wireName}"
    override fun screenChanged() = "screenChanged"
    override fun notFound(name: String?) = "notFound:$name"
    override fun noReaction() = "noReaction"
    override fun unexpectedPage(undone: Boolean) = "unexpected:$undone"
    override fun handOver(reason: HandOverReason) = "handOver:${reason.wireName}"
    override fun budgetExceeded() = "budget"
    override fun refused(reason: RefusalReason) = "refused:$reason"
    override fun failed() = "failed"
}
