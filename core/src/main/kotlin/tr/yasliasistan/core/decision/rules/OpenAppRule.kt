package tr.yasliasistan.core.decision.rules

import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.InstalledAppsSource
import tr.yasliasistan.core.decision.Rule

/**
 * "Open app" rule (PLAN G-10): "ayarları aç", "ayarlara gir", "WhatsApp'ı açar mısın".
 *
 * Needs an open verb and an app name that matches a launcher label. Anything else returns null,
 * so the next layer decides, and finally the user hears "Bunu henüz yapamıyorum" instead of a
 * wrong app being opened. Negations ("açma") are not open verbs.
 */
class OpenAppRule(private val apps: InstalledAppsSource) : Rule {

    override fun apply(command: Command): Decision? {
        val verbs = command.tokens.filter(::isOpenVerb)
        if (verbs.isEmpty()) return null
        val key = AppNameMatcher.nameKey(command.tokens.filterNot(::isOpenVerb))
        val result = AppNameMatcher.match(key, apps.launchableApps())
        return when (result.apps.size) {
            0 -> null
            1 -> Decision.Act(Action.OpenApp(result.apps.single()), certain = result.exact)
            else -> Decision.Choose(result.apps.map(Action::OpenApp))
        }
    }

    internal companion object {
        /** Verb stems (ASCII-folded): aç, gir, git/gid, başlat, çalıştır. */
        private val stems = listOf("calistir", "baslat", "ac", "gir", "git", "gid")

        /** Imperative, polite, question and infinitive endings ("açar", "açın", "açsana", "açabilir"...). */
        private val endings = setOf(
            "", "ar", "er", "ir", "in", "iniz", "sana", "sene", "saniza", "senize", "alim", "elim",
            "abilir", "ebilir", "abilirsin", "ebilirsin", "abilirmisin", "ebilirmisin",
            "abilirmisiniz", "ebilirmisiniz", "armisin", "ermisin", "irmisin", "mak", "mek", "iver", "sin",
        )

        fun isOpenVerb(token: String): Boolean =
            stems.any { token.startsWith(it) && token.substring(it.length) in endings }
    }
}
