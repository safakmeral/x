package tr.yasliasistan.app.cloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.BodyText
import tr.yasliasistan.app.ui.PageTitle
import tr.yasliasistan.app.ui.PrimaryButton
import tr.yasliasistan.app.ui.ScreenColumn
import tr.yasliasistan.app.ui.SecondaryButton
import tr.yasliasistan.app.ui.theme.AssistantTheme

/**
 * "İnternet yardımı bağlantısı" (G-34): the student enters the proxy address and access code at
 * the end of setup (docs/KURULUM-SON.md §2.10). Until then the cloud is off and the assistant works
 * on the device only. The code is typed hidden and never shown again.
 */
class CloudSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AssistantTheme { CloudSetupScreen(onDone = ::finish) } }
    }
}

@Composable
private fun CloudSetupScreen(onDone: () -> Unit) {
    val settings = AppCloud.settings
    var saved by remember { mutableStateOf(settings.savedAddress()) }
    var address by rememberSaveable { mutableStateOf(saved.orEmpty()) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }

    ScreenColumn {
        PageTitle(stringResource(R.string.cloud_setup_title))
        BodyText(stringResource(R.string.cloud_setup_body))
        BodyText(
            saved?.let { stringResource(R.string.cloud_setup_status_on, it) }
                ?: stringResource(R.string.cloud_setup_status_off),
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.cloud_setup_address)) },
            placeholder = { Text("https://….workers.dev") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.cloud_setup_code)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { BodyText(stringResource(it)) }
        PrimaryButton(stringResource(R.string.cloud_setup_save)) {
            message = when (settings.save(address, code)) {
                CloudSaveResult.SAVED -> {
                    code = ""
                    saved = settings.savedAddress()
                    R.string.cloud_setup_saved
                }
                CloudSaveResult.BAD_ADDRESS -> R.string.cloud_setup_bad_address
                CloudSaveResult.BAD_CODE -> R.string.cloud_setup_bad_code
            }
        }
        if (saved != null) {
            SecondaryButton(stringResource(R.string.cloud_setup_remove)) {
                settings.clear()
                saved = null
                address = ""
                code = ""
                message = R.string.cloud_setup_removed
            }
        }
        SecondaryButton(stringResource(R.string.cloud_setup_back), onDone)
    }
}

/** Home-screen entry: one button to the connection setup. */
@Composable
fun CloudSetupEntry() {
    val context = LocalContext.current
    SecondaryButton(stringResource(R.string.cloud_setup_home_button)) {
        context.startActivity(Intent(context, CloudSetupActivity::class.java))
    }
}
