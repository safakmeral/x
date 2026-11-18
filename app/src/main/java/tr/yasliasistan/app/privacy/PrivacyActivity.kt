package tr.yasliasistan.app.privacy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.BodyText
import tr.yasliasistan.app.ui.SecondaryButton
import tr.yasliasistan.app.ui.theme.AssistantTheme
import tr.yasliasistan.core.consent.ConsentStatus

/** "Gizlilik ve izinler": short notice + separate consents, and the long notice (G-30). */
class PrivacyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                val status by AppConsents.status.collectAsState()
                var showDetails by rememberSaveable { mutableStateOf(false) }
                if (showDetails) {
                    BackHandler { showDetails = false }
                    PrivacyDetailsScreen(onBack = { showDetails = false })
                } else {
                    ConsentScreen(
                        status = status,
                        decidedAt = AppConsents::decidedAt,
                        onAccept = AppConsents::grant,
                        onRefuse = AppConsents::refuse,
                        onDetails = { showDetails = true },
                        onDone = ::finish,
                        onDeleteAll = {
                            DeleteAllData.run {
                                runOnUiThread {
                                    android.widget.Toast.makeText(this, R.string.data_delete_done, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Home-screen entry; says so while a decision is still missing or outdated. */
@Composable
fun PrivacyEntry() {
    val context = LocalContext.current
    val status by AppConsents.status.collectAsState()
    val pending = status.values.any { it == ConsentStatus.NOT_ASKED || it == ConsentStatus.OUTDATED }
    if (pending) BodyText(stringResource(R.string.privacy_home_pending))
    SecondaryButton(stringResource(R.string.privacy_home_button)) {
        context.startActivity(Intent(context, PrivacyActivity::class.java))
    }
}
