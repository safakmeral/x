package tr.yasliasistan.core.consent

/**
 * Separate, independently revocable consents (NFG-56, NFG-77). They are never bundled:
 * granting or refusing one never changes another. The accessibility notice (NFG-65) is a
 * separate flow (G-05) and is not stored here.
 */
enum class ConsentPurpose(val key: String) {
    /** Masked text sent abroad to the cloud language model (Gemini, Groq fallback, Cloudflare proxy). */
    CLOUD_TRANSFER("cloud_transfer"),

    /** Saving finished tasks as on-device recipes (G-45). */
    RECIPE_MEMORY("recipe_memory"),

    /** Research voice recordings for WER measurement (G-51). */
    RESEARCH_AUDIO("research_audio"),
    ;

    companion object {
        fun fromKey(key: String): ConsentPurpose? = entries.firstOrNull { it.key == key }
    }
}

enum class ConsentDecision { GRANTED, REFUSED }

/** What the user decided, when, and which notice text version they saw (NFG-78). Stays on the device. */
data class ConsentRecord(
    val purpose: ConsentPurpose,
    val decision: ConsentDecision,
    val atEpochMillis: Long,
    val textVersion: Int,
)

enum class ConsentStatus {
    /** Never answered: treated as "no". */
    NOT_ASKED,
    GRANTED,
    REFUSED,

    /** Answered for an older notice text: treated as "no" and asked again. */
    OUTDATED,
}

/** Persistence port; the app implements it with private preferences. */
interface ConsentStore {
    fun load(): List<ConsentRecord>
    fun save(record: ConsentRecord)
    fun clear()
}

/**
 * The single gate the cloud client (G-34) checks before building any request (NFG-78, FG-103).
 * False means: do not create the call at all.
 */
fun interface CloudTransferGate {
    fun cloudTransferAllowed(): Boolean
}
