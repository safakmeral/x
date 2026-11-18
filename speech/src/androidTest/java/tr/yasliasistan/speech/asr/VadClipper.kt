package tr.yasliasistan.speech.asr

import tr.yasliasistan.core.asr.AudioMix
import tr.yasliasistan.speech.whisper.EnergyVad
import tr.yasliasistan.speech.whisper.MicRecorder
import tr.yasliasistan.speech.whisper.SpeechAudioBuffer
import tr.yasliasistan.speech.whisper.WavAudio

/**
 * Runs a clip through the same energy VAD and speech buffer as the live microphone path, frame by
 * frame, and returns the part the live [tr.yasliasistan.speech.whisper.WhisperEngine] would decode.
 * Padding values mirror WhisperEngine (300 ms around speech, at least 1 s, onset frames).
 */
object VadClipper {

    private const val PAD_SAMPLES = WavAudio.WHISPER_RATE * 300 / 1000
    private const val MIN_SAMPLES = WavAudio.WHISPER_RATE

    /** The spoken part, or null when the VAD hears no speech. */
    fun clip(audio: FloatArray): FloatArray? {
        val pcm = AudioMix.toPcm16(audio)
        val vad = EnergyVad()
        val buffer = SpeechAudioBuffer(maxSamples = pcm.size.coerceAtLeast(1))
        val frame = ShortArray(MicRecorder.FRAME)
        var pos = 0
        while (pos < pcm.size) {
            val n = minOf(frame.size, pcm.size - pos)
            System.arraycopy(pcm, pos, frame, 0, n)
            buffer.append(frame, n, vad.process(frame, n))
            pos += n
        }
        val onset = EnergyVad.Config().onsetFrames * MicRecorder.FRAME
        val clip = buffer.speechClip(padSamples = PAD_SAMPLES, minSamples = MIN_SAMPLES, onsetSamples = onset)
        buffer.wipe()
        return clip
    }
}
