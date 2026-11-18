package tr.yasliasistan.app.listen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.BodyText
import tr.yasliasistan.app.ui.PageTitle
import tr.yasliasistan.app.ui.PrimaryButton
import tr.yasliasistan.app.ui.ScreenColumn
import tr.yasliasistan.app.ui.SecondaryButton
import tr.yasliasistan.app.ui.SectionTitle
import tr.yasliasistan.speech.recognition.DownloadRequest
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.recognition.EngineChoice
import tr.yasliasistan.speech.recognition.EngineKind
import tr.yasliasistan.speech.recognition.SpeechOutcome
import tr.yasliasistan.speech.whisper.WhisperModelSpec

data class ListenSetupUiState(
    val mic: MicAdvice = MicAdvice.ASK,
    /** null while checking. */
    val availability: EngineAvailability? = null,
    val download: DownloadRequest? = null,
    val tryOut: TryOut = TryOut.Idle,
    /** G-15: engine chosen by EnginePolicy; null until the first check. */
    val engine: EngineChoice? = null,
    val model: ModelDownloadUi = ModelDownloadUi.Idle,
    /** No Wi-Fi now: the model download would use mobile data. */
    val onMobileData: Boolean = false,
)

sealed interface TryOut {
    data object Idle : TryOut
    data object Opening : TryOut
    data class Listening(val partial: String) : TryOut
    data class Done(val outcome: SpeechOutcome) : TryOut
}

class ListenSetupActions(
    val askMic: () -> Unit,
    val openAppSettings: () -> Unit,
    val download: () -> Unit,
    val openVoiceSettings: () -> Unit,
    val recheck: () -> Unit,
    val tryStart: () -> Unit,
    val tryFinish: () -> Unit,
    val tryCancel: () -> Unit,
    val close: () -> Unit,
    val downloadModel: () -> Unit,
    val cancelModel: () -> Unit,
    val openTyping: () -> Unit,
)

@Composable
fun ListenSetupScreen(state: ListenSetupUiState, actions: ListenSetupActions) {
    val language = LanguageAdvice.from(state.availability)
    val whisper = state.engine?.takeIf { it.kind == EngineKind.WHISPER }?.whisperModel
    ScreenColumn {
        PageTitle(stringResource(R.string.voice_setup_title))
        BodyText(stringResource(R.string.voice_setup_privacy))
        state.engine?.let { BodyText(engineText(it)) }
        MicSection(state.mic, actions)
        if (whisper != null) {
            ModelSection(ModelAdvice.from(state.availability, state.model), whisper, state, actions)
        } else {
            LanguageSection(language, state.download, actions)
        }
        if (canTryOut(state.mic, language)) TryOutSection(state.tryOut, actions)
        SecondaryButton(stringResource(R.string.voice_close), actions.close)
    }
}

@Composable
private fun MicSection(mic: MicAdvice, actions: ListenSetupActions) {
    SectionTitle(stringResource(R.string.voice_mic_title))
    when (mic) {
        MicAdvice.GRANTED -> BodyText(stringResource(R.string.voice_mic_granted))
        MicAdvice.ASK -> {
            BodyText(stringResource(R.string.voice_mic_why))
            PrimaryButton(stringResource(R.string.voice_mic_ask), actions.askMic)
        }
        MicAdvice.OPEN_APP_SETTINGS -> {
            BodyText(stringResource(R.string.voice_mic_why))
            BodyText(stringResource(R.string.voice_mic_denied))
            PrimaryButton(stringResource(R.string.voice_open_app_settings), actions.openAppSettings)
        }
    }
}

@Composable
private fun LanguageSection(language: LanguageAdvice, download: DownloadRequest?, actions: ListenSetupActions) {
    SectionTitle(stringResource(R.string.voice_lang_title))
    BodyText(stringResource(language.textRes()))
    download?.let { BodyText(stringResource(it.textRes())) }
    if (language.offerDownload) PrimaryButton(stringResource(R.string.voice_download), actions.download)
    if (language.offerSettings) {
        BodyText(stringResource(R.string.voice_manual_steps))
        SecondaryButton(stringResource(R.string.voice_open_voice_settings), actions.openVoiceSettings)
    }
    if (language.offerRecheck) SecondaryButton(stringResource(R.string.voice_recheck), actions.recheck)
}

@Composable
private fun engineText(engine: EngineChoice): String = when (val model = engine.whisperModel) {
    null -> stringResource(R.string.voice_engine_system)
    else -> stringResource(R.string.voice_engine_whisper, model.fileName.removePrefix("ggml-").removeSuffix(".bin"))
}

/** G-15: one-time Whisper model download; the button that names the size is the consent. */
@Composable
private fun ModelSection(advice: ModelAdvice, model: WhisperModelSpec, state: ListenSetupUiState, actions: ListenSetupActions) {
    SectionTitle(stringResource(R.string.voice_model_title))
    when (advice) {
        ModelAdvice.CHECKING -> BodyText(stringResource(R.string.voice_model_checking))
        ModelAdvice.READY -> BodyText(
            stringResource(if (state.model is ModelDownloadUi.Ended) R.string.voice_model_done else R.string.voice_model_ready),
        )
        ModelAdvice.OFFER_DOWNLOAD -> BodyText(stringResource(R.string.voice_model_needed, model.megabytes))
        ModelAdvice.DOWNLOADING -> {
            val percent = (state.model as? ModelDownloadUi.Running)?.percent ?: 0
            BodyText(stringResource(R.string.voice_model_downloading, percent))
            SecondaryButton(stringResource(R.string.voice_model_cancel), actions.cancelModel)
        }
        ModelAdvice.FAILED_NETWORK -> BodyText(stringResource(R.string.voice_model_failed_network))
        ModelAdvice.FAILED_CHECKSUM -> BodyText(stringResource(R.string.voice_model_failed_checksum))
        ModelAdvice.FAILED_SPACE -> BodyText(stringResource(R.string.voice_model_failed_space))
        ModelAdvice.UNAVAILABLE -> BodyText(stringResource(R.string.voice_model_unavailable))
    }
    if (advice.offerDownload) {
        BodyText(stringResource(if (state.onMobileData) R.string.voice_model_mobile_data else R.string.voice_model_wifi_advice))
        PrimaryButton(stringResource(R.string.voice_model_download, model.megabytes), actions.downloadModel)
    }
    if (advice.offerTyping) SecondaryButton(stringResource(R.string.type_button), actions.openTyping)
}

@Composable
private fun TryOutSection(tryOut: TryOut, actions: ListenSetupActions) {
    SectionTitle(stringResource(R.string.voice_try_title))
    when (tryOut) {
        TryOut.Opening, is TryOut.Listening -> {
            BodyText(stringResource(R.string.voice_try_listening))
            if (tryOut is TryOut.Listening && tryOut.partial.isNotEmpty()) BodyText(tryOut.partial)
            PrimaryButton(stringResource(R.string.voice_try_done), actions.tryFinish)
            SecondaryButton(stringResource(R.string.stop_label), actions.tryCancel)
        }
        TryOut.Idle, is TryOut.Done -> {
            BodyText(stringResource(R.string.voice_try_hint))
            (tryOut as? TryOut.Done)?.let { BodyText(outcomeText(it.outcome)) }
            PrimaryButton(stringResource(R.string.voice_try_start), actions.tryStart)
        }
    }
}

@Composable
private fun outcomeText(outcome: SpeechOutcome): String = when (outcome) {
    is SpeechOutcome.Heard -> stringResource(R.string.voice_try_heard, outcome.text)
    SpeechOutcome.NothingHeard -> stringResource(R.string.voice_notice_nothing_heard)
    SpeechOutcome.Cancelled -> ""
    is SpeechOutcome.Failed -> stringResource(VoiceInputCoordinator.noticeForFailure(outcome.reason).textRes())
}

private fun LanguageAdvice.textRes(): Int = when (this) {
    LanguageAdvice.CHECKING -> R.string.voice_lang_checking
    LanguageAdvice.READY -> R.string.voice_lang_ready
    LanguageAdvice.UNKNOWN -> R.string.voice_lang_unknown
    LanguageAdvice.DOWNLOAD_NEEDED -> R.string.voice_lang_missing
    LanguageAdvice.DOWNLOADING -> R.string.voice_lang_downloading
    LanguageAdvice.NOT_SUPPORTED -> R.string.voice_lang_not_supported
    LanguageAdvice.ANDROID_TOO_OLD -> R.string.voice_lang_too_old
}

private fun DownloadRequest.textRes(): Int = when (this) {
    DownloadRequest.DONE -> R.string.voice_download_done
    DownloadRequest.STARTED -> R.string.voice_download_started
    DownloadRequest.SCHEDULED -> R.string.voice_download_scheduled
    DownloadRequest.NOT_POSSIBLE -> R.string.voice_download_not_possible
    DownloadRequest.FAILED -> R.string.voice_download_failed
}
