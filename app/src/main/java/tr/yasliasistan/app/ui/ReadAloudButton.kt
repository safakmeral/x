package tr.yasliasistan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.speech.tts.VoiceStatus

/**
 * Reads the given page texts aloud with the app's own voice (G-05 disclosure, G-08).
 * Shown only when a Turkish voice is ready; reading stops when the page is left.
 */
@Composable
fun ReadAloudButton(textIds: List<Int>) {
    val context = LocalContext.current
    ReadAloudTextButton { textIds.joinToString(separator = "\n") { context.getString(it) } }
}

/** Same as [ReadAloudButton] for texts that are already formatted (e.g. with arguments). */
@Composable
fun ReadAloudTextButton(text: () -> String) {
    val status by AssistantVoice.status.collectAsState()
    DisposableEffect(Unit) { onDispose { AssistantVoice.stop() } }
    if (status !is VoiceStatus.Ready) return
    SecondaryButton(stringResource(R.string.read_aloud)) { AssistantVoice.sayNow(text()) }
}
