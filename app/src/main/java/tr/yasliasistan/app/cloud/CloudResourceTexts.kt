package tr.yasliasistan.app.cloud

import android.content.Context
import tr.yasliasistan.app.R
import tr.yasliasistan.core.llm.LlmPhrases

/** Turkish sentences of the language-model layer (G-34/G-35), from strings.xml. */
class CloudResourceTexts(private val context: Context) : LlmPhrases {
    private fun s(id: Int) = context.getString(id)

    override fun noConsent() = s(R.string.cloud_no_consent)
    override fun notConfigured() = s(R.string.cloud_not_configured)
    override fun quotaExhausted() = s(R.string.cloud_quota_exhausted)
    override fun unavailable() = s(R.string.cloud_unavailable)
    override fun unauthorized() = s(R.string.cloud_unauthorized)
    override fun privateData() = s(R.string.cloud_private_data)
    override fun notUnderstood() = s(R.string.cloud_not_understood)
    override fun planNotRunnable() = s(R.string.cloud_plan_not_runnable)
}
