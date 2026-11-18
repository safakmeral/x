package tr.yasliasistan.speech.tts

import tr.yasliasistan.core.log.SafeLog

/**
 * Queue and interruption logic of [Speaker] on top of a [TtsBackend] (main thread only).
 *
 * - Speech asked for while the engine is still starting is held and spoken once ready, so the
 *   engine can be warmed up early without callers waiting for it.
 * - [stop] silences at once and reports every pending utterance as interrupted exactly once;
 *   late engine callbacks for those utterances are ignored.
 * - If the engine is still starting after [HELD_SPEECH_TIMEOUT_MS] (seen on the A15 with the
 *   screen off), held speech is reported done unspoken so the dialog never hangs; the text is
 *   still shown. The engine check itself keeps going.
 * - Without an offline Turkish voice nothing is spoken (never Turkish through an English voice),
 *   but each utterance is reported done so the dialog continues; the text is shown on screen.
 */
class QueuedSpeaker(private val backend: TtsBackend, private val delay: Delay) : Speaker {

    private data class Held(val id: Int, val text: String)

    private var listener: Speaker.Listener? = null
    private var statusListener: ((VoiceStatus) -> Unit)? = null
    private val held = ArrayDeque<Held>()
    private val inFlight = ArrayDeque<Int>()
    private var startTimedOut = false

    var status: VoiceStatus = VoiceStatus.Checking
        private set

    private val callbacks = object : TtsBackend.Callbacks {
        override fun onStatus(status: VoiceStatus) = onBackendStatus(status)
        override fun onStart(key: String) {
            idOf(key)?.takeIf { it in inFlight }?.let {
                SafeLog.event(TAG, "started #$it")
                listener?.onSpeechStarted(it)
            }
        }
        override fun onDone(key: String) = finish(key)
        override fun onError(key: String) {
            if (idOf(key) != null) SafeLog.event(TAG, "engine error on an utterance", SafeLog.Level.WARN)
            finish(key)
        }
    }

    /** Starts the engine now (warm-up), before the first sentence is needed. */
    fun start() = backend.start(callbacks)

    override fun setListener(listener: Speaker.Listener?) {
        this.listener = listener
    }

    /** Receives every status change, starting with the current one. */
    fun setStatusListener(listener: ((VoiceStatus) -> Unit)?) {
        statusListener = listener
        listener?.invoke(status)
    }

    fun setRate(rate: SpeechRate) = backend.setRate(rate.factor)

    override fun speak(utteranceId: Int, text: String) {
        when {
            status == VoiceStatus.Checking && !startTimedOut -> hold(utteranceId, text)
            status == VoiceStatus.Checking -> listener?.onSpeechDone(utteranceId)
            status !is VoiceStatus.Ready || text.isBlank() -> listener?.onSpeechDone(utteranceId)
            else -> {
                inFlight += utteranceId
                SafeLog.event(TAG, "queued #$utteranceId (${text.length} chars)")
                SafeLog.content(TAG) { "speak #$utteranceId: $text" }
                backend.say(keyOf(utteranceId), text)
            }
        }
    }

    override fun stop() {
        val cut = held.map { it.id } + inFlight
        held.clear()
        delay.cancel()
        inFlight.clear()
        backend.halt()
        if (cut.isNotEmpty()) SafeLog.event(TAG, "stopped, cut ${cut.size}")
        cut.forEach { listener?.onSpeechInterrupted(it) }
    }

    /** Looks for a Turkish voice again (after the user downloaded one). */
    fun recheck() {
        stop()
        startTimedOut = false
        updateStatus(VoiceStatus.Checking)
        backend.restart()
    }

    fun release() {
        stop()
        statusListener = null
        backend.shutdown()
    }

    private fun hold(id: Int, text: String) {
        held += Held(id, text)
        if (held.size == 1) delay.schedule(HELD_SPEECH_TIMEOUT_MS, ::releaseHeldUnspoken)
    }

    private fun releaseHeldUnspoken() {
        startTimedOut = true
        SafeLog.event(TAG, "engine slow to start; ${held.size} sentences shown as text only", SafeLog.Level.WARN)
        val waiting = held.map { it.id }
        held.clear()
        waiting.forEach { listener?.onSpeechDone(it) }
    }

    private fun onBackendStatus(newStatus: VoiceStatus) {
        delay.cancel()
        startTimedOut = false
        updateStatus(newStatus)
        SafeLog.event(TAG, "voice status: $newStatus")
        val waiting = held.toList()
        held.clear()
        waiting.forEach { speak(it.id, it.text) }
    }

    private fun updateStatus(newStatus: VoiceStatus) {
        status = newStatus
        statusListener?.invoke(newStatus)
    }

    private fun finish(key: String) {
        val id = idOf(key) ?: return
        if (!inFlight.remove(id)) return
        SafeLog.event(TAG, "done #$id")
        listener?.onSpeechDone(id)
    }

    private fun keyOf(id: Int) = "$KEY_PREFIX$id"

    private fun idOf(key: String): Int? = key.removePrefix(KEY_PREFIX).takeIf { key.startsWith(KEY_PREFIX) }?.toIntOrNull()

    companion object {
        /** Longest wait for the engine before a sentence is shown as text only (V-06: >5 s = notice). */
        const val HELD_SPEECH_TIMEOUT_MS: Long = 5_000

        private const val TAG = "Speaker"
        private const val KEY_PREFIX = "u"
    }
}
