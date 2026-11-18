package tr.yasliasistan.core.masking

/**
 * Placeholder categories of docs/MASKELEME.md §2. [tiePriority]: lower wins when two candidate
 * spans have the same length (§4.9: EPOSTA > IBAN > KART > TCKN > TEL > ADRES > METIN > KISI > OZEL > SAYI).
 */
enum class MaskCategory(val tiePriority: Int) {
    EPOSTA(0), IBAN(1), KART(2), TCKN(3), TEL(4), ADRES(5), METIN(6), KISI(7), OZEL(8), SAYI(9);

    fun placeholder(n: Int) = "[${name}_$n]"
}

/** Why a request must not leave the device (docs/MASKELEME.md §5.9, §7). Codes only, never content. */
enum class BlockReason {
    DOGRULAMA_KODU, GIZLI_BILGI,
    EPOSTA, IBAN, KART, TCKN, TEL, ADRES, REHBER_ADI, OZEL_TERIM, YAZIYLA_SAYI, RAKAM_DIZISI,
    BILINMEYEN_YER_TUTUCU,
}

/**
 * A detected piece of personal data in the preprocessed text. [start]..[end] is what the
 * placeholder replaces (including a written case ending); [caseClass] is the fixed ending put
 * after the placeholder ("'i", "'e", ... or "").
 */
internal data class Span(
    val start: Int,
    val end: Int,
    val category: MaskCategory,
    val value: String,
    val key: String,
    val caseClass: String = "",
    val valueHasSuffix: Boolean = false,
) {
    val length get() = end - start
    fun overlaps(other: Span) = start < other.end && other.start < end
    fun overlaps(from: Int, to: Int) = start < to && from < end
}

/**
 * Placeholder <-> real value table of one task. Lives only in memory (docs/MASKELEME.md §1.5):
 * never written to disk, logs, the cloud or audit copies. [toString] shows only the size.
 */
class PlaceholderTable internal constructor() {
    private val byKey = HashMap<String, String>()
    private val values = LinkedHashMap<String, String>()
    private val withSuffix = HashSet<String>()
    private val counters = IntArray(MaskCategory.entries.size)

    val size: Int get() = values.size

    /** Same category + key always yields the same placeholder (consistency rule, §2). */
    internal fun placeholderFor(category: MaskCategory, key: String, value: String, valueHasSuffix: Boolean): String {
        val mapKey = category.name + "\u0000" + key
        byKey[mapKey]?.let { return it }
        val placeholder = category.placeholder(++counters[category.ordinal])
        byKey[mapKey] = placeholder
        values[placeholder] = value
        if (valueHasSuffix) withSuffix += placeholder
        return placeholder
    }

    internal fun valueOf(placeholder: String): String? = values[placeholder]
    internal fun hasSuffixInValue(placeholder: String) = placeholder in withSuffix

    /** Values currently masked; for the final audit (G-32) and tests. Never log the result. */
    internal fun snapshot(): Map<String, String> = LinkedHashMap(values)
    internal fun suffixedPlaceholders(): Set<String> = HashSet(withSuffix)

    fun clear() {
        byKey.clear(); values.clear(); withSuffix.clear(); counters.fill(0)
    }

    override fun toString() = "PlaceholderTable(size=$size)"

    internal companion object {
        /** Rebuilds a table from known pairs (tests and the response decoder). */
        fun of(pairs: Map<String, String>, suffixed: Collection<String> = emptyList()): PlaceholderTable {
            val table = PlaceholderTable()
            pairs.forEach { (placeholder, value) ->
                table.values[placeholder] = value
                table.byKey["restored\u0000$placeholder"] = placeholder
            }
            table.withSuffix += suffixed
            return table
        }
    }
}

/**
 * Text that went through the masker. The constructor is internal to `:core`, so the cloud client
 * in `:app` can only send what [MaskingSession] produced (PLAN G-31).
 */
class MaskedText internal constructor(val text: String) {
    override fun toString() = text
    override fun equals(other: Any?) = other is MaskedText && other.text == text
    override fun hashCode() = text.hashCode()
}
