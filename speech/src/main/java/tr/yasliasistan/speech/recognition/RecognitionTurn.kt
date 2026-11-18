package tr.yasliasistan.speech.recognition

/**
 * One listening turn, independent of the recognizer behind it (reusable by the Whisper engine, G-12).
 *
 * Timing (V-12, FG-05):
 * - no speech at all for [ListenConfig.noSpeechMs] -> microphone closed, [SpeechOutcome.NothingHeard];
 * - once the user talks, [ListenConfig.silenceMs] without new speech ends the turn, so the window
 *   stays open while the user keeps talking;
 * - [ListenConfig.maxMs] is a hard upper limit;
 * - after a stop request the final text is awaited for [ListenConfig.finalResultGraceMs]; if it does
 *   not come, the last partial text is used so the user is not kept waiting, marked
 *   [SpeechOutcome.Heard.partialOnly] so it never acts without a readback question (NFG-18).
 * Exactly one outcome is delivered. Empty or noise-only text never becomes [SpeechOutcome.Heard] (NFG-18).
 */
class RecognitionTurn(
    private val config: ListenConfig,
    private val scheduler: TurnScheduler,
    private val mic: MicControl,
    private val listener: SpeechListener,
) {
    /** The recognizer's controls: [stop] asks for the final result, [abort] discards everything. */
    interface MicControl {
        fun stop()
        fun abort()
    }

    var isFinished = false
        private set

    private var ready = false
    private var stopping = false
    private var lastPartial: String? = null

    private var noSpeechTimer: Cancellable? = null
    private var silenceTimer: Cancellable? = null
    private var maxTimer: Cancellable? = null
    private var graceTimer: Cancellable? = null

    /** The microphone is open. */
    fun onReady() {
        if (isFinished || ready) return
        ready = true
        listener.onListening()
        noSpeechTimer = scheduler.schedule(config.noSpeechMs) { abortWith(SpeechOutcome.NothingHeard) }
        maxTimer = scheduler.schedule(config.maxMs) { requestStop() }
    }

    /** The recognizer detected voice activity. */
    fun onSpeechActivity() {
        if (isFinished || stopping) return
        noSpeechTimer?.cancel()
        noSpeechTimer = null
        silenceTimer?.cancel()
        silenceTimer = scheduler.schedule(config.silenceMs) { requestStop() }
    }

    fun onPartialText(text: String) {
        if (isFinished) return
        val cleaned = text.trim()
        if (cleaned.isEmpty() || cleaned == lastPartial) return
        lastPartial = cleaned
        onSpeechActivity()
        listener.onPartialText(cleaned)
    }

    /** Final text from the recognizer (null or blank if it heard nothing). */
    fun onFinalText(text: String?) {
        val final = TranscriptFilter.usableText(text)
        val outcome = when {
            final != null -> SpeechOutcome.Heard(final)
            // Only an unconfirmed partial is left: marked, so it is read back before anything runs (NFG-18).
            else -> TranscriptFilter.usableText(lastPartial)?.let { SpeechOutcome.Heard(it, partialOnly = true) }
        }
        finish(outcome ?: SpeechOutcome.NothingHeard)
    }

    fun onError(code: Int) {
        if (isFinished) return
        val failure = RecognizerErrors.failureFor(code)
        if (failure != null) {
            finish(SpeechOutcome.Failed(failure))
        } else {
            // "No match" after the user spoke: keep what the partial results already showed.
            onFinalText(null)
        }
    }

    /** Asked by the user or the caller: stop listening and deliver what was heard. */
    fun finishRequested() = requestStop()

    fun cancelRequested() = abortWith(SpeechOutcome.Cancelled)

    private fun requestStop() {
        if (isFinished || stopping) return
        stopping = true
        cancelListeningTimers()
        mic.stop()
        graceTimer = scheduler.schedule(config.finalResultGraceMs) {
            mic.abort()
            onFinalText(null)
        }
    }

    private fun abortWith(outcome: SpeechOutcome) {
        if (isFinished) return
        mic.abort()
        finish(outcome)
    }

    private fun finish(outcome: SpeechOutcome) {
        if (isFinished) return
        isFinished = true
        cancelListeningTimers()
        graceTimer?.cancel()
        graceTimer = null
        listener.onOutcome(outcome)
    }

    private fun cancelListeningTimers() {
        listOf(noSpeechTimer, silenceTimer, maxTimer).forEach { it?.cancel() }
        noSpeechTimer = null
        silenceTimer = null
        maxTimer = null
    }
}
