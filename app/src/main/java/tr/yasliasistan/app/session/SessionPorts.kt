package tr.yasliasistan.app.session

import tr.yasliasistan.app.listen.VoiceNotice
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.dialog.Phrase

/** Sentences the session says that are not produced by the decision engine (strings.xml). */
interface SessionTexts {
    fun phrase(phrase: Phrase): String

    /** "%1$s açıldı." (FG-16: completion is announced). */
    fun appOpened(label: String): String

    fun notice(notice: VoiceNotice): String
}

/** Android side effects of a session; the service implements them, tests fake them. */
interface SessionPorts {
    fun render(state: OverlayState)

    /** Starts [app] with its launcher intent; false when it could not be started. */
    fun launch(app: InstalledApp): Boolean

    /** Opens the "Yazarak söyle" screen (FG-04). */
    fun openTyping()

    /** Opens the microphone / Turkish pack setup screen. */
    fun openVoiceSetup()

    /** Warms the installed-apps list while the user is still talking (less waiting, NFG-23). */
    fun prefetchApps()

    /** G-17 "yavaş konuş": the speech rate becomes Slow (the G-08 setting, kept for next time). */
    fun slowDown() = Unit

    /** FG-23: a spoken "dur" (or the session ending) stops "Sen yap" steps at once, like the button. */
    fun stopScreenActions() = Unit

    /** G-17 "baştan anlat" while working: the finished "Sen yap" steps are told again from the first (G-22). */
    fun retellSteps() = Unit
}

/** Runs decision work off the main thread and delivers its result back on the main thread. */
interface Background {
    fun <T> run(work: () -> T, deliver: (T) -> Unit)
}
