package app.ultragrid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

/** A collage cell as fractions (0..1) of the canvas. */
data class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)

enum class CollageTemplate(val label: String, val cells: List<Cell>) {
    SIDE_BY_SIDE("2 across", gridCells(columns = 2, rows = 1)),
    STACKED("2 stacked", gridCells(columns = 1, rows = 2)),
    THREE_STACKED("3 stacked", gridCells(columns = 1, rows = 3)),
    QUAD("2×2", gridCells(columns = 2, rows = 2)),
    FEATURE("1 + 2", listOf(Cell(0f, 0f, 0.6f, 1f), Cell(0.6f, 0f, 1f, 0.5f), Cell(0.6f, 0.5f, 1f, 1f))),
    SIX("2×3", gridCells(columns = 2, rows = 3)),
}

private fun gridCells(columns: Int, rows: Int): List<Cell> =
    (0 until rows).flatMap { r ->
        (0 until columns).map { c ->
            Cell(c.toFloat() / columns, r.toFloat() / rows, (c + 1f) / columns, (r + 1f) / rows)
        }
    }

/** Index of the cell containing the point ([fx], [fy]) given as canvas fractions, or -1. */
fun CollageTemplate.cellAt(fx: Float, fy: Float): Int =
    cells.indexOfFirst { fx >= it.left && fx < it.right && fy >= it.top && fy < it.bottom }

object CollageRenderer {
    const val BACKGROUND_WHITE = 0xFFFFFFFF.toInt()
    const val BACKGROUND_BLACK = 0xFF000000.toInt()

    /** Draws the collage; used for both the live preview and the exported JPEG. */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        template: CollageTemplate,
        photos: Map<Int, Bitmap>,
        gapFraction: Float,
        background: Int,
        filter: PhotoFilter,
        showPlaceholders: Boolean,
    ) {
        canvas.drawColor(background)
        val gap = gapFraction * width
        val half = gap / 2f
        val contentWidth = width - gap
        val contentHeight = height - gap
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = filter.androidColorFilter()
        }
        val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33888888 }
        val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99888888.toInt()
            strokeWidth = width * 0.008f
        }

        template.cells.forEachIndexed { index, cell ->
            val dst = RectF(
                half + cell.left * contentWidth + half,
                half + cell.top * contentHeight + half,
                half + cell.right * contentWidth - half,
                half + cell.bottom * contentHeight - half,
            )
            if (dst.width() <= 0f || dst.height() <= 0f) return@forEachIndexed
            val photo = photos[index]
            if (photo != null) {
                val crop = CropMath.crop(
                    imageAspect = photo.width.toFloat() / photo.height,
                    frameAspect = dst.width() / dst.height(),
                    zoom = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                )
                val src = Rect(
                    (crop.left * photo.width).roundToInt(),
                    (crop.top * photo.height).roundToInt(),
                    ((crop.left + crop.width) * photo.width).roundToInt(),
                    ((crop.top + crop.height) * photo.height).roundToInt(),
                )
                canvas.drawBitmap(photo, src, dst, paint)
            } else if (showPlaceholders) {
                canvas.drawRect(dst, placeholder)
                val arm = minOf(dst.width(), dst.height()) * 0.12f
                canvas.drawLine(dst.centerX() - arm, dst.centerY(), dst.centerX() + arm, dst.centerY(), plus)
                canvas.drawLine(dst.centerX(), dst.centerY() - arm, dst.centerX(), dst.centerY() + arm, plus)
            }
        }
    }
}
