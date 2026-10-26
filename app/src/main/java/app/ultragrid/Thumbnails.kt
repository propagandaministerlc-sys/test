package app.ultragrid

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache of downscaled images for the planner grid. */
object Thumbnails {
    private val cache = LruCache<String, ImageBitmap>(90)

    fun peek(uri: Uri, maxEdge: Int): ImageBitmap? = cache.get("$maxEdge|$uri")

    suspend fun load(context: Context, uri: Uri, maxEdge: Int): ImageBitmap? {
        peek(uri, maxEdge)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { ImageLoader.decode(context, uri, maxEdge).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) cache.put("$maxEdge|$uri", bitmap)
        return bitmap
    }
}

@Composable
fun rememberThumbnail(uri: Uri, maxEdge: Int = 480): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val thumbnail by produceState(Thumbnails.peek(uri, maxEdge), uri, maxEdge) {
        if (value == null) value = Thumbnails.load(context, uri, maxEdge)
    }
    return thumbnail
}
