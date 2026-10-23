package app.ultragrid

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GridViewModel(application: Application) : AndroidViewModel(application) {
    private var source: Bitmap? = null
    private var imageAspect = 1f

    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    var rows by mutableIntStateOf(3)
        private set
    var ratio by mutableStateOf(TileRatio.PROFILE)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var tiles by mutableStateOf<List<SavedTile>>(emptyList())
        private set
    var posted by mutableStateOf<Set<Int>>(emptySet())
        private set

    val frameAspect: Float
        get() = (GRID_COLUMNS * ratio.width).toFloat() / (rows * ratio.height)

    val crop: Crop
        get() = CropMath.crop(imageAspect, frameAspect, zoom, offsetX, offsetY)

    val nextToPost: SavedTile?
        get() = tiles.filter { it.postOrder !in posted }.minByOrNull { it.postOrder }

    fun load(uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val (full, small) = withContext(Dispatchers.IO) {
                    val bitmap = ImageLoader.decode(getApplication(), uri)
                    bitmap to ImageLoader.scaleDown(bitmap, PREVIEW_MAX_EDGE)
                }
                source = full
                imageAspect = full.width.toFloat() / full.height
                preview = small.asImageBitmap()
                tiles = emptyList()
                posted = emptySet()
                resetCrop()
            } catch (e: Exception) {
                message = "Couldn't open that image"
            } finally {
                busy = false
            }
        }
    }

    fun updateRows(count: Int) {
        rows = count.coerceIn(1, MAX_ROWS)
        clampOffsets()
    }

    fun updateRatio(value: TileRatio) {
        ratio = value
        clampOffsets()
    }

    /** [dx]/[dy] are pan distances as fractions of the frame; [zoomChange] is multiplicative. */
    fun transform(dx: Float, dy: Float, zoomChange: Float) {
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        offsetX += dx
        offsetY += dy
        clampOffsets()
    }

    fun resetCrop() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun split() {
        val bitmap = source ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                tiles = TileExporter.export(getApplication(), bitmap, crop, rows, GRID_COLUMNS, ratio)
                posted = emptySet()
                message = "Saved ${tiles.size} tiles to Pictures/${TileExporter.ALBUM}"
            } catch (e: Exception) {
                message = "Couldn't save tiles: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun markPosted(order: Int) {
        posted = posted + order
    }

    fun togglePosted(order: Int) {
        posted = if (order in posted) posted - order else posted + order
    }

    fun backToEditor() {
        tiles = emptyList()
        posted = emptySet()
    }

    fun clearMessage() {
        message = null
    }

    private fun clampOffsets() {
        val (w, h) = CropMath.cropSize(imageAspect, frameAspect, zoom)
        val maxX = CropMath.maxOffset(w)
        val maxY = CropMath.maxOffset(h)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 2048
        const val MAX_ZOOM = 5f
    }
}
