package app.ultragrid

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the Grid / Bento composition editor. */
class CompositionViewModel(application: Application) : AndroidViewModel(application) {
    var template by mutableStateOf(LayoutTemplate.STAGGER)
        private set
    var layout by mutableStateOf(template.create())
        private set
    var format by mutableStateOf(CompositionFormat.FEED)
        private set
    var style by mutableStateOf(CompositionStyle())
        private set
    var filter by mutableStateOf(PhotoFilter.ORIGINAL)
        private set

    /** Photos and crops by frame number. Kept when switching layouts, so nothing is lost. */
    var photos by mutableStateOf<Map<Int, Bitmap>>(emptyMap())
        private set
    var crops by mutableStateOf<Map<Int, FrameCrop>>(emptyMap())
        private set

    var selected by mutableStateOf<Int?>(null)
        private set

    /** The divider being dragged and how it snapped, for the on-screen guides. */
    var activeDivider by mutableStateOf<Divider?>(null)
        private set
    var snap by mutableStateOf<SnapResult?>(null)
        private set

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val geometry by derivedStateOf { LayoutMath.geometry(layout) }
    val frameIds by derivedStateOf { LayoutMath.frameIds(layout) }
    val filledCount by derivedStateOf { frameIds.count { it in photos } }
    val hasPhotos get() = filledCount > 0

    fun selectTemplate(t: LayoutTemplate) {
        template = t
        layout = t.create()
        if (selected != null && selected!! >= t.frameCount) selected = null
    }

    fun selectFormat(f: CompositionFormat) {
        format = f
    }

    fun updateStyle(transform: (CompositionStyle) -> CompositionStyle) {
        style = transform(style)
    }

    fun selectFilter(f: PhotoFilter) {
        filter = f
    }

    fun select(frame: Int?) {
        selected = if (frame == selected) null else frame
    }

    // Dividers

    fun beginDividerDrag(d: Divider) {
        activeDivider = d
        snap = null
    }

    /** Moves the active divider towards [proposed] (content space) with snapping. */
    fun dragDivider(proposed: Float): Boolean {
        val d = activeDivider ?: return false
        val current = geometry.dividers.firstOrNull { it.splitId == d.splitId && it.index == d.index } ?: return false
        val result = LayoutMath.snap(proposed, current, geometry.dividers)
        val snappedNow = result.kind != SnapKind.NONE && snap?.position != result.position
        snap = result
        layout = LayoutMath.moveDivider(layout, d.splitId, d.index, result.position)
        activeDivider = current.copy(position = result.position)
        return snappedNow
    }

    fun endDividerDrag() {
        activeDivider = null
        snap = null
    }

    // Per-frame crop

    /**
     * Pans by ([dx], [dy]) as fractions of the frame size and zooms by [zoom].
     * [frameAspect] is the frame's on-screen width / height.
     */
    fun transformFrame(frame: Int, dx: Float, dy: Float, zoom: Float, frameAspect: Float) {
        val photo = photos[frame] ?: return
        val current = crops[frame] ?: FrameCrop()
        val next = FrameCrop(current.zoom * zoom, current.offsetX + dx, current.offsetY + dy)
            .clamped(photo.width.toFloat() / photo.height, frameAspect)
        crops = crops + (frame to next)
    }

    fun resetCrop(frame: Int) {
        crops = crops - frame
    }

    fun removePhoto(frame: Int) {
        photos = photos - frame
        crops = crops - frame
        if (selected == frame) selected = null
    }

    fun clearAll() {
        photos = emptyMap()
        crops = emptyMap()
        selected = null
    }

    // Photos

    /** Puts [uri] into [frame]. */
    fun setPhoto(frame: Int, uri: Uri) = loadInto(listOf(frame), listOf(uri))

    /** Fills empty frames in order (then the rest) with the picked photos. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val empty = frameIds.filter { it !in photos }
        val order = (empty + frameIds.filter { it in photos }).take(uris.size)
        loadInto(order, uris.take(order.size))
    }

    private fun loadInto(frames: List<Int>, uris: List<Uri>) {
        viewModelScope.launch {
            busy = true
            try {
                val loaded = withContext(Dispatchers.IO) {
                    frames.zip(uris).mapNotNull { (frame, uri) ->
                        runCatching { frame to ImageLoader.decode(getApplication(), uri, PHOTO_MAX_EDGE) }.getOrNull()
                    }
                }
                if (loaded.size < frames.size) message = "Some images couldn't be opened"
                photos = photos + loaded
                crops = crops - loaded.map { it.first }.toSet()
            } finally {
                busy = false
            }
        }
    }

    /** Swaps the photos (and crops) of two frames. */
    fun swap(a: Int, b: Int) {
        if (a == b) return
        val p = photos.toMutableMap()
        val c = crops.toMutableMap()
        val pa = p.remove(a)
        val pb = p.remove(b)
        if (pa != null) p[b] = pa
        if (pb != null) p[a] = pb
        val ca = c.remove(a)
        val cb = c.remove(b)
        if (ca != null) c[b] = ca
        if (cb != null) c[a] = cb
        photos = p
        crops = c
        selected = b
    }

    /** Renders at full size, saves to the gallery and passes the result to [onSaved]. */
    fun save(onSaved: (Uri) -> Unit = {}) {
        if (busy || !hasPhotos) return
        viewModelScope.launch {
            busy = true
            try {
                val uri = withContext(Dispatchers.Default) {
                    val bitmap = CompositionRenderer.render(format, layout, photos, crops, style, filter)
                    withContext(Dispatchers.IO) {
                        TileExporter.saveJpeg(getApplication(), bitmap, "UltraGrid_${template.family.label.lowercase()}_${TileExporter.timestamp()}.jpg")
                    }.also { bitmap.recycle() }
                }
                message = "Saved to Pictures/${TileExporter.ALBUM}"
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
