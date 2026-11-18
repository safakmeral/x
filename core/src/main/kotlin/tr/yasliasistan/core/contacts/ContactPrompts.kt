package tr.yasliasistan.core.contacts

import tr.yasliasistan.core.dialog.ConfirmationParser
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.text.TurkishText

/**
 * Sentences about people. `:core` has no Android resources; the app implements this with
 * strings.xml. Intended Turkish wording is in each comment (NFG-05: short, one question).
 */
interface ContactPhrases {
    /** "Kişilerinizi adıyla bulabilmem için rehberinize bakmam gerekiyor. ..." (NFG-06: reason first). */
    fun permissionReason(): String

    /** "Rehberinize bakma izni vermediniz. Bu yüzden kişiyi rehberden bulamıyorum. ..." */
    fun noPermission(): String

    /** "Rehberde birden çok kişi var. Hangisi: Ayşe Yılmaz ya da Ayşe Demir?" (FG-101, V-23). */
    fun chooseContact(names: List<String>): String

    /** "Rehberde bu adla 5 kişi var. Soyadını da söyler misiniz?" */
    fun tooManyContacts(count: Int): String

    /** "Rehberde bu adla kimseyi bulamadım. Adını bir daha söyler misiniz?" */
    fun contactNotFound(): String
}

/** What to say after a lookup; null means no question is needed (FG-101: one match, no question). */
object ContactPrompts {
    fun questionFor(lookup: ContactLookup, phrases: ContactPhrases): String? = when (lookup) {
        ContactLookup.NoPermission -> phrases.noPermission()
        is ContactLookup.Matched -> when (val m = lookup.match) {
            is ContactMatch.One -> null
            is ContactMatch.Several -> phrases.chooseContact(m.options.map { it.displayName })
            is ContactMatch.TooMany -> phrases.tooManyContacts(m.count)
            ContactMatch.None -> phrases.contactNotFound()
        }
    }
}

/** Reads the answer to "Hangisi: A ya da B?" ("ikincisi", "Demir olan", "Ayşe Demir", "hayır"). */
object ContactChoice {

    sealed interface Result {
        data class Picked(val contact: ContactName) : Result
        data object Rejected : Result
        data object NotAnAnswer : Result
    }

    private val ordinals = listOf("birinci" to 0, "ilk" to 0, "ikinci" to 1, "ucuncu" to 2, "son" to -1)

    fun resolve(answer: String, options: List<ContactName>): Result {
        if (options.isEmpty()) return Result.NotAnAnswer
        if (ConfirmationParser.parse(answer) == ConfirmationParser.Answer.NO) return Result.Rejected
        byOrdinal(TurkishText.tokens(answer), options.size)?.let { return Result.Picked(options[it]) }
        val match = ContactMatcher.build(options).match(answer)
        return if (match is ContactMatch.One) Result.Picked(match.contact) else Result.NotAnAnswer
    }

    private fun byOrdinal(tokens: List<String>, size: Int): Int? {
        val picked = tokens.mapNotNull { token ->
            ordinals.firstOrNull { (stem, _) -> token.startsWith(stem) && (stem != "son" || token in lastWords) }?.second
        }.distinct().singleOrNull() ?: return null
        val index = if (picked < 0) size - 1 else picked
        return index.takeIf { it < size }
    }

    private val lastWords = setOf("son", "sonuncu", "sonuncusu", "sonuncuyu", "sondaki", "sondakini")
}
