package app.ultragrid

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollageViewModel(application: Application) : AndroidViewModel(application) {
    var template by mutableStateOf(CollageTemplate.SIDE_BY_SIDE)
        private set
    var ratio by mutableStateOf(TileRatio.PORTRAIT)
        private set
    var gap by mutableFloatStateOf(0.02f)
        private set
    var background by mutableIntStateOf(CollageRenderer.BACKGROUND_WHITE)
        private set
    var filter by mutableStateOf(PhotoFilter.ORIGINAL)
        private set
    var photos by mutableStateOf<Map<Int, Bitmap>>(emptyMap())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val hasPhotos: Boolean
        get() = template.cells.indices.any { it in photos }

    fun updateTemplate(value: CollageTemplate) {
        template = value
    }

    fun updateRatio(value: TileRatio) {
        ratio = value
    }

    fun updateGap(value: Float) {
        gap = value
    }

    fun updateBackground(value: Int) {
        background = value
    }

    fun updateFilter(value: PhotoFilter) {
        filter = value
    }

    fun setPhoto(index: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageLoader.decode(getApplication(), uri, PHOTO_MAX_EDGE)
                }
                photos = photos + (index to bitmap)
            } catch (e: Exception) {
                message = "Couldn't open that image"
            }
        }
    }

    fun clearPhotos() {
        photos = emptyMap()
    }

    /** Renders the collage at full size, saves it and passes the result to [onSaved]. */
    fun save(onSaved: (Uri) -> Unit = {}) {
        if (busy || !hasPhotos) return
        viewModelScope.launch {
            busy = true
            try {
                val uri = withContext(Dispatchers.IO) {
                    val out = Bitmap.createBitmap(ratio.width, ratio.height, Bitmap.Config.ARGB_8888)
                    CollageRenderer.draw(
                        Canvas(out),
                        ratio.width.toFloat(),
                        ratio.height.toFloat(),
                        template,
                        photos,
                        gap,
                        background,
                        filter,
                        showPlaceholders = false,
                    )
                    TileExporter.saveJpeg(getApplication(), out, "UltraGrid_collage_${TileExporter.timestamp()}.jpg")
                        .also { out.recycle() }
                }
                message = "Collage saved to Pictures/${TileExporter.ALBUM}"
                onSaved(uri)
            } catch (e: Exception) {
                message = "Couldn't save: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun clearMessage() {
        message = null
    }

    private companion object {
        const val PHOTO_MAX_EDGE = 2048
    }
}
