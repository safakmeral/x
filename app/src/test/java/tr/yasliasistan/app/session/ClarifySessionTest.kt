package tr.yasliasistan.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.app.listen.Microphone
import tr.yasliasistan.app.listen.VoiceNotice
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.core.clarify.ClarificationEngine
import tr.yasliasistan.core.clarify.ClarificationRule
import tr.yasliasistan.core.clarify.ClarifyPhrases
import tr.yasliasistan.core.contacts.ContactAccess
import tr.yasliasistan.core.contacts.ContactBook
import tr.yasliasistan.core.contacts.ContactFinder
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionPhrases
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.decision.VolumeChange
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.dialog.Phrase
import tr.yasliasistan.core.dialog.Scheduler
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.metrics.ErrorCode
import tr.yasliasistan.core.metrics.MetricKind
import tr.yasliasistan.core.metrics.MetricRecord
import tr.yasliasistan.speech.tts.Speaker

/** G-37: clarification decisions reach the device actions through the session (made-up names). */
class ClarifySessionTest {

    private val mehmet = ContactName("3", "Mehmet Test")
    private val ran = mutableListOf<Action>()
    private var deviceWorks = true
    private val said = mutableListOf<String>()
    private val metrics = mutableListOf<MetricRecord>()
    private var granted = true

    private val access = object : ContactAccess {
        override fun isGranted() = granted
        override fun book() = ContactBook.of(listOf(mehmet, ContactName("1", "Ayşe Örnek"), ContactName("2", "Ayşe Deneme")))
    }

    private val session = AssistantSession(
        scheduler = { _, _ -> Scheduler.Cancellable { } },
        decisions = DecisionCoordinator(
            DecisionEngine(
                RuleLayer(
                    listOf(
                        ClarificationRule(ClarificationEngine(ContactFinder(access), { emptyList() }, Clarify)),
                        OpenAppRule { emptyList() },
                    ),
                ),
            ),
            Decisions,
        ),
        background = object : Background {
            override fun <T> run(work: () -> T, deliver: (T) -> Unit) = deliver(work())
        },
        speaker = object : Speaker {
            override fun speak(utteranceId: Int, text: String) { said += text }
            override fun stop() = Unit
            override fun setListener(listener: Speaker.Listener?) = Unit
        },
        texts = object : SessionTexts {
            override fun phrase(phrase: Phrase) = phrase.name
            override fun appOpened(label: String) = "$label açıldı."
            override fun notice(notice: VoiceNotice) = notice.name
        },
        ports = object : SessionPorts {
            override fun render(state: OverlayState) = Unit
            override fun launch(app: InstalledApp) = true
            override fun openTyping() = Unit
            override fun openVoiceSetup() = Unit
            override fun prefetchApps() = Unit
        },
        microphoneFactory = {
            object : Microphone {
                override fun startTurn() = Unit
                override fun finishTurn() = false
                override fun stop() = Unit
            }
        },
        clock = { 0L },
        metricsSink = { metrics += it },
        devices = { action ->
            ran += action
            if (deviceWorks) DeviceResult.Done("DONE:${action::class.simpleName}") else DeviceResult.Failed(null)
        },
    )

    @Test
    fun lowRiskSettingRunsAtOnce() {
        session.onTyped("sesi aç")
        assertEquals(listOf<Action>(Action.AdjustVolume(VolumeChange.UP)), ran)
        assertEquals(listOf("VOLUME:true", "DONE:AdjustVolume"), said)
    }

    @Test
    fun callIsReadBackAndRunsOnlyAfterYes() {
        session.onTyped("Mehmet'i ara")
        assertTrue("Medium risk: nothing before yes", ran.isEmpty())
        assertEquals("CALL?Mehmet Test", said.last())
        session.onConfirmTapped(true)
        assertEquals(listOf<Action>(Action.CallContact(mehmet)), ran)
    }

    @Test
    fun choiceQuestionThenAnswerThenYes() {
        session.onTyped("Ayşe'yi ara")
        assertEquals("CHOOSE:Ayşe Örnek|Ayşe Deneme", said.last())
        session.onTyped("ikincisi")
        session.onConfirmTapped(true)
        assertEquals(listOf<Action>(Action.CallContact(ContactName("2", "Ayşe Deneme"))), ran)
    }

    @Test
    fun noPermissionOpensThePermissionScreen() {
        granted = false
        session.onTyped("Mehmet'i ara")
        assertEquals(listOf<Action>(Action.RequestContactsPermission), ran)
    }

    @Test
    fun failedDeviceActionIsAnExecutionError() {
        deviceWorks = false
        session.onTyped("sesi kıs")
        assertEquals(ErrorCode.DEVICE_ACTION_FAILED, metrics.single { it.kind == MetricKind.ERROR }.errorCode)
    }

    private object Decisions : DecisionPhrases {
        override fun openingApp(label: String) = "OPENING:$label"
        override fun confirmOpenApp(label: String) = "OPEN?$label"
        override fun chooseApp(labels: List<String>) = "WHICH:$labels"
        override fun tooManyMatches() = "TOO_MANY_APPS"
        override fun cannotDoYet() = "CANNOT"
        override fun cancelled() = "CANCELLED"
    }

    private object Clarify : ClarifyPhrases {
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
        override fun chooseMessageApp(labels: List<String>) = "WHICH_APP:$labels"
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
}
