package app.ultragrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

enum class GlyphKind { GRID, CAROUSEL, COLLAGE, PLANNER }

/** Small drawn icons for the home screen; each is a few rounded rectangles. */
@Composable
fun Glyph(kind: GlyphKind, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val g = w * 0.06f
        // Rectangles as (left, top, right, bottom) fractions.
        val rects: List<FloatArray> = when (kind) {
            GlyphKind.GRID -> (0 until 3).flatMap { r ->
                (0 until 3).map { c -> floatArrayOf(c / 3f, r / 3f, (c + 1) / 3f, (r + 1) / 3f) }
            }
            GlyphKind.CAROUSEL -> (0 until 3).map { c -> floatArrayOf(c / 3f, 0.2f, (c + 1) / 3f, 0.8f) }
            GlyphKind.COLLAGE -> listOf(
                floatArrayOf(0f, 0f, 0.6f, 1f),
                floatArrayOf(0.6f, 0f, 1f, 0.5f),
                floatArrayOf(0.6f, 0.5f, 1f, 1f),
            )
            GlyphKind.PLANNER -> (0 until 2).flatMap { r ->
                (0 until 3).map { c -> floatArrayOf(c / 3f, r / 2f, (c + 1) / 3f, (r + 1) / 2f) }
            }
        }
        rects.forEachIndexed { i, r ->
            val left = r[0] * w + g / 2
            val top = r[1] * h + g / 2
            drawRoundRect(
                color = color.copy(alpha = if (i % 2 == 0) 1f else 0.6f),
                topLeft = Offset(left, top),
                size = Size(r[2] * w - g / 2 - left, r[3] * h - g / 2 - top),
                cornerRadius = CornerRadius(w * 0.05f),
            )
        }
    }
}
