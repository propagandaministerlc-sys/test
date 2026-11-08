package app.ultragrid

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

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
