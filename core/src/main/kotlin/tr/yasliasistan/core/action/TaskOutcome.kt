package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.ScreenSnapshot

/**
 * One planned step of a "Sen yap" task (PLAN G-22/G-23).
 * - [planner] picks the action on the latest screen (null = the element is not visible);
 * - [expect] is what the screen must show afterwards; null derives it ([StepExpectation.derive]);
 * - [inPackage]: the app the step must act in; another app in front stops the task (FG-24);
 * - [explore]: direction to scroll when the element is not visible (FG-28); null = do not search;
 * - [name]: speakable name of the element sought, for the "bulamadım" question (memory only).
 */
class TaskStep(
    val planner: StepPlanner,
    val expect: StepExpectation? = null,
    val inPackage: String? = null,
    val explore: ScrollDirection? = ScrollDirection.DOWN,
    val name: String? = null,
    /**
     * false: the step's result page is not checked (e.g. WhatsApp's Send closes the preview and may
     * land in the chat or the previous app); the step counts as done once the action succeeded.
     */
    val verify: Boolean = true,
) {
    override fun toString() = "TaskStep(inPackage=$inPackage, explore=$explore)"
}

/** Why a task stopped to ask the user one question (FG-24, NFG-30). */
enum class StepProblem {
    /** The screen is not the one the step was planned for (the user or the app moved on). */
    SCREEN_CHANGED,

    /** The element is not on the screen, even after searching (NFG-30). */
    NOT_FOUND,

    /** The action was done but the screen did not react as expected. */
    NO_REACTION,

    /** The action led to an unexpected page; [TaskOutcome.NeedsUser.undone] tells if "geri" fixed it (FG-25). */
    UNEXPECTED_PAGE,
}

/**
 * How a task ended. [message] is the one sentence to say now (null when nothing needs saying,
 * e.g. after "Dur"); [screen] is the last screen read, so a re-plan needs no extra read (FG-24
 * "ekranı yeniden oku"); [stepsDone] counts verified steps.
 */
sealed class TaskOutcome {
    abstract val stepsDone: Int
    abstract val message: String?
    abstract val screen: ScreenSnapshot?

    /** Content-free: messages and screens may hold names, so they are never printed. */
    final override fun toString() = "${javaClass.simpleName}(steps=$stepsDone)"

    data class Completed(override val stepsDone: Int, override val screen: ScreenSnapshot?) : TaskOutcome() {
        override val message: String? get() = null
    }

    data class Stopped(override val stepsDone: Int) : TaskOutcome() {
        override val message: String? get() = null
        override val screen: ScreenSnapshot? get() = null
    }

    /** FG-24/FG-25: stopped and asks one question. */
    data class NeedsUser(
        val problem: StepProblem,
        val undone: Boolean,
        override val stepsDone: Int,
        override val message: String,
        override val screen: ScreenSnapshot?,
    ) : TaskOutcome()

    /** FG-26: the user takes over (choice, secret field, unsure). */
    data class HandedOver(
        val reason: HandOverReason,
        override val stepsDone: Int,
        override val message: String,
        override val screen: ScreenSnapshot?,
    ) : TaskOutcome()

    /** FG-27: the step budget (V-17) ran out; the user takes over. */
    data class BudgetExceeded(
        override val stepsDone: Int,
        override val message: String,
        override val screen: ScreenSnapshot?,
    ) : TaskOutcome()

    data class Refused(
        val reason: RefusalReason,
        override val stepsDone: Int,
        override val message: String,
    ) : TaskOutcome() {
        override val screen: ScreenSnapshot? get() = null
    }

    data class Failed(override val stepsDone: Int, override val message: String) : TaskOutcome() {
        override val screen: ScreenSnapshot? get() = null
    }
}
