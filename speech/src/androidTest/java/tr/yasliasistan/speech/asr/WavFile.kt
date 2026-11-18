package tr.yasliasistan.speech.asr

import tr.yasliasistan.speech.whisper.WavAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16 kHz mono 16-bit WAV encoder (for exporting synthesized test clips only). */
object WavFile {

    fun encode(samples: ShortArray, rate: Int = WavAudio.WHISPER_RATE): ByteArray {
        val dataBytes = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataBytes)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }
}
