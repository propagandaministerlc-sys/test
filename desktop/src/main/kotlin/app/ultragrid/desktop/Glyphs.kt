package app.ultragrid.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.ultragrid.LayoutMath
import app.ultragrid.LayoutTemplate

enum class GlyphKind { GRID, CAROUSEL, COMPOSITION, PLANNER }

/** Outline icons in the style of the UltraGrid logo: rounded frames drawn with a thin stroke. */
@Composable
fun Glyph(kind: GlyphKind, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        when (kind) {
            GlyphKind.COMPOSITION -> drawBentoLogo(color)
            GlyphKind.GRID -> drawFrames(color, (0 until 3).flatMap { r -> (0 until 3).map { c -> floatArrayOf(c / 3f, r / 3f, (c + 1) / 3f, (r + 1) / 3f) } })
            GlyphKind.CAROUSEL -> drawFrames(color, (0 until 3).map { c -> floatArrayOf(c / 3f, 0.18f, (c + 1) / 3f, 0.82f) })
            GlyphKind.PLANNER -> drawFrames(color, (0 until 2).flatMap { r -> (0 until 3).map { c -> floatArrayOf(c / 3f, 0.12f + r * 0.38f, (c + 1) / 3f, 0.12f + (r + 1) * 0.38f) } })
        }
    }
}

private fun DrawScope.drawFrames(color: Color, rects: List<FloatArray>) {
    val w = size.width
    val h = size.height
    val gap = w * 0.06f
    val stroke = w * 0.035f
    rects.forEach { r ->
        val left = r[0] * w + gap / 2
        val top = r[1] * h + gap / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(r[2] * w - gap / 2 - left, r[3] * h - gap / 2 - top),
            cornerRadius = CornerRadius(w * 0.05f),
            style = Stroke(stroke),
        )
    }
}

/**
 * The UltraGrid mark: a rounded square split into bento frames.
 * Outer corners are large, inner corners small, like the app icon.
 */
fun DrawScope.drawBentoLogo(color: Color, strokeFraction: Float = 0.045f) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    val stroke = s * strokeFraction
    val gap = s * 0.08f
    val big = CornerRadius(s * 0.26f)
    val small = CornerRadius(s * 0.05f)
    val inset = stroke / 2f
    val rows = listOf(0f to 0.29f, 0.37f to 0.63f, 0.71f to 1f)
    fun cell(l: Float, t: Float, r: Float, b: Float, tl: Boolean, tr: Boolean, br: Boolean, bl: Boolean) {
        val left = ox + inset + l * (s - stroke)
        val top = oy + inset + t * (s - stroke)
        val right = ox + inset + r * (s - stroke)
        val bottom = oy + inset + b * (s - stroke)
        val max = minOf(right - left, bottom - top) / 2f
        val large = CornerRadius(minOf(big.x, max))
        val rr = RoundRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            topLeftCornerRadius = if (tl) large else small,
            topRightCornerRadius = if (tr) large else small,
            bottomRightCornerRadius = if (br) large else small,
            bottomLeftCornerRadius = if (bl) large else small,
        )
        drawPath(Path().apply { addRoundRect(rr) }, color, style = Stroke(stroke))
    }
    val g = gap / s
    cell(0f, rows[0].first, 0.66f - g / 2, rows[0].second, tl = true, tr = false, br = false, bl = false)
    cell(0.66f + g / 2, rows[0].first, 1f, rows[0].second, tl = false, tr = true, br = false, bl = false)
    cell(0f, rows[1].first, 0.27f - g / 2, rows[1].second, tl = false, tr = false, br = false, bl = false)
    cell(0.27f + g / 2, rows[1].first, 1f, rows[1].second, tl = false, tr = false, br = false, bl = false)
    cell(0f, rows[2].first, 1f, rows[2].second, tl = false, tr = false, br = true, bl = true)
}

/** Miniature of a layout template, drawn as outlines. */
fun DrawScope.drawTemplate(template: LayoutTemplate, color: Color, aspect: Float) {
    val boxH = size.height
    val boxW = size.width
    val (w, h) = if (aspect > boxW / boxH) boxW to boxW / aspect else boxH * aspect to boxH
    val ox = (boxW - w) / 2f
    val oy = (boxH - h) / 2f
    val gap = minOf(w, h) * 0.07f
    val stroke = minOf(w, h) * 0.04f
    LayoutMath.geometry(template.create()).frames.values.forEach { r ->
        val left = ox + r.left * w + if (r.left > 0f) gap / 2 else stroke / 2
        val top = oy + r.top * h + if (r.top > 0f) gap / 2 else stroke / 2
        val right = ox + r.right * w - if (r.right < 1f) gap / 2 else stroke / 2
        val bottom = oy + r.bottom * h - if (r.bottom < 1f) gap / 2 else stroke / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(minOf(w, h) * 0.06f),
            style = Stroke(stroke),
        )
    }
}
