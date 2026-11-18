package tr.yasliasistan.app.overlay

/**
 * What the overlay must show. G-11 maps the dialog session state onto this;
 * the overlay itself holds no dialog logic.
 *
 * @property sessionActive an assistant session is running (FG-11: "Dur" must be on screen).
 * @property listening the microphone is open right now (privacy: always visibly indicated).
 * @property typeVisible the "Yazarak söyle" button is offered (FG-04; only while input is expected).
 * @property confirmVisible the large "Evet" / "Hayır" buttons are offered (FG-07; only while a
 *   yes/no question is asked; UD-1 Ö-3).
 * @property cloudBusy a request to the cloud proxy is on its way (G-34, NFG-58: visible indicator).
 */
data class OverlayState(
    val sessionActive: Boolean = false,
    val listening: Boolean = false,
    val typeVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val cloudBusy: Boolean = false,
) {
    /** "Dur" is shown whenever anything is running; listening implies a session. */
    val stopVisible: Boolean get() = sessionActive || listening

    /** The open microphone wins; otherwise a cloud request shows the "İnternet" look (G-34). */
    val bubbleLook: BubbleLook get() = when {
        listening -> BubbleLook.LISTENING
        cloudBusy -> BubbleLook.CLOUD
        else -> BubbleLook.IDLE
    }

    companion object {
        val IDLE = OverlayState()
    }
}

enum class BubbleLook { IDLE, LISTENING, CLOUD }

/** Single entry point G-11 wires to `DialogSession` (BubbleTapped / StopPressed). */
interface OverlayCallbacks {
    /** The user tapped the bubble (not a drag). Called on the main thread. */
    fun onBubbleTapped()

    /** The user pressed "Dur". Called on the main thread. */
    fun onStopPressed()

    /** The user pressed "Yazarak söyle" (FG-04). Called on the main thread. */
    fun onTypePressed()

    /** The user pressed "Evet" ([yes] true) or "Hayır" on the confirmation question. Main thread. */
    fun onConfirmTapped(yes: Boolean)
}
