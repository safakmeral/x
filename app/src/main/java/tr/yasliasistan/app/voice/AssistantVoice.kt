package tr.yasliasistan.app.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tr.yasliasistan.speech.tts.AndroidTtsBackend
import tr.yasliasistan.speech.tts.MainThreadDelay
import tr.yasliasistan.speech.tts.QueuedSpeaker
import tr.yasliasistan.speech.tts.SpeechRate
import tr.yasliasistan.speech.tts.Speaker
import tr.yasliasistan.speech.tts.VoiceStatus

/**
 * The app's single voice (G-08). Started from [android.app.Application.onCreate] so the engine is
 * warm before the first sentence. [speaker] is what the dialog wiring (G-11) uses; every sentence
 * it says also appears in [caption] (shown next to the bubble and on screen). Main thread only.
 */
object AssistantVoice {

    private var queued: QueuedSpeaker? = null
    private lateinit var captioned: CaptionedSpeaker
    private lateinit var rateStore: SpeechRateStore

    private val mutableStatus = MutableStateFlow<VoiceStatus>(VoiceStatus.Checking)
    val status: StateFlow<VoiceStatus> = mutableStatus.asStateFlow()

    private val mutableCaption = MutableStateFlow<String?>(null)

    /** Text of what is being (or was just) said; null when nothing should be shown. */
    val caption: StateFlow<String?> = mutableCaption.asStateFlow()

    private val mutableRate = MutableStateFlow(SpeechRate.NORMAL)
    val rate: StateFlow<SpeechRate> = mutableRate.asStateFlow()

    // The app's own sentences (try button, read-aloud) use negative ids so they never match
    // dialog utterance ids.
    private var lastOwnId = 0

    val speaker: Speaker get() = captioned

    fun init(context: Context) {
        if (queued != null) return
        rateStore = SpeechRateStore(context)
        val speaker = QueuedSpeaker(AndroidTtsBackend(context), MainThreadDelay())
        mutableRate.value = rateStore.load()
        speaker.setRate(mutableRate.value)
        speaker.setStatusListener { mutableStatus.value = it }
        captioned = CaptionedSpeaker(speaker, MainThreadDelay(), show = { mutableCaption.value = it })
        queued = speaker
        speaker.start()
    }

    fun setRate(rate: SpeechRate) {
        mutableRate.value = rate
        rateStore.save(rate)
        queued?.setRate(rate)
    }

    /** Looks for a Turkish voice again, e.g. when the user comes back from downloading one. */
    fun recheckIfNotReady() {
        val current = mutableStatus.value
        if (current !is VoiceStatus.Ready && current != VoiceStatus.Checking) queued?.recheck()
    }

    /** Says [text] now, replacing whatever was being said. */
    fun sayNow(text: String) {
        captioned.stop()
        captioned.speak(--lastOwnId, text)
    }

    /** Says [text] after whatever is being said (G-22 step narration). Main thread. */
    fun sayNext(text: String) {
        captioned.speak(--lastOwnId, text)
    }

    fun stop() = captioned.stop()
}
