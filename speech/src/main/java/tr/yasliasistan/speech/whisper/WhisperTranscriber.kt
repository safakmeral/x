package tr.yasliasistan.speech.whisper

import android.os.SystemClock
import java.io.File

/**
 * Owns one loaded whisper.cpp model and turns in-memory audio into text. Blocking: call from a
 * worker thread. Load/transcribe/unload are serialized; [abort] may be called from any thread.
 * Used by [WhisperEngine] and directly by the measurement tool (G-13).
 */
class WhisperTranscriber {

    class Result(
        /** Recognized text (may be blank); never log it outside SafeLog.content. */
        val text: String,
        val audioMs: Long,
        val decodeMs: Long,
    ) {
        override fun toString() = "Result(<${text.length} chars>, audio=$audioMs ms, decode=$decodeMs ms)"
    }

    @Volatile
    private var handle = 0L

    /** File name of the loaded model, or null. */
    @Volatile
    var loadedModel: String? = null
        private set

    /** How long the last [load] took (diagnostics). */
    @Volatile
    var lastLoadMs = 0L
        private set

    val isLoaded: Boolean get() = handle != 0L

    /** Loads [model] unless it is already loaded; returns false on failure. */
    @Synchronized
    fun load(model: File, flashAttention: Boolean = false): Boolean {
        if (handle != 0L && loadedModel == model.name) return true
        if (!WhisperNative.isAvailable) return false
        unload()
        val started = SystemClock.elapsedRealtime()
        val h = WhisperNative.nativeLoad(model.absolutePath, flashAttention)
        lastLoadMs = SystemClock.elapsedRealtime() - started
        if (h == 0L) return false
        synchronized(handleLock) {
            handle = h
            loadedModel = model.name
        }
        return true
    }

    /** Transcribes 16 kHz mono [samples]; null if no model is loaded, decoding failed or was aborted. */
    @Synchronized
    fun transcribe(samples: FloatArray, options: WhisperOptions): Result? {
        val h = handle
        if (h == 0L) return null
        val threads = if (options.threads > 0) options.threads else DeviceProfile.threadsFor(Runtime.getRuntime().availableProcessors())
        val started = SystemClock.elapsedRealtime()
        val bytes = WhisperNative.nativeTranscribe(h, samples, options.language, threads, options.audioCtx, options.beamSize)
            ?: return null
        val decodeMs = SystemClock.elapsedRealtime() - started
        val text = String(bytes, Charsets.UTF_8).trim()
        bytes.fill(0)
        return Result(text, samples.size * 1000L / WavAudio.WHISPER_RATE, decodeMs)
    }

    /** Asks a running [transcribe] to stop as soon as possible. */
    fun abort() {
        synchronized(handleLock) {
            val h = handle
            if (h != 0L) WhisperNative.nativeAbort(h)
        }
    }

    /** Frees the model memory. Safe to call when nothing is loaded. */
    @Synchronized
    fun unload() {
        val h: Long
        synchronized(handleLock) {
            h = handle
            if (h == 0L) return
            handle = 0L // after this, abort() can no longer reach the freed handle
            loadedModel = null
        }
        WhisperNative.nativeFree(h)
    }

    private val handleLock = Any()

    companion object {
        /** whisper.cpp build flags (e.g. "NEON = 1 | DOTPROD = 1"); empty if unavailable. */
        fun systemInfo(): String = if (WhisperNative.isAvailable) WhisperNative.nativeSystemInfo() else ""

        /** Native library variant in use ("whisper_v82"/"whisper_v80"), or null if Whisper cannot run here. */
        fun nativeVariant(): String? = WhisperNative.loadedLibrary
    }
}
