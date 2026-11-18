package tr.yasliasistan.core.clarify

import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.InstalledAppsSource
import tr.yasliasistan.core.decision.SystemScreen
import tr.yasliasistan.core.decision.VolumeChange
import tr.yasliasistan.core.text.TurkishText

/**
 * Volume, brightness, text size and weather (G-37). None of these needs a question: without a
 * direction the volume panel or the settings screen is shown and the user adjusts it (FG-102:
 * asking "louder or quieter?" would be an unnecessary question). Indirect phrases ("sesi çok az")
 * are first turned into the direct command by [IndirectPhrases] (G-39).
 */
internal object SettingsIntents {

    fun isSettingsCommand(tokens: List<String>) =
        tokens.any(CommandWords::isVolumeNoun) || tokens.any(CommandWords::isBrightnessNoun) ||
            isTextSize(tokens) || CommandWords.isWeather(tokens) || IndirectPhrases.match(tokens) != null

    /** null = not a settings command, or one the rules leave to the next layer. */
    fun decide(original: List<String>, apps: InstalledAppsSource, phrases: ClarifyPhrases): Decision? {
        val tokens = IndirectPhrases.match(original)?.let(IndirectPhrases::canonical) ?: original
        return when {
            tokens.any(CommandWords::isVolumeNoun) -> volume(tokens, phrases)
            tokens.any(CommandWords::isBrightnessNoun) -> screen(SystemScreen.BRIGHTNESS, tokens, phrases)
            isTextSize(tokens) -> screen(SystemScreen.TEXT_SIZE, tokens, phrases)
            CommandWords.isWeather(tokens) -> weather(apps)
            else -> null
        }
    }

    private fun isTextSize(tokens: List<String>) =
        tokens.any(CommandWords::isTextNoun) && tokens.any(CommandWords::isSizeWord)

    private fun volume(tokens: List<String>, phrases: ClarifyPhrases): Decision? {
        if (tokens.any(CommandWords::isOffVerb)) return null // mute / silent mode: not a rule here
        val up = CommandWords.direction(tokens)
        val change = when (up) {
            true -> VolumeChange.UP
            false -> VolumeChange.DOWN
            null -> VolumeChange.SHOW
        }
        return Decision.Act(
            Action.AdjustVolume(change),
            certain = true,
            readback = phrases.confirmVolume(up),
            announcement = phrases.volumeAnnouncement(up),
        )
    }

    private fun screen(screen: SystemScreen, tokens: List<String>, phrases: ClarifyPhrases) = Decision.Act(
        Action.OpenSystemScreen(screen, CommandWords.direction(tokens)),
        certain = true,
        readback = phrases.confirmDisplaySettings(),
        announcement = if (screen == SystemScreen.BRIGHTNESS) phrases.brightnessAnnouncement() else phrases.textSizeAnnouncement(),
    )

    /** Weather apps from the launcher list; none -> null (the language model may still help). */
    private fun weather(apps: InstalledAppsSource): Decision? {
        val found = apps.launchableApps().filter { CommandWords.isWeatherApp(TurkishText.tokens(it.label)) }
            .distinctBy { it.packageName }
        return when (found.size) {
            0 -> null
            1 -> Decision.Act(Action.OpenApp(found.single()), certain = true)
            else -> Decision.Choose(found.map(Action::OpenApp))
        }
    }
}
