package tr.yasliasistan.speech.recognition

/**
 * Turns the on-device recognizer's language lists (Android 13+ RecognitionSupport) into an
 * [EngineAvailability]. Only on-device lists are used; online languages are irrelevant (NFG-17).
 */
object LanguageSupport {

    fun classify(
        installed: List<String>,
        pending: List<String>,
        supported: List<String>,
        language: String,
    ): EngineAvailability = when {
        installed.any { sameLanguage(it, language) } -> EngineAvailability.AVAILABLE
        pending.any { sameLanguage(it, language) } -> EngineAvailability.LANGUAGE_DOWNLOADING
        supported.any { sameLanguage(it, language) } -> EngineAvailability.LANGUAGE_NOT_INSTALLED
        // The service reported nothing at all: it cannot tell us, so listening decides.
        installed.isEmpty() && pending.isEmpty() && supported.isEmpty() -> EngineAvailability.UNKNOWN
        // It lists other languages it can download, but not Turkish.
        supported.isNotEmpty() -> EngineAvailability.LANGUAGE_NOT_SUPPORTED
        // Only other installed languages and no download list: a download request may still help.
        else -> EngineAvailability.LANGUAGE_NOT_INSTALLED
    }

    /** "tr", "tr-TR", "tr_TR" and "TR-tr" all mean Turkish. */
    fun sameLanguage(tag: String, language: String): Boolean =
        primary(tag).isNotEmpty() && primary(tag) == primary(language)

    private fun primary(tag: String): String = tag.trim().substringBefore('-').substringBefore('_').lowercase()
}
