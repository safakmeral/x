package tr.yasliasistan.app.privacy

import android.content.Context
import tr.yasliasistan.core.consent.ConsentPurpose
import tr.yasliasistan.core.consent.ConsentRecord
import tr.yasliasistan.core.consent.ConsentStore

/**
 * Consent records in app-private preferences (NFG-43, NFG-78). Excluded from backup and
 * device transfer (data_extraction_rules.xml). Written synchronously so a revocation survives
 * an immediate process kill.
 */
class ConsentPrefsStore(context: Context) : ConsentStore {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): List<ConsentRecord> = ConsentPurpose.entries.mapNotNull {
        ConsentRecordCodec.decode(it, prefs.getString(it.key, null))
    }

    override fun save(record: ConsentRecord) {
        prefs.edit().putString(record.purpose.key, ConsentRecordCodec.encode(record)).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val FILE = "consent"
    }
}
