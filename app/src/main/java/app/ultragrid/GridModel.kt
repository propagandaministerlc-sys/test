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

/** A tile saved to the gallery. [postOrder] 1 is posted first. */
data class SavedTile(
    val uri: Uri,
    val thumbnail: ImageBitmap,
    val postOrder: Int,
    val row: Int,
    val column: Int,
)
