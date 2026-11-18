package tr.yasliasistan.app.voice

import android.content.Context
import tr.yasliasistan.speech.tts.SpeechRate

/** Remembers the chosen speaking speed in app-private preferences (NFG-43). */
class SpeechRateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): SpeechRate =
        SpeechRate.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: SpeechRate.NORMAL

    fun save(rate: SpeechRate) {
        prefs.edit().putString(KEY, rate.name).apply()
    }

    private companion object {
        const val FILE = "voice"
        const val KEY = "speech_rate"
    }
}
