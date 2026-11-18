package tr.yasliasistan.app.listen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import tr.yasliasistan.app.R

/** Turkish text for each notice (strings.xml). */
@StringRes
fun VoiceNotice.textRes(): Int = when (this) {
    VoiceNotice.NEEDS_MIC_PERMISSION -> R.string.voice_notice_needs_mic
    VoiceNotice.LANGUAGE_PACK_MISSING -> R.string.voice_notice_pack_missing
    VoiceNotice.LANGUAGE_DOWNLOADING -> R.string.voice_notice_pack_downloading
    VoiceNotice.OFFLINE_NOT_SUPPORTED -> R.string.voice_notice_not_supported
    VoiceNotice.NOTHING_HEARD -> R.string.voice_notice_nothing_heard
    VoiceNotice.MIC_BUSY -> R.string.voice_notice_busy
    VoiceNotice.MIC_BLOCKED -> R.string.voice_notice_blocked
    VoiceNotice.FAILED -> R.string.voice_notice_failed
}

fun Context.hasMicPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
