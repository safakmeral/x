package tr.yasliasistan.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tr.yasliasistan.app.R
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.app.voice.VoiceSetupNavigator
import tr.yasliasistan.speech.tts.SpeechRate
import tr.yasliasistan.speech.tts.VoiceStatus

/**
 * Voice status, speed choice and a try button (G-08). If no Turkish voice exists, explains in
 * plain words what to do and opens the right screen; the app never reads Turkish with a
 * foreign voice.
 */
@Composable
fun VoiceSection() {
    val context = LocalContext.current
    val navigator = remember { VoiceSetupNavigator(context) }
    val status by AssistantVoice.status.collectAsState()
    val rate by AssistantVoice.rate.collectAsState()
    val caption by AssistantVoice.caption.collectAsState()

    SectionTitle(stringResource(R.string.tts_title))
    when (status) {
        VoiceStatus.Checking -> BodyText(stringResource(R.string.tts_checking))
        is VoiceStatus.Ready -> BodyText(stringResource(R.string.tts_ready))
        is VoiceStatus.MissingData -> BodyText(stringResource(R.string.tts_missing))
        VoiceStatus.NotSupported -> BodyText(stringResource(R.string.tts_not_supported))
        VoiceStatus.InitFailed -> BodyText(stringResource(R.string.tts_init_failed))
    }
    if (status !is VoiceStatus.Ready && status != VoiceStatus.Checking) {
        val unavailable = stringResource(R.string.tts_settings_unavailable)
        val label = if (status is VoiceStatus.MissingData) R.string.tts_missing_button else R.string.tts_settings_button
        PrimaryButton(stringResource(label)) {
            if (!navigator.openFix(status)) Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
        }
    }
    if (status is VoiceStatus.Ready) {
        RateButton(R.string.tts_rate_normal, selected = rate == SpeechRate.NORMAL) { AssistantVoice.setRate(SpeechRate.NORMAL) }
        RateButton(R.string.tts_rate_slow, selected = rate == SpeechRate.SLOW) { AssistantVoice.setRate(SpeechRate.SLOW) }
        val sample = stringResource(R.string.tts_try_text)
        SecondaryButton(stringResource(R.string.tts_try)) { AssistantVoice.sayNow(sample) }
    }
    caption?.let { CaptionBox(it) }
}

@Composable
private fun RateButton(label: Int, selected: Boolean, onClick: () -> Unit) {
    val text = stringResource(label) + if (selected) stringResource(R.string.tts_rate_selected_suffix) else ""
    if (selected) PrimaryButton(text, onClick) else SecondaryButton(text, onClick)
}

/** On-screen copy of what is being said (NFG-04), for when the app itself is in front. */
@Composable
fun CaptionBox(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
