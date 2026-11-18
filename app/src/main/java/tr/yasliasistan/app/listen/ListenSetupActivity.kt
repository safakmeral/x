package tr.yasliasistan.app.listen

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.net.ConnectivityManager
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tr.yasliasistan.app.R
import tr.yasliasistan.app.typing.TypeCommandActivity
import tr.yasliasistan.app.ui.theme.AssistantTheme
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.speech.net.HttpGateway
import tr.yasliasistan.speech.recognition.DownloadRequest
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.recognition.SelectedSpeechEngine
import tr.yasliasistan.speech.whisper.DownloadResult
import tr.yasliasistan.speech.whisper.ModelDownloader
import tr.yasliasistan.speech.whisper.WhisperModelStore
import java.io.File
import tr.yasliasistan.speech.recognition.SpeechListener
import tr.yasliasistan.speech.recognition.SpeechOutcome

/**
 * Explains and fixes what voice input needs (G-07): the microphone permission, asked only after
 * our plain-language reason, and the Turkish OFFLINE model. If the model is missing we offer the
 * system download or the settings screen; we never switch to online recognition (NFG-17).
 * Also lets the user try recognition once; the microphone opens only on that button tap.
 */
class ListenSetupActivity : ComponentActivity() {

    private lateinit var engine: SelectedSpeechEngine
    private val state = mutableStateOf(ListenSetupUiState())
    private var askedForMic = false

    /** G-15: Whisper model download; set to stop it (the part is kept and resumed later). */
    @Volatile
    private var modelCancelled = false
    private val modelDownloader by lazy {
        ModelDownloader(HttpGateway(), File(filesDir, WhisperModelStore.FOLDER))
    }

    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedForMic = true
        SafeLog.event(TAG, "mic permission granted=$granted")
        refreshMic()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedForMic = savedInstanceState?.getBoolean(KEY_ASKED) ?: false
        engine = SelectedSpeechEngine(this)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                ListenSetupScreen(
                    state = state.value,
                    actions = ListenSetupActions(
                        askMic = { micRequest.launch(Manifest.permission.RECORD_AUDIO) },
                        openAppSettings = ::openAppSettings,
                        download = ::requestDownload,
                        openVoiceSettings = ::openVoiceSettings,
                        recheck = ::checkLanguage,
                        tryStart = ::startTryOut,
                        tryFinish = engine::finishListening,
                        tryCancel = engine::cancel,
                        close = ::finish,
                        downloadModel = ::downloadModel,
                        cancelModel = { modelCancelled = true },
                        openTyping = { open(Intent(this, TypeCommandActivity::class.java)) },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may come back from Settings with the permission or the pack changed.
        refreshMic()
        checkLanguage()
    }

    override fun onStop() {
        super.onStop()
        engine.cancel() // never keep the microphone open behind another screen
    }

    override fun onDestroy() {
        modelCancelled = true
        engine.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ASKED, askedForMic)
    }

    private fun refreshMic() {
        val advice = MicAdvice.from(
            granted = hasMicPermission(),
            askedBefore = askedForMic,
            rationaleAllowed = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
        )
        state.value = state.value.copy(mic = advice)
    }

    private fun checkLanguage() {
        state.value = state.value.copy(availability = null, onMobileData = onMobileData())
        engine.checkAvailability { result: EngineAvailability ->
            state.value = state.value.copy(availability = result, engine = engine.choice)
        }
    }

    /**
     * G-15 (V-21): downloads the chosen Whisper model after the user tapped the button that states
     * its size. Only the model file comes in; nothing about the user or phone is sent (HttpGateway).
     */
    private fun downloadModel() {
        val model = engine.choice.whisperModel ?: return
        if (state.value.model is ModelDownloadUi.Running) return
        modelCancelled = false
        state.value = state.value.copy(model = ModelDownloadUi.Running(modelDownloader.partialBytes(model), model.bytes))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                modelDownloader.download(
                    model,
                    onProgress = { done, total ->
                        runOnUiThread { state.value = state.value.copy(model = ModelDownloadUi.Running(done, total)) }
                    },
                    cancelled = { modelCancelled },
                )
            }
            SafeLog.event(TAG, "model download: $result")
            state.value = state.value.copy(model = ModelDownloadUi.Ended(result))
            announce(result)
            checkLanguage()
        }
    }

    /** Plain spoken + written feedback (the screen shows the same text). */
    private fun announce(result: DownloadResult) {
        val text = when (result) {
            DownloadResult.DONE -> R.string.voice_model_done
            DownloadResult.NETWORK -> R.string.voice_model_failed_network
            DownloadResult.CHECKSUM -> R.string.voice_model_failed_checksum
            DownloadResult.NO_SPACE -> R.string.voice_model_failed_space
            DownloadResult.CANCELLED -> return
        }
        AssistantVoice.sayNow(getString(text))
    }

    /** True when the active connection costs data (no Wi-Fi); said before the download. */
    private fun onMobileData(): Boolean =
        getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

    private fun requestDownload() {
        engine.requestLanguageDownload { result: DownloadRequest ->
            state.value = state.value.copy(download = result)
            if (result == DownloadRequest.DONE) checkLanguage()
        }
    }

    private fun startTryOut() {
        state.value = state.value.copy(tryOut = TryOut.Opening)
        engine.startListening(object : SpeechListener {
            override fun onListening() {
                state.value = state.value.copy(tryOut = TryOut.Listening(""))
            }

            override fun onPartialText(text: String) {
                state.value = state.value.copy(tryOut = TryOut.Listening(text))
            }

            override fun onOutcome(outcome: SpeechOutcome) {
                state.value = state.value.copy(tryOut = TryOut.Done(outcome))
                if (outcome is SpeechOutcome.Failed) refreshMic()
            }
        })
    }

    private fun openAppSettings() = open(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )

    private fun openVoiceSettings() {
        if (!open(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), quiet = true)) open(Intent(Settings.ACTION_SETTINGS))
    }

    private fun open(intent: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        SafeLog.event(TAG, "settings screen missing: ${intent.action}", SafeLog.Level.WARN)
        if (!quiet) Toast.makeText(this, R.string.voice_settings_unavailable, Toast.LENGTH_LONG).show()
        false
    }

    private companion object {
        const val TAG = "VoiceSetup"
        const val KEY_ASKED = "asked_for_mic"
    }
}
