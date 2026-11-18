package tr.yasliasistan.app.typing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.PageTitle
import tr.yasliasistan.app.ui.PrimaryButton
import tr.yasliasistan.app.ui.ScreenColumn
import tr.yasliasistan.app.ui.SecondaryButton
import tr.yasliasistan.app.ui.theme.AssistantTheme

/**
 * "Yazarak söyle" (FG-04): the command is typed instead of spoken, e.g. when recognition fails.
 * The text goes straight to the running service through [TypedCommands]; it is not stored or logged.
 * Leaving the screen without sending counts as "Vazgeç".
 */
class TypeCommandActivity : ComponentActivity() {

    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                TypeCommandScreen(onSend = ::send, onCancel = ::cancel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) cancel()
    }

    private fun send(text: String) {
        if (answered) return
        answered = true
        TypedCommands.submit(text.trim())
        finish()
    }

    private fun cancel() {
        if (answered) return
        answered = true
        TypedCommands.cancel()
        finish()
    }
}

@Composable
private fun TypeCommandScreen(onSend: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    ScreenColumn {
        PageTitle(stringResource(R.string.type_title))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            textStyle = MaterialTheme.typography.headlineSmall,
            placeholder = { Text(stringResource(R.string.type_hint), style = MaterialTheme.typography.bodyLarge) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text) }),
        )
        PrimaryButton(stringResource(R.string.type_send)) { if (text.isNotBlank()) onSend(text) }
        SecondaryButton(stringResource(R.string.type_cancel), onCancel)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
