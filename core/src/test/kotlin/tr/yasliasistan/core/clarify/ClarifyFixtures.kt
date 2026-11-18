package tr.yasliasistan.core.clarify

import tr.yasliasistan.core.contacts.ContactAccess
import tr.yasliasistan.core.contacts.ContactBook
import tr.yasliasistan.core.contacts.ContactFinder
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionPhrases
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.masking.ContactName

/** Made-up test data and tagged test wording (the app supplies Turkish sentences from strings.xml). */
internal object TestClarifyPhrases : ClarifyPhrases {
    override fun permissionReason() = "REASON"
    override fun noPermission() = "NO_PERMISSION"
    override fun chooseContact(names: List<String>) = "CHOOSE:" + names.joinToString("|")
    override fun tooManyContacts(count: Int) = "TOO_MANY:$count"
    override fun contactNotFound() = "NOT_FOUND"
    override fun confirmCall(name: String) = "CALL?$name"
    override fun confirmCallInexact(name: String) = "CALL_NEAREST?$name"
    override fun callingAnnouncement(name: String) = "CALLING:$name"
    override fun confirmMessage(name: String, app: String) = "MSG?$name@$app"
    override fun confirmMessageInexact(name: String, app: String) = "MSG_NEAREST?$name@$app"
    override fun messageAnnouncement(name: String) = "MESSAGING:$name"
    override fun smsLabel() = "Mesajlar"
    override fun askWhomToCall() = "WHOM_CALL?"
    override fun askWhomToMessage() = "WHOM_MSG?"
    override fun chooseMessageApp(labels: List<String>) = "WHICH_APP:" + labels.joinToString("|")
    override fun appNotInstalled(label: String) = "NOT_INSTALLED:$label"
    override fun stillNotFound() = "STILL_NOT_FOUND"
    override fun openingContactsPermission() = "OPENING_PERMISSION"
    override fun confirmContactsPermission() = "PERMISSION?"
    override fun confirmVolume(up: Boolean?) = "VOLUME?$up"
    override fun confirmDisplaySettings() = "DISPLAY?"
    override fun volumeAnnouncement(up: Boolean?) = "VOLUME:$up"
    override fun brightnessAnnouncement() = "BRIGHTNESS"
    override fun textSizeAnnouncement() = "TEXT_SIZE"
    override fun cancelled() = "CANCELLED"
}

internal object TestDecisionPhrases : DecisionPhrases {
    override fun openingApp(label: String) = "OPENING:$label"
    override fun confirmOpenApp(label: String) = "OPEN?$label"
    override fun chooseApp(labels: List<String>) = "WHICH:" + labels.joinToString("|")
    override fun tooManyMatches() = "TOO_MANY_APPS"
    override fun cannotDoYet() = "CANNOT"
    override fun cancelled() = "CANCELLED"
}

internal class FakeAccess(var granted: Boolean, var book: ContactBook) : ContactAccess {
    override fun isGranted() = granted
    override fun book(): ContactBook = book
}

internal object People {
    val ayseOrnek = ContactName("1", "Ayşe Örnek")
    val ayseDeneme = ContactName("2", "Ayşe Deneme")
    val mehmetTest = ContactName("3", "Mehmet Test")
    val kizim = ContactName("4", "Kızım")
    val husyinDeneme = ContactName("5", "Hüseyin Deneme")
    val zeynepOrnek = ContactName("6", "Zeynep Örnek")

    val all = listOf(ayseOrnek, ayseDeneme, mehmetTest, kizim, husyinDeneme, zeynepOrnek)
}

internal object Apps {
    val settings = InstalledApp("Ayarlar", "com.android.settings", "S")
    val whatsapp = InstalledApp("WhatsApp", "com.whatsapp", "W")
    val business = InstalledApp("WhatsApp Business", "com.whatsapp.w4b", "B")
    val weather = InstalledApp("Hava Durumu", "com.example.weather", "H")
}

/** Rule layer as the app wires it: clarification rules first, then "open app". */
internal class Harness(
    contacts: List<ContactName> = People.all,
    var apps: List<InstalledApp> = listOf(Apps.settings),
    granted: Boolean = true,
) {
    val access = FakeAccess(granted, ContactBook.of(contacts))
    val engine = ClarificationEngine(ContactFinder(access), { apps }, TestClarifyPhrases)
    val coordinator = DecisionCoordinator(
        DecisionEngine(RuleLayer(listOf(ClarificationRule(engine), OpenAppRule { apps }))),
        TestDecisionPhrases,
    )
}
