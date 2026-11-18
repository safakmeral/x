package tr.yasliasistan.app.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import tr.yasliasistan.core.contacts.ContactBook
import tr.yasliasistan.core.masking.ContactName

class ContactBookCacheTest {

    private var time = 0L
    private var loads = 0
    private val cache = ContactBookCache(
        load = { loads++; ContactBook.of(listOf(ContactName("1", "Ayşe Test"))) },
        now = { time },
        maxAgeMs = 1_000,
    )

    @Test
    fun reusesTheSnapshotUntilChangedOrOld() {
        val first = cache.get()
        assertSame(first, cache.get())
        assertEquals(1, loads)
        cache.clear() // address book changed
        val second = cache.get()
        assertNotSame(first, second)
        time += 1_000 // missed change notice: rebuilt after max age
        cache.get()
        assertEquals(3, loads)
    }

    @Test
    fun permissionAdvice() {
        assertEquals(ContactsPermissionAdvice.GRANTED, ContactsPermissionAdvice.from(true, true, false))
        assertEquals(ContactsPermissionAdvice.ASK, ContactsPermissionAdvice.from(false, false, false))
        assertEquals(ContactsPermissionAdvice.ASK, ContactsPermissionAdvice.from(false, true, true))
        assertEquals(ContactsPermissionAdvice.OPEN_APP_SETTINGS, ContactsPermissionAdvice.from(false, true, false))
    }
}
