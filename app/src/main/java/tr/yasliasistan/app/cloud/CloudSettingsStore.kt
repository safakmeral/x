package tr.yasliasistan.app.cloud

import android.content.Context
import tr.yasliasistan.core.cloud.AccessCode
import tr.yasliasistan.core.cloud.CloudConfig
import tr.yasliasistan.core.cloud.CloudConfigSource
import tr.yasliasistan.core.cloud.ProxyAddress
import tr.yasliasistan.speech.net.NetworkAllowlist

/** Result of saving what was typed on the cloud setup screen. */
enum class CloudSaveResult { SAVED, BAD_ADDRESS, BAD_CODE }

/**
 * Proxy address and access code (G-34, docs/KURULUM-SON.md §2.10) in app-private preferences,
 * excluded from backup and device transfer (data_extraction_rules.xml). The access code is never
 * shown again after saving and never logged. The proxy host is put on (or taken off) the network
 * allowlist here, so nothing else can open a connection to it.
 */
class CloudSettingsStore(context: Context) : CloudConfigSource {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile
    private var cached: CloudConfig? = load()

    init {
        NetworkAllowlist.setProxyHost(cached?.address?.host)
    }

    override fun current(): CloudConfig? = cached

    /** The saved address for display (the code is never displayed). */
    fun savedAddress(): String? = cached?.address?.let { "https://${it.host}" }

    fun save(addressInput: String, codeInput: String): CloudSaveResult {
        val address = ProxyAddress.parse(addressInput) ?: return CloudSaveResult.BAD_ADDRESS
        val code = AccessCode.parse(codeInput) ?: return CloudSaveResult.BAD_CODE
        prefs.edit().putString(KEY_HOST, address.host).putString(KEY_CODE, code.value).commit()
        cached = CloudConfig(address, code)
        NetworkAllowlist.setProxyHost(address.host)
        return CloudSaveResult.SAVED
    }

    /** Turns the cloud off on this phone (also used by "Tüm verilerimi sil", G-50). */
    fun clear() {
        prefs.edit().clear().commit()
        cached = null
        NetworkAllowlist.setProxyHost(null)
    }

    private fun load(): CloudConfig? {
        val address = prefs.getString(KEY_HOST, null)?.let { ProxyAddress.parse("https://$it") } ?: return null
        val code = prefs.getString(KEY_CODE, null)?.let(AccessCode::parse) ?: return null
        return CloudConfig(address, code)
    }

    private companion object {
        const val FILE = "cloud"
        const val KEY_HOST = "proxy_host"
        const val KEY_CODE = "access_code"
    }
}
