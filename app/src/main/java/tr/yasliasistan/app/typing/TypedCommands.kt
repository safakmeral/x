package tr.yasliasistan.app.typing

/**
 * Hands typed text from [TypeCommandActivity] to the accessibility service in the same process.
 * Nothing is stored: if no service listens, the text is dropped. Main thread only.
 */
object TypedCommands {

    interface Listener {
        fun onTyped(text: String)
        fun onTypingCancelled()
    }

    private var listener: Listener? = null

    fun register(listener: Listener?) {
        this.listener = listener
    }

    fun submit(text: String) {
        listener?.onTyped(text)
    }

    fun cancel() {
        listener?.onTypingCancelled()
    }
}
