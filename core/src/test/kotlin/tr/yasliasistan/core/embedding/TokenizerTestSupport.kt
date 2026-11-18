package tr.yasliasistan.core.embedding

import java.io.ByteArrayOutputStream
import java.io.File

/** Builds tiny SentencePiece model files in protobuf form, and finds the real one if downloaded. */
object TokenizerTestSupport {

    /** Downloaded by tools/gomme-model-indir.ps1 (git-ignored, 5 MB, MIT). Tests run from core/. */
    val realModel = File("../models/gomme/sentencepiece.bpe.model")

    fun model(vararg pieces: Triple<String, Float, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        // An unrelated field before the pieces (trainer_spec, field 2) must be skipped.
        writeLen(out, 2, byteArrayOf(0x08, 0x01))
        for ((text, score, type) in pieces) {
            val p = ByteArrayOutputStream()
            writeLen(p, 1, text.toByteArray(Charsets.UTF_8))
            p.write((2 shl 3) or 5)
            val bits = score.toRawBits()
            for (i in 0 until 4) p.write((bits ushr (8 * i)) and 0xFF)
            if (type != 1) {
                p.write((3 shl 3) or 0)
                p.write(type)
            }
            writeLen(out, 1, p.toByteArray())
        }
        return out.toByteArray()
    }

    private fun writeLen(out: ByteArrayOutputStream, field: Int, bytes: ByteArray) {
        out.write((field shl 3) or 2)
        var len = bytes.size
        while (len >= 0x80) {
            out.write((len and 0x7F) or 0x80)
            len = len ushr 7
        }
        out.write(len)
        out.write(bytes)
    }
}
