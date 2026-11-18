package tr.yasliasistan.core.consent

/**
 * Identity of the data controller shown in the privacy notice (NFG-81, SS-13). The values come
 * from one configuration file (`app/veri-sorumlusu.properties`); while a placeholder is left the
 * notice is not complete and release builds warn.
 */
data class DataControllerInfo(val name: String, val contact: String) {

    val isComplete: Boolean get() = missingFields().isEmpty()

    fun missingFields(): List<String> = buildList {
        if (isPlaceholder(name)) add("name")
        if (isPlaceholder(contact)) add("contact")
    }

    companion object {
        const val NAME_PLACEHOLDER = "[VERI_SORUMLUSU_AD]"
        const val CONTACT_PLACEHOLDER = "[ILETISIM]"

        fun isPlaceholder(value: String): Boolean {
            val v = value.trim()
            return v.isEmpty() || (v.startsWith("[") && v.endsWith("]"))
        }
    }
}
