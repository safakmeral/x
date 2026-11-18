package tr.yasliasistan.app.device

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import tr.yasliasistan.app.accessibility.ScreenActions
import tr.yasliasistan.app.session.DeviceResult
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.action.ElementRef
import tr.yasliasistan.core.action.ScreenAction
import tr.yasliasistan.core.action.StepPlanner
import tr.yasliasistan.core.action.TaskOutcome
import tr.yasliasistan.core.action.TaskStep
import tr.yasliasistan.core.clarify.PhoneEntry
import tr.yasliasistan.core.clarify.PhoneNumbers
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.text.TurkishText

/**
 * Demo task (18.11.2026): "WhatsApp'tan Ayşe'ye fotoğraf gönder".
 *
 * 1. The system photo picker opens and the assistant says what to do: the user chooses the photos
 *    (no storage permission; only the chosen photos are shared, for this send only).
 * 2. The photos are handed to WhatsApp's chat with the person (standard share intent; the chat is
 *    chosen by the person's number from the address book; nothing goes through our servers).
 * 3. "Sen yap": the assistant finds WhatsApp's Send button and presses it, with the yellow touch
 *    ring and a spoken step ([tr.yasliasistan.core.action.TaskRunner]). "Dur" stops it.
 *
 * The user already confirmed the send (High risk, readback question).
 */
internal class WhatsAppPhotoTask(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun run(action: Action.SendPhoto, numbers: (ContactName) -> List<PhoneEntry>, done: (DeviceResult) -> Unit) {
        if (action.guide) {
            // "Ben yapayım": the user does every step; the assistant shows the next one (PhotoGuideTask).
            done(DeviceResult.Done("Size adım adım göstereceğim. Her adımda neye basacağınızı sarı halkayla göstereceğim."))
            main.postDelayed({ PhotoGuideTask.start(action.contact.displayName) }, GUIDE_START_DELAY_MS)
            return
        }
        val runner = ScreenActions.runner ?: return done(DeviceResult.Failed("Önce asistanın erişilebilirlik iznini açmanız gerekiyor."))
        val number = PhoneNumbers.pick(numbers(action.contact))
            ?: return done(DeviceResult.Failed("Bu kişinin telefon numarasını rehberde bulamadım."))
        val digits = PhoneNumbers.forWhatsApp(number) ?: return done(DeviceResult.Failed("Bu kişinin numarası WhatsApp için uygun değil."))

        PhotoPick.await { photos ->
            if (photos.isEmpty()) {
                done(DeviceResult.Failed("Fotoğraf seçmediniz. Göndermiyorum."))
            } else {
                share(action, digits, photos, runner, done)
            }
        }
        AssistantVoice.sayNow("Göndermek istediğiniz fotoğraflara dokunun. Sonra alttaki Ekle düğmesine basın.")
        try {
            PhotoPickActivity.start(context)
        } catch (e: ActivityNotFoundException) {
            PhotoPick.deliver(emptyList())
        }
    }

    private fun share(
        action: Action.SendPhoto,
        digits: String,
        photos: List<Uri>,
        runner: tr.yasliasistan.app.accessibility.ScreenActionRunner,
        done: (DeviceResult) -> Unit,
    ) {
        val share = if (photos.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, photos[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photos))
        }
            .setType("image/*")
            .putExtra("jid", "$digits@s.whatsapp.net") // opens this person's chat directly
            .setPackage(action.app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share.clipData = ClipData.newRawUri(null, photos[0]).also { clip -> photos.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        try {
            context.startActivity(share)
        } catch (e: ActivityNotFoundException) {
            SafeLog.event(TAG, "whatsapp share missing", SafeLog.Level.WARN)
            return done(DeviceResult.Failed("WhatsApp'ı açamadım."))
        } catch (e: SecurityException) {
            SafeLog.event(TAG, "whatsapp share refused", SafeLog.Level.WARN)
            return done(DeviceResult.Failed("WhatsApp'ı açamadım."))
        }

        // WhatsApp needs a moment to show the photo preview; then "Sen yap" presses Send.
        val pkg = action.app.packageName
        // "Ben yapayım": the Send button is only shown with a yellow ring; the user presses it.
        val last = if (action.guide) showSendButton(pkg) else sendButton(pkg)
        val steps = listOf(
            TaskStep(StepPlanner.fixed(ScreenAction.Wait(WAIT_FOR_PREVIEW_MS)), explore = null),
            TaskStep(last, inPackage = pkg, explore = null, name = "Gönder düğmesi", verify = false),
        )
        main.postDelayed({
            runner.runTask(steps) { outcome ->
                SafeLog.event(TAG, "photo task $outcome")
                done(
                    when (outcome) {
                        is TaskOutcome.HandedOver ->
                            DeviceResult.Done("Göndermek için sarı halkanın içindeki yeşil Gönder düğmesine basın.")
                        is TaskOutcome.Completed ->
                            DeviceResult.Done(if (photos.size == 1) "Fotoğrafı gönderdim." else "${photos.size} fotoğrafı gönderdim.")
                        is TaskOutcome.Stopped -> DeviceResult.Failed("Durdum. Fotoğraf gönderilmedi.")
                        else -> DeviceResult.Failed(outcome.message ?: "Göndermeyi tamamlayamadım. Gönder düğmesine siz basabilirsiniz.")
                    },
                )
            }
        }, OPEN_DELAY_MS)
    }

    private companion object {
        const val TAG = "WhatsAppPhoto"
        const val OPEN_DELAY_MS = 1_200L
        const val WAIT_FOR_PREVIEW_MS = 1_500L
        const val GUIDE_START_DELAY_MS = 5_000L

        /** Labels of WhatsApp's send button (Turkish and English UI). */
        val SEND_LABELS = setOf("gonder", "send")

        /** WhatsApp's Send button on the photo preview: a clickable, enabled element named "Gönder". */
        fun findSend(screen: ScreenSnapshot, pkg: String) = screen.elements
            .filter { it.packageName == pkg && it.clickable && it.enabled && !it.secret }
            .lastOrNull { e -> e.label?.let { TurkishText.normalize(it) } in SEND_LABELS }

        fun sendButton(pkg: String) = StepPlanner { screen -> findSend(screen, pkg)?.let { ScreenAction.Tap(ElementRef.of(it)) } }

        /** "Ben yapayım": ring around Send, then the task goes to the user. */
        fun showSendButton(pkg: String) = StepPlanner { screen ->
            findSend(screen, pkg)?.let {
                ScreenActions.guide?.showGuide(it.bounds)
                ScreenAction.HandOver(tr.yasliasistan.core.action.HandOverReason.USER_CHOICE)
            }
        }
    }
}
