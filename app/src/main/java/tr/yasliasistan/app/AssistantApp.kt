package tr.yasliasistan.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import tr.yasliasistan.app.cloud.AppCloud
import tr.yasliasistan.app.metrics.AppMetrics
import tr.yasliasistan.app.privacy.AppConsents
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.log.SafeLog

class AssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Content logging (may contain personal data) is allowed only in debug builds.
        SafeLog.install(sink = LogcatSink, allowContent = BuildConfig.DEBUG)
        // G-18: content-free metric log, kept on the device for 30 days.
        AppMetrics.init(this)
        // G-30: consent records (cloud gate for G-34); everything is "no" until the user decides.
        AppConsents.init(this)
        // G-34: cloud client (off until the proxy address/code are entered and consent is given).
        AppCloud.init(this)
        // G-08: start the voice engine now so the first sentence is not delayed by engine start-up.
        AssistantVoice.init(this)
        registerActivityLifecycleCallbacks(VoiceRecheckOnResume)
    }

    /** After the user returns from downloading a Turkish voice, look for it again. */
    private object VoiceRecheckOnResume : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = AssistantVoice.recheckIfNotReady()
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private object LogcatSink : SafeLog.Sink {
        override fun write(level: SafeLog.Level, tag: String, message: String) {
            when (level) {
                SafeLog.Level.DEBUG -> Log.d(tag, message)
                SafeLog.Level.INFO -> Log.i(tag, message)
                SafeLog.Level.WARN -> Log.w(tag, message)
                SafeLog.Level.ERROR -> Log.e(tag, message)
            }
        }
    }
}
