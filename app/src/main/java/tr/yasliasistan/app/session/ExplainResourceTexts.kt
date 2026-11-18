package tr.yasliasistan.app.session

import android.content.Context
import tr.yasliasistan.app.R
import tr.yasliasistan.core.clarify.IndirectKind
import tr.yasliasistan.core.context.ExplainPhrases

/** Turkish sentences of the layered "neden?" answer (G-39), from strings_explain.xml. */
class ExplainResourceTexts(private val context: Context) : ExplainPhrases {

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    override fun because(why: String, done: String) = s(R.string.explain_because, why, done)
    override fun youAsked() = s(R.string.explain_you_asked)

    override fun indirectCue(kind: IndirectKind) = s(
        when (kind) {
            IndirectKind.VOLUME_UP -> R.string.explain_indirect_volume_up
            IndirectKind.VOLUME_DOWN -> R.string.explain_indirect_volume_down
            IndirectKind.BRIGHTNESS_UP -> R.string.explain_indirect_brightness_up
            IndirectKind.BRIGHTNESS_DOWN -> R.string.explain_indirect_brightness_down
            IndirectKind.TEXT_UP -> R.string.explain_indirect_text_up
            IndirectKind.TEXT_DOWN -> R.string.explain_indirect_text_down
        },
    )

    override fun previousReference(name: String) = s(R.string.explain_previous_reference, name)
    override fun screenReference(label: String) = s(R.string.explain_screen_reference, label)
    override fun repeated() = s(R.string.explain_repeated)
    override fun doneOpenApp(label: String) = s(R.string.explain_done_open_app, label)
    override fun doneCall(name: String) = s(R.string.explain_done_call, name)
    override fun doneMessage(name: String) = s(R.string.explain_done_message, name)

    override fun doneVolume(up: Boolean?) = s(
        when (up) {
            true -> R.string.explain_done_volume_up
            false -> R.string.explain_done_volume_down
            null -> R.string.explain_done_volume_show
        },
    )

    override fun doneDisplaySettings() = s(R.string.explain_done_display)
    override fun doneContactsPermission() = s(R.string.explain_done_permission)
    override fun doneOther() = s(R.string.explain_done_other)
    override fun askedSeveral() = s(R.string.explain_asked_several)
    override fun askedMissing() = s(R.string.explain_asked_missing)
    override fun cannotDo() = s(R.string.explain_cannot_do)
    override fun nothingRecent() = s(R.string.explain_nothing_recent)
    override fun screenBasis(label: String) = s(R.string.explain_screen_basis, label)
    override fun appBasis(label: String) = s(R.string.explain_app_basis, label)
    override fun contactBasis(name: String) = s(R.string.explain_contact_basis, name)
    override fun contactBasisInexact(name: String) = s(R.string.explain_contact_basis_inexact, name)
    override fun confirmNote() = s(R.string.explain_confirm_note)
    override fun undoNote() = s(R.string.explain_undo_note)
    override fun askDetail() = s(R.string.explain_ask_detail)
    override fun cannotDoDetail() = s(R.string.explain_cannot_do_detail)
    override fun noMoreDetail() = s(R.string.explain_no_more_detail)
}
