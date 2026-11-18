# Release build rules.

# KVKK: remove every SafeLog.content(...) call from release code, so transcripts, contacts
# and screen text can never reach logcat even if the runtime flag were wrong.
-assumenosideeffects class tr.yasliasistan.core.log.SafeLog {
    public final void content(java.lang.String, kotlin.jvm.functions.Function0);
}

# Verbose/debug android.util.Log calls are also dropped in release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
