package tr.yasliasistan.speech.tts

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import tr.yasliasistan.core.log.SafeLog
import java.io.File
import java.util.Locale

/**
 * [TtsBackend] on Android's [TextToSpeech].
 *
 * Installed engines are checked one by one (Google, then the phone's default, then other
 * preinstalled engines; other third-party engines are never used) and
 * the app picks the engine for itself; the phone's settings are not changed. Private system
 * engines (e.g. Samsung's on the A15) cannot be bound by apps and are skipped. Only installed, network-free Turkish voices are used, so text is
 * synthesised on the device. The chosen engine is warmed up with a fixed word so the first real
 * sentence starts quickly.
 */
class AndroidTtsBackend(context: Context) : TtsBackend {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var callbacks: TtsBackend.Callbacks? = null
    private var active: TextToSpeech? = null
    private val probing = mutableListOf<TextToSpeech>()
    private val probed = mutableMapOf<String, TextToSpeech>()
    private var rate = SpeechRate.NORMAL.factor

    // Bumped on restart/shutdown so results of an older check are ignored.
    private var generation = 0

    override fun start(callbacks: TtsBackend.Callbacks) {
        this.callbacks = callbacks
        checkEngines()
    }

    override fun restart() {
        releaseEngines()
        checkEngines()
    }

    override fun say(key: String, text: String) {
        val tts = active
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, offlineParams(), key) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) main.post { callbacks?.onError(key) }
    }

    override fun halt() {
        active?.stop()
    }

    override fun setRate(factor: Float) {
        rate = factor
        active?.setSpeechRate(factor)
    }

    override fun shutdown() {
        releaseEngines()
        callbacks = null
    }

    private fun releaseEngines() {
        generation++
        (probing + listOfNotNull(active)).forEach { it.shutdown() }
        probing.clear()
        probed.clear()
        active = null
    }

    private fun checkEngines() {
        val gen = ++generation
        val startedAt = SystemClock.elapsedRealtime()
        val defaultPackage = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        // Google first: it is the usual fallback when an engine cannot be bound, so its voice
        // list must be known before others are judged (see VoiceSelector.dropFallbacks).
        val engines = installedEngines(defaultPackage).sortedBy {
            when (it) {
                VoiceSelector.GOOGLE_ENGINE -> 0
                defaultPackage -> 1
                else -> 2
            }
        }
        probeNext(gen, engines, defaultPackage, emptyList(), startedAt)
    }

    /**
     * Checks engines one at a time. Stops early once a ready engine is found and the phone's
     * default engine is not still waiting to be checked (the default would be preferred).
     */
    private fun probeNext(gen: Int, remaining: List<String>, defaultPackage: String?, probes: List<EngineProbe>, startedAt: Long) {
        if (gen != generation) return
        val next = remaining.firstOrNull() ?: return finish(gen, probes, startedAt)
        val rest = remaining.drop(1)
        openEngine(next) { tts, ok ->
            val result = if (ok) probe(tts, next, isDefault = next == defaultPackage) else null
            val all = probes + listOfNotNull(result)
            val readyFound = all.any(VoiceSelector::isReady)
            if (readyFound && defaultPackage !in rest) {
                finish(gen, all, startedAt)
            } else {
                probeNext(gen, rest, defaultPackage, all, startedAt)
            }
        } ?: probeNext(gen, rest, defaultPackage, probes, startedAt)
    }

    /** Installed engines the app may use (Google, the phone default, preinstalled ones; UD-1 Ö-5). */
    private fun installedEngines(defaultPackage: String?): List<String> =
        runCatching {
            appContext.packageManager
                .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .filter {
                    val isSystem = (it.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    VoiceSelector.isTrustedEngine(it.serviceInfo.packageName, defaultPackage, isSystem)
                }
                .map { it.serviceInfo.packageName }
                .distinct()
        }.getOrDefault(emptyList())

    /** Asks the engine never to synthesise over the network (the chosen voice is offline anyway). */
    private fun offlineParams() = Bundle().apply {
        putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
        putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "0")
    }

    /** Creates an engine; [onInit] runs on the main thread unless a newer check started. */
    private fun openEngine(enginePackage: String, onInit: (TextToSpeech, Boolean) -> Unit): TextToSpeech? {
        val gen = generation
        var created: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            main.post {
                val tts = created ?: return@post
                if (gen == generation) onInit(tts, status == TextToSpeech.SUCCESS)
            }
        }
        created = runCatching { TextToSpeech(appContext, listener, enginePackage) }.getOrNull()
        created?.let { probing += it }
        return created
    }

    private fun probe(tts: TextToSpeech, enginePackage: String, isDefault: Boolean): EngineProbe {
        probed[enginePackage] = tts
        val language = when (runCatching { tts.isLanguageAvailable(TURKISH) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE ->
                LanguageSupport.INSTALLED
            TextToSpeech.LANG_MISSING_DATA -> LanguageSupport.MISSING_DATA
            else -> LanguageSupport.NOT_SUPPORTED
        }
        val voices = turkishVoices(tts).map {
            VoiceInfo(
                name = it.name,
                language = VoiceSelector.TURKISH,
                networkRequired = it.isNetworkConnectionRequired,
                installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty(),
                quality = it.quality,
            )
        }
        val allVoicesKey = runCatching { tts.voices.orEmpty().map { it.name }.sorted().joinToString(",") }.getOrDefault("")
        return EngineProbe(enginePackage, isDefault, language, voices, allVoicesKey).also {
            SafeLog.event(TAG, "engine $enginePackage: $language, ${voices.count { v -> v.installed && !v.networkRequired }} offline tr voices of ${voices.size}")
        }
    }

    private fun finish(gen: Int, probes: List<EngineProbe>, startedAt: Long) {
        if (gen != generation) return
        val status = VoiceSelector.choose(probes)
        val ready = status as? VoiceStatus.Ready
        val candidate = ready?.let { probed[it.enginePackage] }
        // The exact offline voice must be set; otherwise the engine could pick any voice (UD-1 Ö-5).
        val chosen = candidate?.takeIf { configure(it, ready) }
        probing.filter { it !== chosen }.forEach { it.shutdown() }
        probing.clear()
        probed.clear()
        active = chosen
        SafeLog.event(TAG, "voice check took ${SystemClock.elapsedRealtime() - startedAt} ms")
        val reported = when {
            ready == null -> status
            candidate == null -> VoiceStatus.InitFailed
            chosen == null -> VoiceStatus.MissingData(ready.enginePackage) // no speech, text only
            else -> status
        }
        callbacks?.onStatus(reported)
    }

    /** Sets the chosen offline voice; false (engine not used) if it cannot be set exactly. */
    private fun configure(tts: TextToSpeech, ready: VoiceStatus.Ready): Boolean {
        val voice = turkishVoices(tts).firstOrNull { it.name == ready.voiceName && !it.isNetworkConnectionRequired }
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            SafeLog.event(TAG, "offline voice could not be set; speech off", SafeLog.Level.WARN)
            return false
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setSpeechRate(rate)
        tts.setOnUtteranceProgressListener(ProgressRelay())
        warmUp(tts)
        return true
    }

    /** Synthesises a fixed, non-personal word to a cache file so the voice model is loaded. */
    private fun warmUp(tts: TextToSpeech) {
        val file = File(appContext.cacheDir, WARMUP_FILE)
        tts.synthesizeToFile(WARMUP_TEXT, offlineParams(), file, WARMUP_KEY)
    }

    private fun turkishVoices(tts: TextToSpeech): List<Voice> =
        runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale?.language == VoiceSelector.TURKISH }

    /** Engine callbacks arrive on a binder thread; hand them to the main thread. */
    private inner class ProgressRelay : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            main.post { callbacks?.onStart(utteranceId) }
        }

        override fun onDone(utteranceId: String) {
            if (utteranceId == WARMUP_KEY) {
                File(appContext.cacheDir, WARMUP_FILE).delete()
                SafeLog.event(TAG, "warm-up done")
                return
            }
            main.post { callbacks?.onDone(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post { callbacks?.onError(utteranceId) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            SafeLog.event(TAG, "engine error code $errorCode", SafeLog.Level.WARN)
            main.post { callbacks?.onError(utteranceId) }
        }

        // Interrupted utterances are reported by QueuedSpeaker.stop(); nothing to do here.
        override fun onStop(utteranceId: String, interrupted: Boolean) = Unit
    }

    private companion object {
        const val TAG = "Tts"
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
        const val WARMUP_KEY = "warmup"
        const val WARMUP_TEXT = "Merhaba"
        const val WARMUP_FILE = "tts_warmup.wav"
    }
}
