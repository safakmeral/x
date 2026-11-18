package tr.yasliasistan.core.embedding

import java.io.IOException

/**
 * The vocabulary of a SentencePiece model file (`sentencepiece.bpe.model`, protobuf ModelProto).
 * Only the pieces are read (field 1: piece, score, type); trainer and normalizer settings are
 * skipped because the tokenizer applies the equivalent normalization itself.
 */
class SentencePieceModel(val pieces: List<Piece>) {

    data class Piece(val text: String, val score: Float, val type: Type)

    /** sentencepiece_model.proto SentencePiece.Type values. */
    enum class Type(val code: Int) {
        NORMAL(1), UNKNOWN(2), CONTROL(3), USER_DEFINED(4), UNUSED(5), BYTE(6);

        companion object {
            fun of(code: Int): Type = entries.firstOrNull { it.code == code } ?: NORMAL
        }
    }

    companion object {
        /** Parses the model bytes; throws [IOException] for malformed data. */
        fun parse(bytes: ByteArray): SentencePieceModel {
            val pieces = ArrayList<Piece>(256_000)
            val reader = ProtoReader(bytes, 0, bytes.size)
            while (reader.hasMore()) {
                val (field, wire) = reader.tag()
                if (field == 1 && wire == ProtoReader.LEN) {
                    pieces += readPiece(reader.subReader())
                } else {
                    reader.skip(wire)
                }
            }
            if (pieces.isEmpty()) throw IOException("no pieces in model")
            return SentencePieceModel(pieces)
        }

        private fun readPiece(r: ProtoReader): Piece {
            var text = ""
            var score = 0f
            var type = Type.NORMAL
            while (r.hasMore()) {
                val (field, wire) = r.tag()
                when {
                    field == 1 && wire == ProtoReader.LEN -> text = r.string()
                    field == 2 && wire == ProtoReader.FIXED32 -> score = Float.fromBits(r.fixed32())
                    field == 3 && wire == ProtoReader.VARINT -> type = Type.of(r.varint().toInt())
                    else -> r.skip(wire)
                }
            }
            return Piece(text, score, type)
        }
    }
}

/** Minimal protobuf wire-format reader (only what [SentencePieceModel] needs). */
internal class ProtoReader(private val buf: ByteArray, private var pos: Int, private val end: Int) {

    fun hasMore() = pos < end

    fun tag(): Pair<Int, Int> {
        val key = varint()
        return (key ushr 3).toInt() to (key and 7).toInt()
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = byte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw IOException("varint too long")
    }

    fun fixed32(): Int {
        var v = 0
        for (i in 0 until 4) v = v or (byte() shl (8 * i))
        return v
    }

    fun string(): String {
        val len = length()
        val s = String(buf, pos, len, Charsets.UTF_8)
        pos += len
        return s
    }

    fun subReader(): ProtoReader {
        val len = length()
        val r = ProtoReader(buf, pos, pos + len)
        pos += len
        return r
    }

    fun skip(wire: Int) {
        when (wire) {
            VARINT -> varint()
            FIXED64 -> advance(8)
            LEN -> advance(length())
            FIXED32 -> advance(4)
            else -> throw IOException("unsupported wire type $wire")
        }
    }

    private fun length(): Int {
        val len = varint()
        if (len < 0 || len > end - pos) throw IOException("bad length")
        return len.toInt()
    }

    private fun advance(n: Int) {
        if (n > end - pos) throw IOException("truncated")
        pos += n
    }

    private fun byte(): Int {
        if (pos >= end) throw IOException("truncated")
        return buf[pos++].toInt() and 0xFF
    }

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LEN = 2
        const val FIXED32 = 5
    }
}
