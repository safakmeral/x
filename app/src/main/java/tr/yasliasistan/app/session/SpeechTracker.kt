package tr.yasliasistan.app.session

/**
 * Barge-in bookkeeping for [AssistantSession] (G-16). Knows which sentences are queued or playing,
 * so that listening during a question starts only once its sound has really started (the voice
 * detector then learns the echo first), and so the echo check knows what the assistant said.
 * Texts are kept in memory only for this comparison and dropped when the session ends. Main thread.
 */
internal class SpeechTracker {
    private val audible = LinkedHashMap<Int, String>()
    private val reference = mutableListOf<String>()
    private var waitingFor = NONE

    fun queued(utteranceId: Int, text: String) {
        audible[utteranceId] = text
        reference += text
    }

    fun ended(utteranceId: Int) {
        audible.remove(utteranceId)
        if (utteranceId == waitingFor) waitingFor = NONE
    }

    /** The dialog wants to listen during [utteranceId]; ignored when it already ended (no voice). */
    fun listenWhenStarted(utteranceId: Int) {
        if (utteranceId in audible) waitingFor = utteranceId
    }

    /** Sound of [utteranceId] started; true when barge-in listening should open now. */
    fun started(utteranceId: Int): Boolean {
        if (utteranceId != waitingFor || utteranceId == NONE) return false
        waitingFor = NONE
        reference.clear()
        reference += audible.values
        return true
    }

    /** G-17: a command turn opens now; the echo reference restarts from what is still to be heard. */
    fun commandListening() {
        reference.clear()
        reference += audible.values
    }

    /** What the assistant has been saying since listening opened (stays after it is cut short). */
    fun echoReference(): String = reference.joinToString(" ")

    fun sessionEnded() {
        waitingFor = NONE
        reference.clear()
    }

    private companion object {
        const val NONE = 0
    }
}
