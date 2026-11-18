package tr.yasliasistan.app.cloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tr.yasliasistan.app.privacy.AppConsents
import tr.yasliasistan.core.cloud.AuditCopyStore
import tr.yasliasistan.core.cloud.CloudClient
import tr.yasliasistan.core.cloud.CloudIndicator
import tr.yasliasistan.speech.net.HttpGateway
import java.io.File

/**
 * The app's single cloud client (G-34), initialised in [tr.yasliasistan.app.AssistantApp.onCreate].
 * - [client]: consent gate = [AppConsents.cloudGate]; address/code from [settings]; transport over
 *   `HttpGateway`; masked audit copies in `noBackupFilesDir/cloud-audit` (7 days, V-15).
 * - [busy]: true while a request travels; the session shows it on the bubble (NFG-58).
 * Wiring the language-model layer into the decision engine is G-38's job
 * (see [tr.yasliasistan.core.llm.LanguageModelLayer], phrases: [CloudResourceTexts]).
 */
object AppCloud {

    lateinit var settings: CloudSettingsStore
        private set
    lateinit var auditCopies: AuditCopyStore
        private set
    lateinit var client: CloudClient
        private set

    private val mutableBusy = MutableStateFlow(false)
    private val main = Handler(Looper.getMainLooper())

    /** Cloud request in flight, updated on the main thread. */
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    fun init(context: Context) {
        if (::client.isInitialized) return
        val app = context.applicationContext
        settings = CloudSettingsStore(app)
        auditCopies = AuditCopyStore(File(app.noBackupFilesDir, AUDIT_DIR))
        client = CloudClient(
            gate = AppConsents.cloudGate,
            config = settings,
            transport = GatewayCloudTransport(HttpGateway()),
            audit = auditCopies,
            indicator = CloudIndicator { on -> main.post { mutableBusy.value = on } },
        )
        // Old copies go even if no new request is ever made (V-15).
        Thread({ auditCopies.prune() }, "cloud-audit-prune").start()
    }

    /** "Tüm verilerimi sil": the saved proxy address and access code are removed too. */
    fun forgetSetup() {
        if (::settings.isInitialized) settings.clear()
    }

    private const val AUDIT_DIR = "cloud-audit"
}
