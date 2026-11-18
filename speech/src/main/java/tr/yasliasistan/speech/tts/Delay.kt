package tr.yasliasistan.speech.tts

import android.os.Handler
import android.os.Looper

/** A single cancellable delayed action on the main thread; faked in unit tests. */
interface Delay {
    /** Replaces any action scheduled earlier. */
    fun schedule(delayMs: Long, action: () -> Unit)
    fun cancel()
}

/** [Delay] on the main looper. */
class MainThreadDelay : Delay {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun schedule(delayMs: Long, action: () -> Unit) {
        cancel()
        pending = Runnable { pending = null; action() }.also { handler.postDelayed(it, delayMs) }
    }

    override fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}
