package tr.yasliasistan.core.decision

import tr.yasliasistan.core.decision.rules.AppNameMatcher
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.dialog.ConfirmationParser

/** Reads the user's answer to a "which one?" question (FG-101). */
internal object ChoiceResolver {

    sealed interface Result {
        data class Picked(val action: Action) : Result
        data object Rejected : Result
        data object NotAnAnswer : Result
    }

    /** Ordinal word stems (ASCII-folded) -> option index. */
    private val ordinals = listOf(
        "birinci" to 0, "ilk" to 0, "ikinci" to 1, "ucuncu" to 2, "dorduncu" to 3, "besinci" to 4,
    )

    fun resolve(answer: Command, options: List<Action>): Result {
        if (ConfirmationParser.parse(answer.raw) == ConfirmationParser.Answer.NO) return Result.Rejected
        byOrdinal(answer.tokens, options.size)?.let { return Result.Picked(options[it]) }
        return byName(answer.tokens, options)?.let(Result::Picked) ?: Result.NotAnAnswer
    }

    private fun byOrdinal(tokens: List<String>, size: Int): Int? {
        val indices = tokens.mapNotNull { token -> ordinals.firstOrNull { token.startsWith(it.first) }?.second }
            .distinct()
        return indices.singleOrNull()?.takeIf { it < size }
    }

    private fun byName(tokens: List<String>, options: List<Action>): Action? {
        val apps = options.filterIsInstance<Action.OpenApp>().map { it.app }
        val key = AppNameMatcher.nameKey(tokens.filterNot(OpenAppRule::isOpenVerb))
        val match = AppNameMatcher.match(key, apps).apps.singleOrNull() ?: return null
        return options.first { it is Action.OpenApp && it.app == match }
    }
}
