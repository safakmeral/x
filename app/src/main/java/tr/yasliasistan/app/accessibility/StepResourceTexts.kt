package tr.yasliasistan.app.accessibility

import android.content.Context
import androidx.annotation.StringRes
import tr.yasliasistan.app.R
import tr.yasliasistan.core.action.HandOverReason
import tr.yasliasistan.core.action.RefusalReason
import tr.yasliasistan.core.action.ScrollDirection
import tr.yasliasistan.core.action.StepTexts

/** Turkish "Sen yap" sentences from strings_steps.xml (G-22, G-23). */
class StepResourceTexts(private val context: Context) : StepTexts {

    override fun tapped(name: String?) = named(R.string.step_tapped_named, R.string.step_tapped, name)
    override fun longPressed(name: String?) = named(R.string.step_long_pressed_named, R.string.step_long_pressed, name)
    override fun typed(name: String?) = named(R.string.step_typed_named, R.string.step_typed, name)

    override fun scrolled(direction: ScrollDirection) = text(
        when (direction) {
            ScrollDirection.DOWN -> R.string.step_scrolled_down
            ScrollDirection.UP -> R.string.step_scrolled_up
            ScrollDirection.LEFT -> R.string.step_scrolled_left
            ScrollDirection.RIGHT -> R.string.step_scrolled_right
        },
    )

    override fun wentBack() = text(R.string.step_went_back)
    override fun wentHome() = text(R.string.step_went_home)
    override fun opened(appName: String): String = context.getString(R.string.step_opened, appName)

    override fun searching(direction: ScrollDirection) = text(
        when (direction) {
            ScrollDirection.DOWN -> R.string.step_searching_down
            ScrollDirection.UP -> R.string.step_searching_up
            ScrollDirection.LEFT -> R.string.step_searching_left
            ScrollDirection.RIGHT -> R.string.step_searching_right
        },
    )

    override fun screenChanged() = text(R.string.step_screen_changed)
    override fun notFound(name: String?) = named(R.string.step_not_found_named, R.string.step_not_found, name)
    override fun noReaction() = text(R.string.step_no_reaction)
    override fun unexpectedPage(undone: Boolean) =
        text(if (undone) R.string.step_unexpected_undone else R.string.step_unexpected)

    override fun handOver(reason: HandOverReason) = text(
        when (reason) {
            HandOverReason.USER_CHOICE -> R.string.step_handover_choice
            HandOverReason.SECRET_FIELD -> R.string.step_handover_secret
            HandOverReason.NOT_FOUND -> R.string.step_handover_not_found
            HandOverReason.UNSURE -> R.string.step_handover_unsure
        },
    )

    override fun budgetExceeded() = text(R.string.step_budget_exceeded)

    override fun refused(reason: RefusalReason) = text(
        when (reason) {
            RefusalReason.NO_ACTIVE_TASK -> R.string.step_refused_no_task
            RefusalReason.SECRET_FIELD -> R.string.step_handover_secret
            RefusalReason.NOT_EDITABLE -> R.string.step_refused_not_editable
            RefusalReason.DISABLED -> R.string.step_refused_disabled
            RefusalReason.PACKAGE_NOT_ALLOWED -> R.string.step_refused_not_allowed
        },
    )

    override fun failed() = text(R.string.step_failed)

    private fun text(@StringRes id: Int): String = context.getString(id)

    private fun named(@StringRes withName: Int, @StringRes without: Int, name: String?): String =
        if (name.isNullOrBlank()) context.getString(without) else context.getString(withName, name)
}
