package app.ultragrid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object Sharing {
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** Opens Instagram's share flow for [uri], falling back to the system share sheet. */
    fun toInstagram(context: Context, uri: Uri, mimeType: String = "image/jpeg") {
        launch(
            context,
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            },
        )
    }

    /** Shares all [uris] at once, which Instagram turns into a single carousel post. */
    fun carouselToInstagram(context: Context, uris: List<Uri>) {
        launch(
            context,
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            },
        )
    }

    private fun launch(context: Context, send: Intent) {
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent(send).setPackage(INSTAGRAM_PACKAGE))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, "Share"))
        }
    }
}
