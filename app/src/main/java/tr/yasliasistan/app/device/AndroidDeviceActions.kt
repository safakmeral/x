package tr.yasliasistan.app.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import tr.yasliasistan.app.R
import tr.yasliasistan.app.contacts.ContactsPermissionActivity
import tr.yasliasistan.app.contacts.hasContactsPermission
import tr.yasliasistan.app.session.DeviceActions
import tr.yasliasistan.app.session.DeviceResult
import tr.yasliasistan.core.clarify.PhoneEntry
import tr.yasliasistan.core.clarify.PhoneNumbers
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.MessageChannel
import tr.yasliasistan.core.decision.VolumeChange
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.masking.ContactName

/**
 * Android side of the G-37 actions, all through standard intents that need no new permission
 * (NFG-68): ACTION_DIAL only fills in the number (the user presses call), ACTION_SENDTO / the
 * WhatsApp chat link only open a new message (the user sends it). The number is read from the
 * address book for the chosen person at this moment only, kept in memory and never logged.
 */
class AndroidDeviceActions(context: Context) : DeviceActions {
    private val context = context.applicationContext

    override fun run(action: Action): DeviceResult = when (action) {
        is Action.CallContact -> withNumber(action.contact) { number ->
            open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), R.string.device_dialer_opened)
        }
        is Action.MessageContact -> withNumber(action.contact) { number -> message(number, action.channel) }
        is Action.AdjustVolume -> volume(action.change)
        is Action.OpenSystemScreen -> open(Intent(Settings.ACTION_DISPLAY_SETTINGS), R.string.device_screen_opened)
        Action.RequestContactsPermission -> {
            ContactsPermissionActivity.start(context)
            DeviceResult.Done(context.getString(R.string.device_permission_opened))
        }
        is Action.OpenApp -> DeviceResult.Failed(null) // handled by SessionPorts.launch
        is Action.SendPhoto -> DeviceResult.Failed(null) // multi-step: runAsync
    }

    override fun runAsync(action: Action, done: (DeviceResult) -> Unit) {
        if (action is Action.SendPhoto) WhatsAppPhotoTask(context).run(action, ::phoneEntries, done) else done(run(action))
    }

    private fun message(number: String, channel: MessageChannel): DeviceResult = when (channel) {
        MessageChannel.Sms -> open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)), R.string.device_message_opened)
        is MessageChannel.WhatsApp -> {
            val digits = PhoneNumbers.forWhatsApp(number)
            if (digits == null) {
                DeviceResult.Failed(null)
            } else {
                val link = Uri.parse("https://api.whatsapp.com/send?phone=$digits")
                open(Intent(Intent.ACTION_VIEW, link).setPackage(channel.app.packageName), R.string.device_message_opened)
            }
        }
    }

    private fun volume(change: VolumeChange): DeviceResult {
        val audio = context.getSystemService(AudioManager::class.java) ?: return DeviceResult.Failed(null)
        val direction = when (change) {
            VolumeChange.UP -> AudioManager.ADJUST_RAISE
            VolumeChange.DOWN -> AudioManager.ADJUST_LOWER
            VolumeChange.SHOW -> AudioManager.ADJUST_SAME
        }
        return try {
            val steps = if (change == VolumeChange.SHOW) 1 else VOLUME_STEPS
            repeat(steps) {
                audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            }
            DeviceResult.Done(context.getString(R.string.device_volume_done))
        } catch (e: SecurityException) {
            // Do Not Disturb can forbid ringer changes.
            SafeLog.event(TAG, "volume change refused", SafeLog.Level.WARN)
            DeviceResult.Failed(null)
        }
    }

    private inline fun withNumber(contact: ContactName, block: (String) -> DeviceResult): DeviceResult {
        val number = PhoneNumbers.pick(phoneEntries(contact)) ?: return DeviceResult.Failed(context.getString(R.string.device_no_number))
        return block(number)
    }

    private fun phoneEntries(contact: ContactName): List<PhoneEntry> {
        if (!hasContactsPermission(context)) return emptyList()
        val projection = arrayOf(Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY)
        return try {
            context.contentResolver.query(Phone.CONTENT_URI, projection, "${Phone.CONTACT_ID} = ?", arrayOf(contact.id), null)
                ?.use { c ->
                    val numberCol = c.getColumnIndexOrThrow(Phone.NUMBER)
                    val typeCol = c.getColumnIndexOrThrow(Phone.TYPE)
                    val primaryCol = c.getColumnIndexOrThrow(Phone.IS_SUPER_PRIMARY)
                    buildList {
                        while (c.moveToNext()) {
                            val number = c.getString(numberCol) ?: continue
                            add(PhoneEntry(number, c.getInt(typeCol) == Phone.TYPE_MOBILE, c.getInt(primaryCol) != 0))
                        }
                    }
                } ?: emptyList()
        } catch (e: SecurityException) {
            SafeLog.event(TAG, "number read refused", SafeLog.Level.WARN)
            emptyList()
        }
    }

    private fun open(intent: Intent, doneText: Int): DeviceResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        DeviceResult.Done(context.getString(doneText))
    } catch (e: ActivityNotFoundException) {
        SafeLog.event(TAG, "no app for device action", SafeLog.Level.WARN)
        DeviceResult.Failed(null)
    } catch (e: SecurityException) {
        SafeLog.event(TAG, "device action not allowed", SafeLog.Level.WARN)
        DeviceResult.Failed(null)
    }

    private companion object {
        const val TAG = "DeviceAction"

        /** Volume keys pressed per "sesi aç / kıs" command; the system panel shows the result. */
        const val VOLUME_STEPS = 2
    }
}
