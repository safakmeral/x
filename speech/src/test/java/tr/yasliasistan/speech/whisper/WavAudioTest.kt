package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioTest {

    private fun wav(rate: Int, channels: Int, samples: ShortArray, bits: Int = 16, extraChunk: Boolean = false): ByteArray {
        val extra = if (extraChunk) 8 + 3 + 1 else 0 // odd-sized chunk + pad byte
        val data = samples.size * 2
        val buf = ByteBuffer.allocate(44 + extra + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + extra + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(bits.toShort())
        if (extraChunk) buf.put("LIST".toByteArray()).putInt(3).put(byteArrayOf(1, 2, 3, 0))
        buf.put("data".toByteArray()).putInt(data)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }

    @Test
    fun parsesMono16k() {
        val pcm = WavAudio.parse(wav(16_000, 1, ShortArray(16_000) { 100 }))
        assertEquals(16_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(1000L, pcm.durationMs)
        val input = WavAudio.toWhisperInput(pcm)
        assertEquals(16_000, input.size)
        assertEquals(100 / 32768f, input[0], 1e-7f)
    }

    @Test
    fun skipsUnknownOddSizedChunks() {
        val pcm = WavAudio.parse(wav(16_000, 1, shortArrayOf(1, 2, 3), extraChunk = true))
        assertEquals(3, pcm.samples.size)
        assertEquals(3.toShort(), pcm.samples[2])
    }

    @Test
    fun stereoIsMixedToMono() {
        val pcm = WavAudio.parse(wav(16_000, 2, shortArrayOf(1000, 3000, -1000, -3000)))
        val input = WavAudio.toWhisperInput(pcm)
        assertEquals(2, input.size)
        assertEquals(2000 / 32768f, input[0], 1e-7f)
        assertEquals(-2000 / 32768f, input[1], 1e-7f)
    }

    @Test
    fun resamples24kTo16k() {
        val pcm = WavAudio.parse(wav(24_000, 1, ShortArray(24_000) { (it % 100).toShort() }))
        assertEquals(16_000, WavAudio.toWhisperInput(pcm).size)
    }

    @Test
    fun linearResampleInterpolates() {
        val out = WavAudio.resample(floatArrayOf(0f, 1f, 0f, 1f), fromRate = 2, toRate = 4)
        assertEquals(8, out.size)
        assertEquals(0.5f, out[1], 1e-6f)
    }

    @Test
    fun rejectsNonPcm16() {
        assertThrows(IllegalArgumentException::class.java) { WavAudio.parse(wav(16_000, 1, ShortArray(4), bits = 8)) }
        assertThrows(IllegalArgumentException::class.java) { WavAudio.parse(ByteArray(10)) }
    }
}
