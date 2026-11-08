package app.ultragrid.desktop

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ultragrid.CompositionFormat
import app.ultragrid.CompositionStyle
import app.ultragrid.CropMath
import app.ultragrid.Divider
import app.ultragrid.FrameCrop
import app.ultragrid.GRID_COLUMNS
import app.ultragrid.LayoutMath
import app.ultragrid.LayoutTemplate
import app.ultragrid.PhotoFilter
import app.ultragrid.SnapKind
import app.ultragrid.SnapResult
import app.ultragrid.TileRatio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File

/** Shared status line ("Saved …", errors). */
class StatusState {
    var message by mutableStateOf<String?>(null)
        private set
    var serial by mutableStateOf(0)
        private set

    fun show(text: String) {
        message = text
        serial++
    }

    fun clear() {
        message = null
    }
}

class CompositionState(private val scope: CoroutineScope, private val status: StatusState) {
    var template by mutableStateOf(LayoutTemplate.STAGGER)
        private set
    var layout by mutableStateOf(template.create())
        private set
    var format by mutableStateOf(CompositionFormat.FEED)
    var style by mutableStateOf(CompositionStyle())
    var filter by mutableStateOf(PhotoFilter.ORIGINAL)
    var photos by mutableStateOf<Map<Int, Image>>(emptyMap())
        private set
    var crops by mutableStateOf<Map<Int, FrameCrop>>(emptyMap())
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var swapFrom by mutableStateOf<Int?>(null)
    var activeDivider by mutableStateOf<Divider?>(null)
        private set
    var snap by mutableStateOf<SnapResult?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val geometry by derivedStateOf { LayoutMath.geometry(layout) }
    val frameIds by derivedStateOf { LayoutMath.frameIds(layout) }
    val filledCount by derivedStateOf { frameIds.count { it in photos } }
    val hasPhotos get() = filledCount > 0

    fun selectTemplate(t: LayoutTemplate) {
        template = t
        layout = t.create()
        selected?.let { if (it >= t.frameCount) selected = null }
    }

    fun select(frame: Int?) {
        selected = if (frame == selected) null else frame
    }

    fun beginDividerDrag(d: Divider) {
        activeDivider = d
        snap = null
    }

    fun dragDivider(proposed: Float) {
        val d = activeDivider ?: return
        val current = geometry.dividers.firstOrNull { it.splitId == d.splitId && it.index == d.index } ?: return
        val result = LayoutMath.snap(proposed, current, geometry.dividers)
        snap = result
        layout = LayoutMath.moveDivider(layout, d.splitId, d.index, result.position)
        activeDivider = current.copy(position = result.position)
    }

    fun endDividerDrag() {
        activeDivider = null
        snap = null
    }

    val isSnapping get() = snap?.kind?.let { it != SnapKind.NONE } == true

    fun transformFrame(frame: Int, dx: Float, dy: Float, zoom: Float, frameAspect: Float) {
        val photo = photos[frame] ?: return
        val current = crops[frame] ?: FrameCrop()
        crops = crops + (frame to FrameCrop(current.zoom * zoom, current.offsetX + dx, current.offsetY + dy)
            .clamped(photo.width.toFloat() / photo.height, frameAspect))
    }

    fun setZoom(frame: Int, zoom: Float, frameAspect: Float) {
        val photo = photos[frame] ?: return
        val current = crops[frame] ?: FrameCrop()
        crops = crops + (frame to current.copy(zoom = zoom).clamped(photo.width.toFloat() / photo.height, frameAspect))
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
        swapFrom = null
    }

    fun swap(a: Int, b: Int) {
        swapFrom = null
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

    fun setPhoto(frame: Int, file: File) = load(listOf(frame), listOf(file))

    /** Fills empty frames first, in order, then the others. */
    fun addPhotos(files: List<File>) {
        if (files.isEmpty()) return
        val empty = frameIds.filter { it !in photos }
        val order = (empty + frameIds.filter { it in photos }).take(files.size)
        load(order, files.take(order.size))
    }

    private fun load(frames: List<Int>, files: List<File>) {
        scope.launch {
            busy = true
            val loaded = withContext(Dispatchers.IO) {
                frames.zip(files).mapNotNull { (frame, file) -> runCatching { frame to SkiaRender.load(file) }.getOrNull() }
            }
            if (loaded.size < frames.size) status.show("Some images couldn't be opened")
            photos = photos + loaded
            crops = crops - loaded.map { it.first }.toSet()
            busy = false
        }
    }

    fun export(file: File) {
        if (!hasPhotos || busy) return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.Default) {
                    val image = SkiaRender.renderComposition(format, layout, photos, crops, style, filter)
                    SkiaRender.saveJpeg(image, file)
                    image.close()
                }
                status.show("Saved ${file.name} (${format.width} × ${format.height})")
            } catch (e: Exception) {
                status.show("Couldn't save: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
}

enum class SplitKind(val title: String, val countLabel: String, val counts: IntRange, val defaultCount: Int, val defaultRatio: TileRatio) {
    GRID("Profile grid", "Rows", 1..app.ultragrid.MAX_ROWS, 3, TileRatio.PROFILE),
    CAROUSEL("Carousel", "Slides", 2..app.ultragrid.MAX_SLIDES, 3, TileRatio.PORTRAIT),
}

class SplitState(val kind: SplitKind, private val scope: CoroutineScope, private val status: StatusState) {
    var image by mutableStateOf<Image?>(null)
        private set
    var fileName by mutableStateOf("")
        private set
    var count by mutableStateOf(kind.defaultCount)
    var ratio by mutableStateOf(kind.defaultRatio)
    var filter by mutableStateOf(PhotoFilter.ORIGINAL)
    var crop by mutableStateOf(FrameCrop())
        private set
    var busy by mutableStateOf(false)
        private set

    val columns get() = if (kind == SplitKind.GRID) GRID_COLUMNS else count
    val rows get() = if (kind == SplitKind.GRID) count else 1
    val aspect get() = (columns * ratio.width).toFloat() / (rows * ratio.height)

    /** Number shown on a tile: posting order for the grid, slide number for the carousel. */
    fun label(row: Int, column: Int) =
        if (kind == SplitKind.GRID) CropMath.postOrder(row, column, rows) else column + 1

    fun open(file: File) {
        scope.launch {
            busy = true
            val loaded = withContext(Dispatchers.IO) { runCatching { SkiaRender.load(file, maxEdge = 6000) }.getOrNull() }
            if (loaded == null) {
                status.show("Couldn't open ${file.name}")
            } else {
                image = loaded
                fileName = file.nameWithoutExtension
                crop = FrameCrop()
            }
            busy = false
        }
    }

    fun transform(dx: Float, dy: Float, zoom: Float) {
        val img = image ?: return
        crop = FrameCrop(crop.zoom * zoom, crop.offsetX + dx, crop.offsetY + dy)
            .clamped(img.width.toFloat() / img.height, aspect)
    }

    fun setZoom(zoom: Float) {
        val img = image ?: return
        crop = crop.copy(zoom = zoom).clamped(img.width.toFloat() / img.height, aspect)
    }

    fun resetCrop() {
        crop = FrameCrop()
    }

    /** Saves every tile next to [target] as name_01.jpg …, numbered in posting order. */
    fun export(target: File) {
        val img = image ?: return
        if (busy) return
        scope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.Default) {
                    val tiles = SkiaRender.renderTiles(img, crop, columns, rows, ratio, filter)
                    val base = target.nameWithoutExtension
                    val dir = target.parentFile
                    tiles.forEachIndexed { r, row ->
                        row.forEachIndexed { c, tile ->
                            val n = label(r, c)
                            SkiaRender.saveJpeg(tile, File(dir, "${base}_${n.toString().padStart(2, '0')}.jpg"))
                            tile.close()
                        }
                    }
                    rows * columns
                }
                status.show(
                    if (kind == SplitKind.GRID) {
                        "Saved $count tiles. Post ${target.nameWithoutExtension}_01 first."
                    } else {
                        "Saved $count slides, in swipe order."
                    },
                )
            } catch (e: Exception) {
                status.show("Couldn't save: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
}
