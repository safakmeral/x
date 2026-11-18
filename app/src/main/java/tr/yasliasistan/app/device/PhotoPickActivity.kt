package tr.yasliasistan.app.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Demo (18.11.2026): opens the system photo picker so the user chooses the photos to send
 * ("Ben seçerim"). The picker needs no storage permission: only the chosen photos are shared with
 * this app, and only for this send. Transparent; closes as soon as the user is done.
 */
class PhotoPickActivity : ComponentActivity() {

    private val pick = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        PhotoPick.deliver(uris)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    companion object {
        const val MAX_PHOTOS = 10

        fun start(context: Context) {
            context.startActivity(
                Intent(context, PhotoPickActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}

/** Hands the picked photos (memory only) back to the waiting task; empty = the user cancelled. */
internal object PhotoPick {
    @Volatile
    private var pending: ((List<Uri>) -> Unit)? = null

    fun await(onPicked: (List<Uri>) -> Unit) {
        pending = onPicked
    }

    fun deliver(uris: List<Uri>) {
        val callback = pending ?: return
        pending = null
        callback(uris)
    }
}
