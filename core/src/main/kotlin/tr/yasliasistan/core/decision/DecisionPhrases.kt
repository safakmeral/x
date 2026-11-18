package tr.yasliasistan.core.decision

import tr.yasliasistan.core.risk.Risk
import tr.yasliasistan.core.risk.RiskAssessor

/**
 * Sentences of the decision engine. `:core` has no Android resources: the app implements this
 * with strings.xml. Intended Turkish wording is in each comment (NFG-05: short, one question).
 */
interface DecisionPhrases {
    /** "%1$s uygulamasını açıyorum." (low risk: tell and do, FG-71). */
    fun openingApp(label: String): String

    /** "%1$s uygulamasını açayım mı?" (readback question, FG-07). */
    fun confirmOpenApp(label: String): String

    /** "Hangisini açayım? WhatsApp mı, WhatsApp Business mı?" (FG-101: options read by name). */
    fun chooseApp(labels: List<String>): String

    /** "Birden çok uygulama buldum. Adını tam söyler misiniz?" (too many options to read out). */
    fun tooManyMatches(): String

    /** "Bunu henüz yapamıyorum." */
    fun cannotDoYet(): String

    /** "Tamam, yapmıyorum." (the user rejected the offered options). */
    fun cancelled(): String
}

/**
 * Risk until the rule-based classifier of PLAN G-25 exists. Opening an app or a settings screen,
 * changing the volume and showing our permission screen are undone with Back/Home or the volume
 * keys, so they are Low (FG-71). Calling and messaging are Medium (Table 3, FG-72), even though
 * only the dialer / compose screen is opened and the user still presses call / send.
 */
object DefaultRiskAssessor : RiskAssessor {
    override fun assess(action: Action): Risk = when (action) {
        is Action.OpenApp, is Action.AdjustVolume, is Action.OpenSystemScreen,
        Action.RequestContactsPermission -> Risk.LOW
        is Action.CallContact, is Action.MessageContact -> Risk.MEDIUM
        // "Sen yap": the assistant itself presses Send (High); "Ben yapayım": the user presses it (Medium).
        is Action.SendPhoto -> if (action.guide) Risk.MEDIUM else Risk.HIGH
    }
}
