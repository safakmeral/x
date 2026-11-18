package tr.yasliasistan.speech.whisper

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads 16-bit PCM WAV data and converts it to what Whisper expects: 16 kHz mono floats.
 * Used by device tests and the measurement tool (G-13) to feed recorded clips without a microphone.
 */
object WavAudio {

    const val WHISPER_RATE = 16_000

    class Pcm(val sampleRate: Int, val channels: Int, val samples: ShortArray) {
        val frames: Int get() = samples.size / channels
        val durationMs: Long get() = frames * 1000L / sampleRate
    }

    /** Parses a RIFF/WAVE byte array (PCM, 16 bit). Throws [IllegalArgumentException] otherwise. */
    fun parse(bytes: ByteArray): Pcm {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && tag(buf, 0) == "RIFF" && tag(buf, 8) == "WAVE") { "not a WAV file" }
        var pos = 12
        var rate = 0
        var channels = 0
        var bits = 0
        var format = 0
        while (pos + 8 <= bytes.size) {
            val id = tag(buf, pos)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            require(size >= 0 && body + size <= bytes.size || id == "data") { "broken chunk $id" }
            when (id) {
                "fmt " -> {
                    format = buf.getShort(body).toInt()
                    channels = buf.getShort(body + 2).toInt()
                    rate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    require(format == PCM || format == EXTENSIBLE) { "only PCM WAV is supported" }
                    require(bits == 16 && channels in 1..2 && rate > 0) { "need 16-bit mono/stereo PCM" }
                    // Streaming writers may leave the size as 0 or -1: then read to the end.
                    val available = bytes.size - body
                    val length = (if (size <= 0 || size > available) available else size) / 2
                    val samples = ShortArray(length) { buf.getShort(body + it * 2) }
                    return Pcm(rate, channels, samples)
                }
            }
            pos = body + size + (size and 1)
        }
        throw IllegalArgumentException("no data chunk")
    }

    /** Mixes to mono and resamples (linear interpolation) to 16 kHz floats in [-1, 1]. */
    fun toWhisperInput(pcm: Pcm): FloatArray {
        val mono = FloatArray(pcm.frames) { frame ->
            var sum = 0f
            for (c in 0 until pcm.channels) sum += pcm.samples[frame * pcm.channels + c]
            sum / pcm.channels / 32768f
        }
        return resample(mono, pcm.sampleRate, WHISPER_RATE)
    }

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input.copyOf()
        val outLength = (input.size.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        return FloatArray(outLength) { i ->
            val x = i * step
            val left = x.toInt().coerceAtMost(input.size - 1)
            val right = (left + 1).coerceAtMost(input.size - 1)
            val t = (x - left).toFloat()
            input[left] * (1 - t) + input[right] * t
        }
    }

    private fun tag(buf: ByteBuffer, at: Int): String =
        String(CharArray(4) { (buf.get(at + it).toInt() and 0xFF).toChar() })

    private const val PCM = 1
    private const val EXTENSIBLE = 0xFFFE.toShort().toInt()
}
