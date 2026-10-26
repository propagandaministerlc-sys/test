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

/** State for the single-photo editors: profile grid and carousel. */
class GridViewModel(application: Application) : AndroidViewModel(application) {
    private var source: Bitmap? = null
    private var imageAspect = 1f

    var mode by mutableStateOf(SplitMode.GRID)
        private set
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Rows in grid mode, slides in carousel mode. */
    var count by mutableIntStateOf(SplitMode.GRID.defaultCount)
        private set
    var ratio by mutableStateOf(SplitMode.GRID.defaultRatio)
        private set
    var filter by mutableStateOf(PhotoFilter.ORIGINAL)
        private set
    var overlay by mutableStateOf(TextOverlay())
        private set
    var tab by mutableStateOf(EditorTab.LAYOUT)
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

    val rows: Int get() = if (mode == SplitMode.GRID) count else 1
    val columns: Int get() = if (mode == SplitMode.GRID) GRID_COLUMNS else count

    val frameAspect: Float
        get() = (columns * ratio.width).toFloat() / (rows * ratio.height)

    val crop: Crop
        get() = CropMath.crop(imageAspect, frameAspect, zoom, offsetX, offsetY)

    val nextToPost: SavedTile?
        get() = tiles.filter { it.postOrder !in posted }.minByOrNull { it.postOrder }

    fun orderOf(row: Int, column: Int): Int =
        if (mode == SplitMode.GRID) CropMath.postOrder(row, column, rows) else column + 1

    fun startMode(newMode: SplitMode) {
        if (mode == newMode) return
        mode = newMode
        count = newMode.defaultCount
        ratio = newMode.defaultRatio
        tab = EditorTab.LAYOUT
        backToEditor()
        clampOffsets()
    }

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
                backToEditor()
                resetCrop()
            } catch (e: Exception) {
                message = "Couldn't open that image"
            } finally {
                busy = false
            }
        }
    }

    fun updateCount(value: Int) {
        count = value.coerceIn(mode.counts)
        clampOffsets()
    }

    fun updateRatio(value: TileRatio) {
        ratio = value
        clampOffsets()
    }

    fun updateFilter(value: PhotoFilter) {
        filter = value
    }

    fun updateOverlay(value: TextOverlay) {
        overlay = value
    }

    /**
     * A pinch/drag on the frame. On the Text tab it moves and scales the text; otherwise it
     * pans and zooms the photo. [dx]/[dy] are fractions of the frame.
     */
    fun transform(dx: Float, dy: Float, zoomChange: Float) {
        if (tab == EditorTab.TEXT && !overlay.isEmpty) {
            overlay = overlay.copy(
                x = (overlay.x + dx).coerceIn(0f, 1f),
                y = (overlay.y + dy).coerceIn(0f, 1f),
                size = (overlay.size * zoomChange).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE),
            )
            return
        }
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
        val prefix = if (mode == SplitMode.GRID) "UltraGrid" else "UltraGrid_carousel"
        viewModelScope.launch {
            busy = true
            try {
                tiles = TileExporter.export(
                    getApplication(), bitmap, crop, rows, columns, ratio, filter, overlay, prefix, this::orderOf,
                )
                posted = emptySet()
                message = "Saved ${tiles.size} images to Pictures/${TileExporter.ALBUM}"
            } catch (e: Exception) {
                message = "Couldn't save: ${e.message}"
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
        const val MIN_TEXT_SIZE = 0.02f
        const val MAX_TEXT_SIZE = 0.4f
    }
}
