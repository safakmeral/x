package tr.yasliasistan.core.context

import tr.yasliasistan.core.clarify.PersonIntent
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.text.TurkishText

/**
 * Resolves references in a new command (PLAN G-39, FG-08) by rewriting it into the plain command
 * the rules already understand; nothing else changes (risk, readback, one-question limit).
 *
 * - "bu / şu uygulama" -> the active app; "bunu / şuna" -> the focused element's label;
 * - "onu da ara", "tekrar ara", "ona da mesaj yaz" -> the previous person;
 * - "onu tekrar aç" -> the previous app.
 *
 * No referent -> no rewrite: the command goes on unchanged ("onu ara" then asks "Kimi arayayım?").
 */
internal object References {

    class Rewrite(val command: Command, val reason: Reason)

    fun rewrite(
        command: Command,
        focus: () -> ScreenFocus?,
        contact: ContactName?,
        app: InstalledApp?,
    ): Rewrite? {
        val tokens = command.tokens
        if (tokens.isEmpty()) return null
        if (tokens.any { it in ContextWords.demonstratives }) return onScreen(tokens, focus())
        if (tokens.none { it in ContextWords.backReferences }) return null
        return previousPerson(tokens, contact) ?: previousApp(tokens, app)
    }

    private fun onScreen(tokens: List<String>, focus: ScreenFocus?): Rewrite? {
        focus ?: return null
        val i = tokens.indexOfFirst { it in ContextWords.demonstratives }
        val appNoun = tokens.getOrNull(i + 1)?.let(ContextWords::isAppNoun) == true
        val (label, drop) = if (appNoun) focus.appLabel to 2 else focus.elementLabel to 1
        label ?: return null
        val rewritten = tokens.take(i) + TurkishText.tokens(label) + tokens.drop(i + drop)
        return Rewrite(Command(rewritten.joinToString(" ")), Reason.OnScreen(label))
    }

    private fun previousPerson(tokens: List<String>, contact: ContactName?): Rewrite? {
        contact ?: return null
        val cleaned = tokens.filterNot { it == "daha" || it == "ayni" }
        val intent = PersonIntent.parse(cleaned) ?: return null
        if (intent == PersonIntent.UNSUPPORTED || intent.personTokens.isNotEmpty()) return null
        val name = contact.displayName
        return Rewrite(Command((TurkishText.tokens(name) + cleaned).joinToString(" ")), Reason.Previous(name))
    }

    private fun previousApp(tokens: List<String>, app: InstalledApp?): Rewrite? {
        app ?: return null
        val verb = tokens.firstOrNull(OpenAppRule::isOpenVerb) ?: return null
        val rest = tokens.filterNot { OpenAppRule.isOpenVerb(it) || it in ContextWords.backReferences || it in fillers }
        if (rest.isNotEmpty()) return null
        return Rewrite(Command((TurkishText.tokens(app.label) + verb).joinToString(" ")), Reason.Previous(app.label))
    }

    private val fillers = setOf("bir", "de", "da", "lutfen", "hadi", "haydi", "misin", "misiniz", "uygulamayi", "uygulamasini")
}
