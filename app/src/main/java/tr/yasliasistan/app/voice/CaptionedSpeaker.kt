package tr.yasliasistan.app.voice

import tr.yasliasistan.speech.tts.Delay
import tr.yasliasistan.speech.tts.Speaker

/**
 * Wraps a [Speaker] so every spoken sentence is also shown as text (NFG-04, G-08).
 * The caption shows the sentence being said, stays [lingerMs] after the last one so a slow
 * reader can finish it, and disappears at once on [stop] ([stopKeepingText] lets it linger). Main thread only.
 */
class CaptionedSpeaker(
    private val inner: Speaker,
    private val timer: Delay,
    private val show: (String?) -> Unit,
    private val lingerMs: Long = CAPTION_LINGER_MS,
) : Speaker {

    private var outer: Speaker.Listener? = null
    private val queue = ArrayDeque<Pair<Int, String>>()

    init {
        inner.setListener(object : Speaker.Listener {
            override fun onSpeechStarted(utteranceId: Int) {
                outer?.onSpeechStarted(utteranceId)
            }

            override fun onSpeechDone(utteranceId: Int) {
                advance(utteranceId)
                outer?.onSpeechDone(utteranceId)
            }

            override fun onSpeechInterrupted(utteranceId: Int) {
                outer?.onSpeechInterrupted(utteranceId)
            }
        })
    }

    override fun setListener(listener: Speaker.Listener?) {
        outer = listener
    }

    override fun speak(utteranceId: Int, text: String) {
        timer.cancel()
        queue += utteranceId to text
        if (queue.size == 1) show(text)
        inner.speak(utteranceId, text)
    }

    override fun stop() {
        timer.cancel()
        queue.clear()
        show(null)
        inner.stop()
    }

    /** The question was cut short but is still open: its text lingers like a finished sentence. */
    override fun stopKeepingText() {
        timer.cancel()
        queue.clear()
        inner.stop()
        timer.schedule(lingerMs) { show(null) }
    }

    private fun advance(finishedId: Int) {
        if (queue.firstOrNull()?.first != finishedId) return
        queue.removeFirst()
        val next = queue.firstOrNull()
        if (next != null) {
            show(next.second)
        } else {
            timer.schedule(lingerMs) { show(null) }
        }
    }

    companion object {
        /** How long the last sentence stays on screen after speech ends. */
        const val CAPTION_LINGER_MS: Long = 6_000
    }
}
