package tr.yasliasistan.core.clarify

/** One saved number of a contact; never logged (personal data). */
class PhoneEntry(val number: String, val isMobile: Boolean, val isPrimary: Boolean) {
    override fun toString() = "PhoneEntry(<hidden>)"
}

/**
 * Number choice without asking (G-37, FG-101): a second question about the number would break the
 * one-question limit. The user's own choice wins (primary), then a mobile number, then the first.
 */
object PhoneNumbers {

    fun pick(entries: List<PhoneEntry>): String? {
        val usable = entries.filter { digits(it.number).isNotEmpty() }
        return (usable.firstOrNull { it.isPrimary } ?: usable.firstOrNull { it.isMobile } ?: usable.firstOrNull())?.number
    }

    /**
     * International digits for a WhatsApp chat link ("0532 111 22 33" -> "905321112233").
     * Turkish national formats get the 90 country code; null when the number cannot be used.
     */
    fun forWhatsApp(number: String): String? {
        val trimmed = number.trim()
        val d = digits(trimmed)
        val intl = when {
            trimmed.startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.length == 11 && d.startsWith("0") -> "90" + d.drop(1)
            d.length == 10 && d.startsWith("5") -> "90$d"
            d.length == 12 && d.startsWith("90") -> d
            else -> return null
        }
        return intl.takeIf { it.length in 8..15 }
    }

    private fun digits(s: String) = s.filter(Char::isDigit)
}
