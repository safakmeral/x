package tr.yasliasistan.speech.whisper

/**
 * A Whisper model the app may download (V-21, G-15). [sha256] and [bytes] come from the official
 * repository's file metadata at [REVISION] (huggingface.co/ggerganov/whisper.cpp, MIT); the
 * download is accepted only if both match.
 */
data class WhisperModelSpec(
    val fileName: String,
    val bytes: Long,
    val sha256: String,
) {
    /** Pinned to one repository revision so the file behind the URL cannot change. */
    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/${WhisperModelCatalog.REVISION}/$fileName"

    /** Size in whole megabytes, for the plain-Turkish "about N MB" message. */
    val megabytes: Int get() = ((bytes + 500_000) / 1_000_000).toInt()
}

object WhisperModelCatalog {
    /** Commit of huggingface.co/ggerganov/whisper.cpp checked on 17.11.2026. */
    const val REVISION = "5359861c739e955e79d9a303bcbc70fb988958b1"

    val TINY = WhisperModelSpec(
        fileName = WhisperModels.TINY,
        bytes = 77_691_713L,
        sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
    )

    val BASE = WhisperModelSpec(
        fileName = WhisperModels.BASE,
        bytes = 147_951_465L,
        sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
    )
}
