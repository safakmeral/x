package tr.yasliasistan.core

/**
 * Single place for tunable defaults approved on 17.11.2026 (docs/PLAN.md section 2).
 * Changing a value here changes the behaviour everywhere; keep the V-xx reference next to it.
 */
object Defaults {
    /** V-07, V-12: how long a confirmation question waits; silence after this means "no". */
    const val CONFIRM_TIMEOUT_MS: Long = 10_000

    /** V-06 / NFG-23: if a wait exceeds this, the user hears a short progress notice. */
    const val PROGRESS_NOTICE_AFTER_MS: Long = 5_000

    /** Repeat interval of the progress notice while the wait continues (see docs/SAPMALAR.md). */
    const val PROGRESS_REPEAT_MS: Long = 10_000

    /** V-12: end of speech after this much silence while listening. */
    const val LISTEN_SILENCE_MS: Long = 3_500

    /** V-12: hard upper limit of a single listening turn. */
    const val LISTEN_MAX_MS: Long = 60_000

    /**
     * G-10 (see docs/SAPMALAR.md): at most this many options are read out in a "which one?"
     * question; with more, the user is asked to say the full name.
     */
    const val MAX_SPOKEN_OPTIONS: Int = 3

    /**
     * G-39 (see docs/SAPMALAR.md): the short-term conversation context ("onu da ara", "tekrar",
     * "neden?") is forgotten this long after the last command. RAM only, never stored.
     */
    const val CONTEXT_TTL_MS: Long = 120_000

    /**
     * V-17 / FG-27: most screen actions one "Sen yap" task may do (search scrolls count too);
     * beyond this the assistant stops and hands the task to the user.
     */
    const val STEP_BUDGET: Int = 10

    /** G-23 / FG-28: most scrolls while searching for one element that is not visible (see SAPMALAR). */
    const val EXPLORE_MAX_SCROLLS: Int = 5

    /** V-15 / NFG-59: metric records older than this are deleted automatically. */
    const val METRICS_RETENTION_DAYS: Int = 30

    /** V-15 / FG-91: masked audit copies of cloud requests are kept this long on the device. */
    const val CLOUD_AUDIT_RETENTION_DAYS: Int = 7

    /** G-34 (speed principle): connecting to the proxy must be quick, or we fall back at once. */
    const val CLOUD_CONNECT_TIMEOUT_MS: Int = 5_000

    /** G-34: the proxy's whole model chain stops at 15 s (proxy/src/config.js); a small margin on top. */
    const val CLOUD_READ_TIMEOUT_MS: Int = 17_000

    /**
     * G-34 (see docs/SAPMALAR.md): after the proxy says every free quota is used up, the assistant
     * stays on the device this long without calling it again (no repeated 15 s waits).
     */
    const val CLOUD_QUOTA_PAUSE_MS: Long = 15 * 60_000L
}
