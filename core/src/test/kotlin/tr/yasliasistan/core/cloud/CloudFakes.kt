package tr.yasliasistan.core.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import tr.yasliasistan.core.consent.CloudTransferGate
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.masking.AuditResult
import tr.yasliasistan.core.masking.AuditedRequest
import tr.yasliasistan.core.masking.CommandMaskResult
import tr.yasliasistan.core.masking.ContactIndex
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.masking.InputSource
import tr.yasliasistan.core.masking.MaskingSession
import tr.yasliasistan.core.masking.ScreenMaskResult
import tr.yasliasistan.core.masking.ScreenScope
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.LabelSource
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.ScreenWindow
import tr.yasliasistan.core.screen.TextKind
import tr.yasliasistan.core.screen.WindowKind

/** Fake HTTP for the cloud tests: records every request, answers from a script. No real network. */
class FakeTransport(private val answer: (Int) -> TransportResult = { ok(PLAN_RESULT) }) : CloudTransport {
    val requests = mutableListOf<Sent>()

    data class Sent(val url: String, val bearer: String, val body: JsonObject)

    override fun post(url: String, bearer: String, jsonBody: String): TransportResult {
        requests += Sent(url, bearer, Json.parseToJsonElement(jsonBody) as JsonObject)
        return answer(requests.size)
    }

    companion object {
        fun ok(result: String) = TransportResult.Response(
            200,
            """{"v":1,"ok":true,"result":$result,"provider":"gemini","model":"m","attempts":1}""",
        )

        fun error(status: Int, code: String, onDevice: Boolean = false, retryAfter: Long? = null) = TransportResult.Response(
            status,
            """{"v":1,"ok":false,"error":"$code"${if (onDevice) ",\"on_device\":true" else ""}}""",
            retryAfter,
        )

        const val PLAN_RESULT =
            """{"geri_okuma":"Ekran ayarını açayım mı?","netlestirme_sorusu":null,"yapamam":null,"guven":"yuksek",""" +
                """"adimlar":[{"eylem":"tap","oge":2,"yon":null,"uzunluk":null,"metin":null,"uygulama":null,""" +
                """"bekleme_ms":null,"neden":null,"beklenen_durum":"Ekran ayarları açık"}]}"""
    }
}

class SwitchGate(var allowed: Boolean = true) : CloudTransferGate {
    override fun cloudTransferAllowed() = allowed
}

class MutableClock(var now: Long = 1_700_000_000_000L) : () -> Long {
    override fun invoke() = now
}

/** Made-up screens and contacts (no real personal data). */
object CloudFixtures {
    const val SETTINGS = "com.android.settings"
    const val WHATSAPP = "com.whatsapp"
    const val OTHER = "com.example.notes"
    const val CODE = "TestErisim2026"

    val config = CloudConfig(ProxyAddress.parse("https://vekil.ornek.workers.dev")!!, AccessCode.parse(CODE)!!)
    val contacts: ContactIndex = ContactIndex.build(listOf(ContactName("c1", "Ayşe Test")))
    val scope = ScreenScope(allowedPackages = setOf(SETTINGS, WHATSAPP))
    val apps = listOf(
        InstalledApp("Ayarlar", SETTINGS, "$SETTINGS.Settings"),
        InstalledApp("WhatsApp", WHATSAPP, "$WHATSAPP.Main"),
        InstalledApp("Notlarım", OTHER, "$OTHER.Main"),
    )

    fun el(
        id: Int,
        label: String?,
        pkg: String = SETTINGS,
        role: ElementRole = ElementRole.BUTTON,
        kind: TextKind = TextKind.UI_LABEL,
        editable: Boolean = false,
        secret: Boolean = false,
    ) = ScreenElement(
        id = id, role = role, label = label,
        labelSource = if (label == null) LabelSource.NONE else LabelSource.TEXT, textKind = kind,
        clickable = true, longClickable = false, scrollable = false, editable = editable,
        checkable = false, checked = false, enabled = true, selected = false, focused = false,
        heading = false, inListItem = false, secret = secret, unlabeledIcon = false,
        bounds = Bounds(0, id * 100, 1080, id * 100 + 90), windowId = 1, packageName = pkg,
    )

    fun screen(vararg elements: ScreenElement, pkg: String = SETTINGS) = ScreenSnapshot(
        windows = listOf(ScreenWindow(1, WindowKind.APPLICATION, pkg, active = true)),
        elements = elements.toList(),
        truncated = false,
    )

    val settingsHome = screen(el(1, "Bağlantılar"), el(2, "Ekran"), el(3, "Ses"))

    /** A masked, audited request for "0555 000 00 01'i ara" on the settings screen. */
    fun audited(session: MaskingSession = MaskingSession(contacts), command: String = "0555 000 00 01'i ara"): AuditedRequest {
        val cmd = (session.maskCommand(command, InputSource.SPEECH) as CommandMaskResult.Masked).text
        val screen = (session.maskScreen(settingsHome, scope) as ScreenMaskResult.Masked).screen
        return (session.audit(cmd, screen) as AuditResult.Passed).request
    }
}
