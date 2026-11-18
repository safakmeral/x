package tr.yasliasistan.app.contacts

import tr.yasliasistan.core.contacts.ContactBook

/**
 * Holds one address-book snapshot in memory (never on disk). Rebuilt when marked stale by a
 * change notice or after [maxAgeMs], in case a change notice was missed. Thread-safe.
 */
class ContactBookCache(
    private val load: () -> ContactBook,
    private val now: () -> Long,
    private val maxAgeMs: Long = MAX_AGE_MS,
) {
    private var book: ContactBook? = null
    private var loadedAt = 0L

    @Synchronized
    fun get(): ContactBook {
        val current = book
        if (current != null && now() - loadedAt < maxAgeMs) return current
        return load().also {
            book = it
            loadedAt = now()
        }
    }

    /** Address book changed, permission gone or assistant stopped: drop the copy; the next [get] rebuilds. */
    @Synchronized
    fun clear() {
        book = null
    }

    companion object {
        const val MAX_AGE_MS: Long = 5 * 60_000
    }
}
