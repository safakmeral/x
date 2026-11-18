package tr.yasliasistan.app.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import tr.yasliasistan.core.contacts.ContactAccess
import tr.yasliasistan.core.contacts.ContactBook
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.masking.ContactNameSource

/**
 * Reads only id + display name from the phone's address book (PLAN G-36, data minimization:
 * no numbers, e-mails or photos). Without READ_CONTACTS it returns an empty list. Nothing is
 * copied to disk, logged or sent anywhere.
 */
class AndroidContactNameSource(private val context: Context) : ContactNameSource {

    override fun contactNames(): List<ContactName> {
        if (!hasContactsPermission(context)) return emptyList()
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        return try {
            context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val list = ArrayList<ContactName>(cursor.count)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol)?.trim().orEmpty()
                        if (name.isNotEmpty()) list += ContactName(cursor.getLong(idCol).toString(), name)
                    }
                    list
                } ?: emptyList()
        } catch (e: SecurityException) {
            SafeLog.event(TAG, "contacts read refused", SafeLog.Level.WARN)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "Contacts"
    }
}

fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/**
 * The app's [ContactAccess]: checks READ_CONTACTS on every lookup (the user may revoke it at any
 * time) and keeps one [ContactBook] in memory only. The book is rebuilt after the address book
 * changes (content observer) and dropped as soon as the permission is gone.
 */
class AndroidContactAccess(context: Context) : ContactAccess {
    private val appContext = context.applicationContext
    private val source = AndroidContactNameSource(appContext)
    private val cache = ContactBookCache(load = { ContactBook.from(source) }, now = System::currentTimeMillis)
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = cache.clear()
    }

    override fun isGranted(): Boolean {
        val granted = hasContactsPermission(appContext)
        if (!granted) cache.clear()
        return granted
    }

    override fun book(): ContactBook {
        if (!isGranted()) return ContactBook.EMPTY
        startObserving()
        return cache.get()
    }

    /** Builds the book ahead of time (e.g. when listening starts) so the lookup itself is instant. */
    fun prefetch() {
        if (isGranted()) book()
    }

    /** Frees the in-memory copy (e.g. when the assistant service stops). */
    @Synchronized
    fun release() {
        if (observing) {
            appContext.contentResolver.unregisterContentObserver(observer)
            observing = false
        }
        cache.clear()
    }

    @Synchronized
    private fun startObserving() {
        if (observing) return
        try {
            appContext.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observing = true
        } catch (e: SecurityException) {
            SafeLog.event("Contacts", "observer refused; relying on refresh interval", SafeLog.Level.WARN)
        }
    }
}
