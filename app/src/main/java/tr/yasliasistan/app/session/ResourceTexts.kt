package tr.yasliasistan.app.session

import android.content.Context
import tr.yasliasistan.app.R
import tr.yasliasistan.app.listen.VoiceNotice
import tr.yasliasistan.app.listen.textRes
import tr.yasliasistan.core.context.ExplainPhrases
import tr.yasliasistan.core.decision.DecisionPhrases
import tr.yasliasistan.core.dialog.Phrase

/**
 * Turkish sentences of the dialog and the decision engine, from strings.xml (NFG-05). Also supplies
 * the "neden?" sentences (G-39), which switches the decision coordinator's short-term context on.
 */
class ResourceTexts(private val context: Context) :
    SessionTexts,
    DecisionPhrases,
    ExplainPhrases by ExplainResourceTexts(context) {

    override fun phrase(phrase: Phrase): String = context.getString(phraseRes(phrase))

    override fun appOpened(label: String) = context.getString(R.string.app_opened, label)

    override fun notice(notice: VoiceNotice): String = context.getString(notice.textRes())

    override fun openingApp(label: String) = context.getString(R.string.decision_opening_app, label)

    override fun confirmOpenApp(label: String) = context.getString(R.string.decision_confirm_open_app, label)

    override fun chooseApp(labels: List<String>) =
        context.getString(R.string.decision_choose_app, joinOptions(labels, context.getString(R.string.decision_or)))

    override fun tooManyMatches() = context.getString(R.string.decision_too_many)

    override fun cannotDoYet() = context.getString(R.string.decision_cannot_do)

    override fun cancelled() = context.getString(R.string.dialog_cancelled)

    companion object {
        fun phraseRes(phrase: Phrase): Int = when (phrase) {
            Phrase.PROGRESS -> R.string.dialog_progress
            Phrase.DONE -> R.string.dialog_done
            Phrase.CANCELLED -> R.string.dialog_cancelled
            Phrase.STOPPED -> R.string.dialog_stopped
            Phrase.CONFIRM_AGAIN -> R.string.dialog_confirm_again
            Phrase.NOT_HEARD -> R.string.dialog_not_heard
            Phrase.FAILED -> R.string.dialog_failed
            Phrase.CLOSING -> R.string.dialog_closing
        }

        /** "A, B ya da C" (no question particle, so no vowel-harmony mistakes). */
        fun joinOptions(labels: List<String>, or: String): String = when (labels.size) {
            0 -> ""
            1 -> labels.single()
            else -> labels.dropLast(1).joinToString(", ") + " $or " + labels.last()
        }
    }
}
