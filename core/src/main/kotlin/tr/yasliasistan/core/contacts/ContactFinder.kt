package tr.yasliasistan.core.contacts

import tr.yasliasistan.core.masking.ContactIndex
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.masking.ContactNameSource

/**
 * One in-memory snapshot of the address book (PLAN G-36). The person matcher and the masking
 * index (G-31) are built from the same snapshot; nothing is written to disk. Rebuild it when the
 * address book changes.
 */
class ContactBook private constructor(contacts: List<ContactName>) {
    val matcher: ContactMatcher = ContactMatcher.build(contacts)

    /** Masking index for G-31/G-32, built only when masking first needs it. */
    val maskingIndex: ContactIndex by lazy { ContactIndex.build(contacts) }

    val size: Int get() = matcher.size

    companion object {
        fun of(contacts: List<ContactName>) = ContactBook(contacts)
        fun from(source: ContactNameSource) = ContactBook(source.contactNames())
        val EMPTY = ContactBook(emptyList())
    }
}

/**
 * The address book as the assistant may see it. READ_CONTACTS is optional (V-14): without it the
 * rest of the app works and person lookups answer [ContactLookup.NoPermission].
 */
interface ContactAccess {
    fun isGranted(): Boolean

    /** Current snapshot; called only when [isGranted]. */
    fun book(): ContactBook
}

/** Result of [ContactFinder.find]. */
sealed interface ContactLookup {
    data class Matched(val match: ContactMatch) : ContactLookup

    /** The user has not allowed address-book access: say so plainly, then use the screen. */
    data object NoPermission : ContactLookup
}

/** Looks a person up on the device; the address book never leaves it (NFG-46, NFG-79). */
class ContactFinder(private val access: ContactAccess) {
    fun hasPermission(): Boolean = access.isGranted()

    fun find(spoken: String): ContactLookup =
        if (!access.isGranted()) ContactLookup.NoPermission else ContactLookup.Matched(access.book().matcher.match(spoken))
}
