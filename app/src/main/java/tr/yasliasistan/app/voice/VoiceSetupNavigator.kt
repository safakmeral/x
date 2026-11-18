package tr.yasliasistan.app.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.speech.tts.VoiceStatus

/**
 * Opens the screen where the user can get a Turkish voice. The app never changes phone settings
 * or installs anything itself; it only opens the right screen.
 */
class VoiceSetupNavigator(private val context: Context) {

    /** Returns false if no suitable screen could be opened. */
    fun openFix(status: VoiceStatus): Boolean {
        val candidates = buildList {
            if (status is VoiceStatus.MissingData) {
                add(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(status.enginePackage))
            }
            add(Intent(TTS_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        return candidates.any(::tryStart)
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SafeLog.event(TAG, "opened ${intent.action}")
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val TAG = "VoiceSetup"
        const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    }
}
