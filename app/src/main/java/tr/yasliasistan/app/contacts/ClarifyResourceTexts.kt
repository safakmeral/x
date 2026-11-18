package tr.yasliasistan.app.contacts

import android.content.Context
import tr.yasliasistan.app.R
import tr.yasliasistan.app.session.ResourceTexts
import tr.yasliasistan.core.clarify.ClarifyPhrases
import tr.yasliasistan.core.contacts.ContactPhrases

/** Turkish sentences of the clarification rules (G-37), from strings.xml. */
class ClarifyResourceTexts(
    private val context: Context,
    contacts: ContactPhrases = ContactResourceTexts(context),
) : ClarifyPhrases, ContactPhrases by contacts {

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    override fun confirmCall(name: String) = s(R.string.clarify_confirm_call, name)
    override fun confirmCallInexact(name: String) = s(R.string.clarify_confirm_call_inexact, name)
    override fun callingAnnouncement(name: String) = s(R.string.clarify_calling, name)
    override fun confirmMessage(name: String, app: String) = s(R.string.clarify_confirm_message, name, app)
    override fun confirmMessageInexact(name: String, app: String) = s(R.string.clarify_confirm_message_inexact, name, app)
    override fun messageAnnouncement(name: String) = s(R.string.clarify_messaging, name)
    override fun smsLabel() = s(R.string.clarify_sms_label)
    override fun askWhomToCall() = s(R.string.clarify_whom_call)
    override fun askWhomToMessage() = s(R.string.clarify_whom_message)

    override fun chooseMessageApp(labels: List<String>) =
        s(R.string.clarify_choose_message_app, ResourceTexts.joinOptions(labels, s(R.string.decision_or)))

    override fun appNotInstalled(label: String) = s(R.string.clarify_app_not_installed, label)
    override fun stillNotFound() = s(R.string.clarify_still_not_found)
    override fun openingContactsPermission() = s(R.string.clarify_opening_permission)
    override fun confirmContactsPermission() = s(R.string.clarify_confirm_permission)

    override fun confirmVolume(up: Boolean?) = s(
        when (up) {
            true -> R.string.clarify_confirm_volume_up
            false -> R.string.clarify_confirm_volume_down
            null -> R.string.clarify_confirm_volume_show
        },
    )

    override fun confirmDisplaySettings() = s(R.string.clarify_confirm_display)

    override fun volumeAnnouncement(up: Boolean?) = s(
        when (up) {
            true -> R.string.clarify_volume_up
            false -> R.string.clarify_volume_down
            null -> R.string.clarify_volume_show
        },
    )

    override fun brightnessAnnouncement() = s(R.string.clarify_brightness)
    override fun textSizeAnnouncement() = s(R.string.clarify_text_size)
    override fun cancelled() = s(R.string.dialog_cancelled)
}
