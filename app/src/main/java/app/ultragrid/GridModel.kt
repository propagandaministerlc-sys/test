package app.ultragrid

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

/** Instagram profiles are always three columns wide. */
const val GRID_COLUMNS = 3
const val MAX_ROWS = 5
const val MAX_SLIDES = 10

enum class TileRatio(val label: String, val description: String, val width: Int, val height: Int) {
    PROFILE("3:4", "Fills Instagram's profile grid exactly (1080×1440).", 1080, 1440),
    PORTRAIT("4:5", "Largest portrait size shown uncropped in the feed (1080×1350).", 1080, 1350),
    SQUARE("1:1", "Classic square posts (1080×1080).", 1080, 1080),
}

/** The two ways a single photo can be split. */
enum class SplitMode(
    val title: String,
    val countLabel: String,
    val counts: IntRange,
    val defaultCount: Int,
    val defaultRatio: TileRatio,
) {
    GRID("Profile grid", "Rows", 1..MAX_ROWS, 3, TileRatio.PROFILE),
    CAROUSEL("Carousel", "Slides", 2..MAX_SLIDES, 3, TileRatio.PORTRAIT),
}

enum class EditorTab(val label: String) { LAYOUT("Layout"), FILTER("Filter"), TEXT("Text") }

/** A rectangle expressed as fractions (0..1) of the source image. */
data class Crop(val left: Float, val top: Float, val width: Float, val height: Float)

/** A tile saved to the gallery. [postOrder] 1 is posted first. */
data class SavedTile(
    val uri: Uri,
    val thumbnail: ImageBitmap,
    val postOrder: Int,
    val row: Int,
    val column: Int,
)

/**
 * Maps the editor's zoom/pan state onto the source image.
 *
 * Offsets are fractions of the frame size, so they stay valid as the on-screen frame
 * resizes. At zoom 1 the image just covers the frame ("center crop").
 */
object CropMath {
    /** Width and height of the visible region as fractions of the image. */
    fun cropSize(imageAspect: Float, frameAspect: Float, zoom: Float): Pair<Float, Float> =
        if (imageAspect > frameAspect) {
            (frameAspect / imageAspect) / zoom to 1f / zoom
        } else {
            1f / zoom to (imageAspect / frameAspect) / zoom
        }

    /** Largest offset (in frame fractions) that keeps the frame fully covered. */
    fun maxOffset(sizeFraction: Float): Float = (1f - sizeFraction) / (2f * sizeFraction)

    fun crop(imageAspect: Float, frameAspect: Float, zoom: Float, offsetX: Float, offsetY: Float): Crop {
        val (w, h) = cropSize(imageAspect, frameAspect, zoom)
        val centerX = 0.5f - offsetX * w
        val centerY = 0.5f - offsetY * h
        return Crop(centerX - w / 2f, centerY - h / 2f, w, h)
    }

    /** Post order for the tile at [row]/[column]: the last tile posted lands top-left. */
    fun postOrder(row: Int, column: Int, rows: Int, columns: Int = GRID_COLUMNS): Int =
        rows * columns - (row * columns + column)
}
