package tr.yasliasistan.app.embedding

import android.content.Context
import java.io.File

/** One file pinned to an exact repository revision; accepted only if size and SHA-256 match. */
data class PinnedFile(val fileName: String, val url: String, val bytes: Long, val sha256: String) {
    val megabytes: Int get() = ((bytes + 500_000) / 1_000_000).toInt()
}

/**
 * The task-memory embedding model (V-03, TK-05): intfloat/multilingual-e5-small (MIT), int8 ONNX
 * from the official repository. 118 MB is above the 60 MB "ship in the APK" line used for Whisper
 * (V-21), so it is downloaded once from huggingface.co (already on NetworkAllowlist, G-15) into the
 * app-private, non-backed-up folder. Sizes and hashes: Hugging Face file metadata, 17.11.2026.
 */
object EmbeddingModelFiles {
    /** Commit of huggingface.co/intfloat/multilingual-e5-small checked on 17.11.2026. */
    const val REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
    const val FOLDER = "gomme"
    private const val BASE = "https://huggingface.co/intfloat/multilingual-e5-small/resolve/$REVISION/onnx/"

    /** Dynamic int8 quantization published by the model authors (MatMulInteger; runs on ARM CPUs). */
    val MODEL = PinnedFile(
        fileName = "model_qint8_avx512_vnni.onnx",
        url = BASE + "model_qint8_avx512_vnni.onnx",
        bytes = 118_346_824L,
        sha256 = "dd476dd0c2514e9b9be83aeb3853fac0763e0bdf4a71645407587d77c48a2d88",
    )

    /** SentencePiece vocabulary read by XlmRobertaTokenizer (no tokenizer library needed). */
    val TOKENIZER = PinnedFile(
        fileName = "sentencepiece.bpe.model",
        url = BASE + "sentencepiece.bpe.model",
        bytes = 5_069_051L,
        sha256 = "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865",
    )

    val ALL = listOf(MODEL, TOKENIZER)

    /** App-private folder that is excluded from backups (noBackupFilesDir). */
    fun dir(context: Context): File = File(context.noBackupFilesDir, FOLDER)

    /** Installed means present with the official size (hash is checked once, when downloaded). */
    fun isInstalled(dir: File): Boolean = ALL.all { File(dir, it.fileName).let { f -> f.isFile && f.length() == it.bytes } }
}
