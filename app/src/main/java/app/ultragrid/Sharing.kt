package app.ultragrid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object Sharing {
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** Opens Instagram's share flow for [uri], falling back to the system share sheet. */
    fun toInstagram(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent(send).setPackage(INSTAGRAM_PACKAGE))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, "Share tile"))
        }
    }
}
