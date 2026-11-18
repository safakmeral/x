package tr.yasliasistan.app.privacy

import tr.yasliasistan.core.consent.ConsentDecision
import tr.yasliasistan.core.consent.ConsentPurpose
import tr.yasliasistan.core.consent.ConsentRecord

/** Turns a consent record into one preference string ("GRANTED|1700000000000|1") and back. */
object ConsentRecordCodec {

    fun encode(record: ConsentRecord): String =
        "${record.decision.name}|${record.atEpochMillis}|${record.textVersion}"

    /** Returns null for anything malformed; an unreadable record counts as "never asked" (no consent). */
    fun decode(purpose: ConsentPurpose, value: String?): ConsentRecord? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 3) return null
        val decision = ConsentDecision.entries.firstOrNull { it.name == parts[0] } ?: return null
        val at = parts[1].toLongOrNull() ?: return null
        val version = parts[2].toIntOrNull() ?: return null
        return ConsentRecord(purpose, decision, at, version)
    }
}
