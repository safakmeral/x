package tr.yasliasistan.app.session

import tr.yasliasistan.core.decision.Action

/** Outcome of a device action; texts are already in Turkish (strings.xml). */
sealed interface DeviceResult {
    data class Done(val text: String) : DeviceResult

    /** [text] null = the generic "Bunu yapamadım." */
    data class Failed(val text: String?) : DeviceResult
}

/**
 * Runs the actions of the clarification rules (G-37): dialer, message screen, volume, settings
 * screens, our contacts permission screen. Opening an app stays in [SessionPorts.launch].
 */
fun interface DeviceActions {
    fun run(action: Action): DeviceResult

    /** Actions that take several screen steps ("Sen yap"); [done] is called once, on the main thread. */
    fun runAsync(action: Action, done: (DeviceResult) -> Unit) = done(run(action))

    companion object {
        val NONE = DeviceActions { DeviceResult.Failed(null) }
    }
}
