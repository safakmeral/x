package tr.yasliasistan.core.log

/**
 * The only logging entry point for the project (KVKK).
 *
 * - [event] is for content-free technical messages (state names, durations, error codes).
 *   It must never receive transcripts, contact data or screen text.
 * - [content] is for anything that may contain personal data. The message is built lazily,
 *   only when [contentEnabled] is true. In release builds R8 removes every call to [content]
 *   at compile time (see app/proguard-rules.pro), and the app also keeps [contentEnabled] false.
 */
object SafeLog {

    /** Destination of log lines; the app plugs in android.util.Log, tests plug in a list. */
    fun interface Sink {
        fun write(level: Level, tag: String, message: String)
    }

    enum class Level { DEBUG, INFO, WARN, ERROR }

    @Volatile
    private var sink: Sink? = null

    @Volatile
    var contentEnabled: Boolean = false
        private set

    /** Called once at startup. [allowContent] must be BuildConfig.DEBUG. */
    fun install(sink: Sink?, allowContent: Boolean) {
        this.sink = sink
        this.contentEnabled = allowContent
    }

    fun event(tag: String, message: String, level: Level = Level.INFO) {
        sink?.write(level, tag, message)
    }

    /** Debug-only log line that may contain personal data; never evaluated when disabled. */
    fun content(tag: String, message: () -> String) {
        if (!contentEnabled) return
        sink?.write(Level.DEBUG, tag, message())
    }
}
