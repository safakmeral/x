package tr.yasliasistan.speech.whisper

/**
 * In-memory audio of one listening turn (16 kHz mono PCM16). Never written to a file (NFG-49).
 *
 * Remembers where speech started and ended so only the spoken part (plus a little padding) is
 * sent to Whisper: less work, and no silence for Whisper to "hallucinate" text into.
 * [wipe] overwrites the samples with zeros when the turn is over.
 */
class SpeechAudioBuffer(
    private val maxSamples: Int,
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
) {
    private var data = ShortArray(minOf(initialCapacity, maxSamples).coerceAtLeast(1))

    /** Number of samples stored. */
    var size = 0
        private set

    private var firstSpeech = -1
    private var lastSpeechEnd = -1

    val isFull: Boolean get() = size >= maxSamples
    val hasSpeech: Boolean get() = firstSpeech >= 0

    /** Appends [length] samples; [isSpeech] marks them as voice. Extra samples beyond the cap are dropped. */
    fun append(samples: ShortArray, length: Int = samples.size, isSpeech: Boolean) {
        val n = minOf(length, maxSamples - size)
        if (n <= 0) return
        ensureCapacity(size + n)
        System.arraycopy(samples, 0, data, size, n)
        if (isSpeech) {
            if (firstSpeech < 0) firstSpeech = size
            lastSpeechEnd = size + n
        }
        size += n
    }

    /**
     * The spoken part as floats in [-1, 1], with [padSamples] of context on both sides, padded with
     * trailing silence to at least [minSamples]. Null when no speech was detected.
     * [onsetSamples] extends the start backwards, because the detector confirms speech a few frames late.
     */
    fun speechClip(padSamples: Int, minSamples: Int, onsetSamples: Int = 0): FloatArray? {
        if (firstSpeech < 0) return null
        val from = (firstSpeech - onsetSamples - padSamples).coerceAtLeast(0)
        val to = (lastSpeechEnd + padSamples).coerceAtMost(size)
        val length = to - from
        val out = FloatArray(maxOf(length, minSamples))
        for (i in 0 until length) out[i] = data[from + i] / SCALE
        return out
    }

    /** Zeroes all stored audio and forgets speech marks. */
    fun wipe() {
        data.fill(0)
        size = 0
        firstSpeech = -1
        lastSpeechEnd = -1
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= data.size) return
        var newSize = data.size
        while (newSize < needed) newSize = minOf(maxSamples, newSize * 2)
        val grown = data.copyOf(newSize)
        data.fill(0) // do not leave a stale copy of the audio behind
        data = grown
    }

    companion object {
        private const val SCALE = 32768f
        const val DEFAULT_INITIAL_CAPACITY = 16_000 * 10
    }
}
