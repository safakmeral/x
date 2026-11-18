package tr.yasliasistan.app.listen

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.SecondaryButton

/** Home-screen button to the microphone / Turkish offline pack setup (G-07). */
@Composable
fun ListenSetupEntry() {
    val context = LocalContext.current
    SecondaryButton(stringResource(R.string.home_voice_setup)) {
        context.startActivity(Intent(context, ListenSetupActivity::class.java))
    }
}
