package tr.yasliasistan.speech.whisper

import java.io.File

/**
 * JNI entry points of whisper_jni.cpp. Exactly one of the two native builds is loaded, chosen by
 * the CPU features (see [DeviceProfile]). If neither loads (x86 emulator, broken install) Whisper
 * is reported as unavailable instead of crashing.
 *
 * Not thread-safe per handle: [WhisperTranscriber] serializes calls; only [nativeAbort] may be
 * called from another thread.
 */
internal object WhisperNative {

    /** Name of the loaded library, or null if Whisper cannot run on this device. */
    val loadedLibrary: String? by lazy { loadBest() }

    val isAvailable: Boolean get() = loadedLibrary != null

    private fun loadBest(): String? {
        val preferred = runCatching { DeviceProfile.libraryFor(DeviceProfile.cpuFeatures(File("/proc/cpuinfo").readText())) }
            .getOrDefault(DeviceProfile.BASE_LIBRARY)
        val order = listOf(preferred, DeviceProfile.BASE_LIBRARY).distinct()
        for (name in order) {
            try {
                System.loadLibrary(name)
                return name
            } catch (_: UnsatisfiedLinkError) {
                // try the next one
            } catch (_: SecurityException) {
                // try the next one
            }
        }
        return null
    }

    /** Returns a handle (0 = failed). */
    external fun nativeLoad(modelPath: String, flashAttn: Boolean): Long

    /** UTF-8 text, or null on failure/abort. */
    external fun nativeTranscribe(
        handle: Long,
        samples: FloatArray,
        language: String,
        threads: Int,
        audioCtx: Int,
        beamSize: Int,
    ): ByteArray?

    external fun nativeAbort(handle: Long)

    external fun nativeFree(handle: Long)

    /** CPU features whisper.cpp was built with (no user data). */
    external fun nativeSystemInfo(): String
}
