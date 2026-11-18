package tr.yasliasistan.speech.asr

import android.os.Debug

/**
 * Samples this process's PSS (proportional memory, includes the native Whisper model) on a
 * background thread and keeps the highest value. Debug/measurement only.
 */
class PssSampler(private val intervalMs: Long = 100) {

    @Volatile
    private var peakKb = 0L

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        peakKb = Debug.getPss()
        running = true
        thread = Thread({
            while (running) {
                peakKb = maxOf(peakKb, Debug.getPss())
                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "asr-pss").apply { isDaemon = true; start() }
    }

    /** Stops sampling and returns the peak in MB. */
    fun stopMb(): Int {
        running = false
        thread?.interrupt()
        thread?.join(1_000)
        thread = null
        peakKb = maxOf(peakKb, Debug.getPss())
        return (peakKb / 1024).toInt()
    }
}
