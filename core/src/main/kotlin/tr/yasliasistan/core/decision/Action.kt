package tr.yasliasistan.core.decision

import tr.yasliasistan.core.masking.ContactName

/**
 * A launcher entry of the phone. Supplied by the app from the launcher intent query
 * (`<queries>` in the manifest, no special permission). The list of installed apps stays on the
 * device and is not logged (NFG-71), so toString hides it.
 */
data class InstalledApp(val label: String, val packageName: String, val activityName: String) {
    override fun toString() = "InstalledApp(<hidden>)"
}

/** Source of launchable apps; implemented in `:app` with PackageManager (PLAN G-11). */
fun interface InstalledAppsSource {
    fun launchableApps(): List<InstalledApp>
}

/** Where a message is written (G-37). SMS uses the phone's default messaging app. */
sealed interface MessageChannel {
    data object Sms : MessageChannel

    /** WhatsApp or WhatsApp Business, found in the launcher list. */
    data class WhatsApp(val app: InstalledApp) : MessageChannel
}

enum class VolumeChange { UP, DOWN, SHOW }

/** System settings screens opened for the user (changing them directly needs WRITE_SETTINGS, NFG-68). */
enum class SystemScreen { BRIGHTNESS, TEXT_SIZE }

/** Something the assistant can do. G-20 extends this with the full action schema. */
sealed interface Action {
    /** Name the user hears in readbacks and choice questions. */
    val spokenName: String

    /** Open an app with its launch intent; low risk and undoable (FG-71). */
    data class OpenApp(val app: InstalledApp) : Action {
        override val spokenName get() = app.label
    }

    /**
     * Open the dialer with the person's number filled in (G-37). The call itself starts only when
     * the user presses the call button; still Medium risk (Table 3, FG-72).
     */
    data class CallContact(val contact: ContactName) : Action {
        override val spokenName get() = contact.displayName
        override fun toString() = "CallContact(id=${contact.id})"
    }

    /** Open a new message to the person in [channel]; nothing is sent by the assistant (FG-72). */
    data class MessageContact(val contact: ContactName, val channel: MessageChannel) : Action {
        override val spokenName get() = contact.displayName
        override fun toString() = "MessageContact(id=${contact.id}, channel=${channel::class.simpleName})"
    }

    /**
     * Demo task (18.11.2026): send the newest photo of the gallery to the person in WhatsApp. The
     * photo is handed to WhatsApp's chat with the share intent, then "Sen yap" presses Send.
     * Sends something to another person, so it needs a confirmation first (Table 3, FG-72).
     */
    data class SendPhoto(
        val contact: ContactName,
        val app: InstalledApp,
        /** "Ben yapayım": the assistant only shows the Send button (yellow ring); the user presses it. */
        val guide: Boolean = false,
    ) : Action {
        override val spokenName get() = contact.displayName
        override fun toString() = "SendPhoto(id=${contact.id})"
    }

    /** Change the volume of the stream the system considers active (no permission needed). */
    data class AdjustVolume(val change: VolumeChange) : Action {
        override val spokenName get() = change.name
    }

    /** Show a settings screen; [raise] is the requested direction (null when not said). */
    data class OpenSystemScreen(val screen: SystemScreen, val raise: Boolean?) : Action {
        override val spokenName get() = screen.name
    }

    /** Show our address-book permission screen (reason first, NFG-06; V-14 optional). */
    data object RequestContactsPermission : Action {
        override val spokenName get() = "contacts-permission"
    }
}
