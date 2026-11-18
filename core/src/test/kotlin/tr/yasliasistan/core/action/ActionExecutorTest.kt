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
import tr.yasliasistan.core.action.ActionFixtures.password
import tr.yasliasistan.core.action.ActionFixtures.screen
import tr.yasliasistan.core.action.ActionFixtures.search
import tr.yasliasistan.core.action.ActionFixtures.settingsApp
import tr.yasliasistan.core.action.ActionFixtures.sound
import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.core.screen.Bounds

class ActionExecutorTest {

    private val performer = RecordingPerformer()
    private val time = FakeTime()
    private var notFound = 0
    private val metrics = object : ScreenMetrics {
        override fun elementNotFound() {
            notFound++
        }
    }
    private val allowed = mutableSetOf(SETTINGS)

    private fun executor(screen: ScreenSource) = ActionExecutor(
        screen = screen,
        performer = performer,
        gate = { it in allowed },
        pause = time,
        clock = { time.now },
        metrics = metrics,
    )

    private fun tap(e: tr.yasliasistan.core.screen.ScreenElement) = ScreenAction.Tap(ElementRef.of(e))

    // --- NFG-38: the target must be the planned element ---

    @Test
    fun tapsWhenTargetIsUnchangedAndReportsTheNewScreen() {
        val result = executor(ScriptedScreen(home, displayPage)).execute(tap(display))
        assertEquals(ExecutionCode.SUCCESS, result.code)
        assertEquals(listOf("tap:2"), performer.calls)
        assertTrue(result.screenChanged)
        assertEquals(displayPage, result.screen)
    }

    @Test
    fun movedTargetIsNotTouched() {
        val moved = screen(list, display.copy(bounds = Bounds(0, 600, 1080, 720)), sound)
        val result = executor(ScriptedScreen(moved)).execute(tap(display))
        assertEquals(ExecutionCode.SCREEN_CHANGED, result.code)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun sameNumberWithAnotherLabelIsNotTouched() {
        val other = screen(list, el(2, "Pil", display.bounds), sound)
        val result = executor(ScriptedScreen(other)).execute(tap(display))
        assertEquals(ExecutionCode.ELEMENT_NOT_FOUND, result.code)
        assertTrue(performer.calls.isEmpty())
        assertEquals(1, notFound)
    }

    @Test
    fun otherWindowIsAChangedScreen() {
        val other = screen(list, display.copy(windowId = 9), sound)
        assertEquals(ExecutionCode.SCREEN_CHANGED, executor(ScriptedScreen(other)).execute(tap(display)).code)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun renumberedButUnmovedTargetIsStillTapped() {
        val banner = el(1, "Güncelleme var", Bounds(0, 0, 1080, 90))
        val renumbered = screen(banner, list.copy(id = 2), display.copy(id = 3), sound.copy(id = 4))
        val result = executor(ScriptedScreen(renumbered, displayPage)).execute(tap(display))
        assertEquals(ExecutionCode.SUCCESS, result.code)
        assertEquals(listOf("tap:3"), performer.calls)
    }

    @Test
    fun twoIdenticalCandidatesAreNotGuessed() {
        val doubled = screen(list.copy(id = 9), display.copy(id = 4), display.copy(id = 5))
        assertEquals(ExecutionCode.SCREEN_CHANGED, executor(ScriptedScreen(doubled)).execute(tap(display)).code)
        assertTrue(performer.calls.isEmpty())
    }

    // --- Safety rules ---

    @Test
    fun secretFieldsAreRefusedForEveryAction() {
        val ref = ElementRef.of(password)
        for (action in listOf(ScreenAction.Tap(ref), ScreenAction.LongPress(ref), ScreenAction.TypeText(ref, "1234"))) {
            val result = executor(ScriptedScreen(home)).execute(action)
            assertEquals(RefusalReason.SECRET_FIELD, result.refusal)
        }
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun typingOnlyIntoEditableFields() {
        val ex = executor(ScriptedScreen(home))
        assertEquals(RefusalReason.NOT_EDITABLE, ex.execute(ScreenAction.TypeText(ElementRef.of(display), "x")).refusal)
        assertEquals(ExecutionCode.SUCCESS, ex.execute(ScreenAction.TypeText(ElementRef.of(search), "parlaklık")).code)
        assertEquals(listOf("type:4"), performer.calls)
    }

    @Test
    fun disabledElementIsRefused() {
        val off = display.copy(enabled = false)
        val result = executor(ScriptedScreen(screen(list, off))).execute(tap(off))
        assertEquals(RefusalReason.DISABLED, result.refusal)
    }

    @Test
    fun appOutsideTheGateIsRefused() {
        val pay = el(1, "Öde", Bounds(0, 300, 1080, 420), pkg = BANK)
        val bankScreen = screen(pay, pkg = BANK)
        assertEquals(RefusalReason.PACKAGE_NOT_ALLOWED, executor(ScriptedScreen(bankScreen)).execute(tap(pay)).refusal)
        assertEquals(RefusalReason.PACKAGE_NOT_ALLOWED, executor(ScriptedScreen(bankScreen)).execute(ScreenAction.Back).refusal)
        val bankApp = settingsApp.copy(packageName = BANK)
        assertEquals(RefusalReason.PACKAGE_NOT_ALLOWED, executor(ScriptedScreen(home)).execute(ScreenAction.OpenApp(bankApp)).refusal)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun unknownPackageIsNeverAllowed() {
        val nameless = el(1, "Tamam", Bounds(0, 300, 1080, 420), pkg = null)
        assertEquals(RefusalReason.PACKAGE_NOT_ALLOWED, executor(ScriptedScreen(screen(nameless))).execute(tap(nameless)).refusal)
    }

    @Test
    fun unexpectedAppInFrontIsAChangedScreen() {
        val result = executor(ScriptedScreen(home)).execute(tap(display), expectedPackage = "com.example.other")
        assertEquals(ExecutionCode.SCREEN_CHANGED, result.code)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun homeIsAlwaysAllowed() {
        val bankScreen = screen(el(1, "Öde", Bounds(0, 300, 1080, 420), pkg = BANK), pkg = BANK)
        assertEquals(ExecutionCode.SUCCESS, executor(ScriptedScreen(bankScreen)).execute(ScreenAction.Home).code)
        assertEquals(listOf("home"), performer.calls)
    }

    @Test
    fun nothingIsReadOrDoneWithoutAnOpenTask() {
        val screen = ScriptedScreen(home).apply { active = false }
        val result = executor(screen).execute(tap(display))
        assertEquals(RefusalReason.NO_ACTIVE_TASK, result.refusal)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun systemRefusalIsReportedAsFailed() {
        performer.result = false
        assertEquals(ExecutionCode.FAILED, executor(ScriptedScreen(home)).execute(tap(display)).code)
    }

    // --- Scroll, open app, wait, hand over ---

    @Test
    fun untargetedScrollUsesTheLargestScrollableArea() {
        val result = executor(ScriptedScreen(home)).execute(ScreenAction.Scroll(null, ScrollDirection.DOWN, ScrollLength.SHORT))
        assertEquals(ExecutionCode.SUCCESS, result.code)
        assertEquals(listOf("scroll:1:down:short"), performer.calls)
    }

    @Test
    fun untargetedScrollWithoutScrollableAreaIsNotFound() {
        val result = executor(ScriptedScreen(displayPage)).execute(ScreenAction.Scroll(null, ScrollDirection.UP, ScrollLength.LONG))
        assertEquals(ExecutionCode.ELEMENT_NOT_FOUND, result.code)
        assertEquals(1, notFound)
    }

    @Test
    fun openAppWaitsUntilTheAppIsInFront() {
        val launcher = screen(el(1, "Ayarlar", Bounds(0, 0, 200, 200), pkg = "launcher"), pkg = "launcher")
        allowed += "launcher"
        val screen = ScriptedScreen(launcher, launcher, home, home)
        val result = executor(screen).execute(ScreenAction.OpenApp(settingsApp))
        assertEquals(ExecutionCode.SUCCESS, result.code)
        assertEquals(listOf("open:$SETTINGS"), performer.calls)
        assertEquals(home, result.screen)
    }

    @Test
    fun noReactionEndsAfterTheCapWithoutChange() {
        val result = executor(ScriptedScreen(home)).execute(tap(display))
        assertEquals(ExecutionCode.SUCCESS, result.code)
        assertFalse(result.screenChanged)
        assertTrue(time.now <= ExecutorTiming().settleMaxMs + ExecutorTiming().pollMs)
    }

    @Test
    fun quickScreenContinuesAfterTwoPolls() {
        executor(ScriptedScreen(home, displayPage)).execute(tap(display))
        assertEquals(2 * ExecutorTiming().pollMs, time.now)
    }

    @Test
    fun handOverAndDoneTouchNothing() {
        val ex = executor(ScriptedScreen(home))
        val handed = ex.execute(ScreenAction.HandOver(HandOverReason.USER_CHOICE))
        assertEquals(ExecutionCode.HANDED_OVER, handed.code)
        assertEquals(HandOverReason.USER_CHOICE, handed.handOver)
        assertEquals(ExecutionCode.DONE, ex.execute(ScreenAction.Done).code)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun waitSleepsTheGivenTime() {
        assertEquals(ExecutionCode.SUCCESS, executor(ScriptedScreen(home)).execute(ScreenAction.Wait(1_000)).code)
        assertEquals(1_000L, time.now)
    }

    // --- FG-23: "Dur" ---

    @Test
    fun stopDuringSequenceEmptiesTheQueueAndStartsNothingMore() {
        val ex = executor(ScriptedScreen(home, displayPage))
        performer.onCall = { ex.stop() } // "Dur" pressed while the first tap runs
        val result = ex.runActions(listOf(tap(display), ScreenAction.Back, ScreenAction.Home))
        assertEquals(ExecutionCode.STOPPED, result.last.code)
        assertEquals(listOf("tap:2"), performer.calls)
        assertEquals(0, ex.pendingSteps)
        assertEquals(1, performer.cancelled)
    }

    @Test
    fun stopDuringSettleStartsNoNextStep() {
        val ex = executor(ScriptedScreen(home, displayPage))
        time.onSleep = { ex.stop() }
        val result = ex.runActions(listOf(tap(display), ScreenAction.Back))
        assertEquals(ExecutionCode.STOPPED, result.last.code)
        assertEquals(listOf("tap:2"), performer.calls)
        assertEquals(0, ex.pendingSteps)
    }

    @Test
    fun stopEndsAWait() {
        val ex = executor(ScriptedScreen(home))
        time.onSleep = { if (time.now >= 200) ex.stop() }
        assertEquals(ExecutionCode.STOPPED, ex.execute(ScreenAction.Wait(3_000)).code)
        assertEquals(200L, time.now)
    }

    @Test
    fun afterStopNothingRunsUntilANewTask() {
        val ex = executor(ScriptedScreen(home, displayPage))
        ex.stop()
        assertEquals(ExecutionCode.STOPPED, ex.runActions(listOf(ScreenAction.Home)).last.code)
        assertEquals(ExecutionCode.STOPPED, ex.execute(ScreenAction.Home).code)
        assertTrue(performer.calls.isEmpty())
        ex.newTask()
        assertEquals(ExecutionCode.SUCCESS, ex.execute(ScreenAction.Home).code)
    }

    // --- Sequences ---

    @Test
    fun sequenceStopsAtFirstMismatchWithEmptyQueue() {
        val ex = executor(ScriptedScreen(home, displayPage))
        val result = ex.runActions(listOf(tap(display), tap(sound), ScreenAction.Back))
        assertEquals(ExecutionCode.ELEMENT_NOT_FOUND, result.last.code)
        assertEquals(1, result.stepsDone)
        assertEquals(listOf("tap:2"), performer.calls)
        assertEquals(0, ex.pendingSteps)
    }

    @Test
    fun plannerSeesTheLatestScreen() {
        val ex = executor(ScriptedScreen(home, displayPage))
        val byName = StepPlanner { s -> s.elements.firstOrNull { it.label == "Parlaklık" }?.let { ScreenAction.Tap(ElementRef.of(it)) } }
        val result = ex.runSequence(listOf(StepPlanner.fixed(tap(display)), byName, StepPlanner.fixed(ScreenAction.Done)))
        assertEquals(ExecutionCode.DONE, result.last.code)
        assertEquals(2, result.stepsDone)
        assertEquals(listOf("tap:2", "tap:1"), performer.calls)
    }

    @Test
    fun slowPlannerMakesTheExecutorReadTheScreenAgain() {
        val moved = screen(list, display.copy(bounds = Bounds(0, 600, 1080, 720)), sound)
        val screen = ScriptedScreen(home, moved)
        val slow = StepPlanner { time.now += 500; tap(display) } // e.g. waiting for the language model
        val result = executor(screen).runSequence(listOf(slow))
        assertEquals(ExecutionCode.SCREEN_CHANGED, result.last.code)
        assertEquals(2, screen.reads)
        assertTrue(performer.calls.isEmpty())
    }

    @Test
    fun quickPlannerReusesTheScreenJustRead() {
        val screen = ScriptedScreen(home, displayPage)
        executor(screen).runActions(listOf(tap(display)))
        assertEquals(listOf("tap:2"), performer.calls)
        assertEquals(3, screen.reads) // one before the tap, two while settling
    }

    @Test
    fun plannerWithoutTargetCountsElementNotFound() {
        val result = executor(ScriptedScreen(home)).runSequence(listOf(StepPlanner { null }))
        assertEquals(ExecutionCode.ELEMENT_NOT_FOUND, result.last.code)
        assertEquals(1, notFound)
    }
}
