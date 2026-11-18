package tr.yasliasistan.app.contacts

import android.content.Context
import tr.yasliasistan.app.R
import tr.yasliasistan.app.session.ResourceTexts
import tr.yasliasistan.core.contacts.ContactPhrases

/** Turkish sentences about people, from strings.xml (G-36; names are read aloud per V-23). */
class ContactResourceTexts(private val context: Context) : ContactPhrases {

    override fun permissionReason() = context.getString(R.string.contacts_permission_reason)

    override fun noPermission() = context.getString(R.string.contacts_no_permission)

    override fun chooseContact(names: List<String>) = context.getString(
        R.string.contacts_choose,
        ResourceTexts.joinOptions(names, context.getString(R.string.decision_or)),
    )

    override fun tooManyContacts(count: Int) = context.getString(R.string.contacts_too_many, count)

    override fun contactNotFound() = context.getString(R.string.contacts_not_found)
}
