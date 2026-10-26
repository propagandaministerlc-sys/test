package app.ultragrid

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

val TEXT_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFFFFD54F.toInt(),
    0xFFFF6F91.toInt(),
    0xFF6C4DFF.toInt(),
)

/**
 * Text drawn across the whole picture before it is split, so it can span several tiles.
 * Position and size are fractions of the full canvas, which keeps the preview and the
 * exported files identical.
 */
data class TextOverlay(
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.08f,
    val color: Int = TEXT_COLORS.first(),
    val bold: Boolean = true,
) {
    val isEmpty: Boolean get() = text.isBlank()

    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (isEmpty) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@TextOverlay.color
            textSize = size * height
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x66000000)
        }
        val lines = text.lines()
        val lineHeight = paint.fontSpacing
        var baseline = y * height - lineHeight * (lines.size - 1) / 2f - (paint.ascent() + paint.descent()) / 2f
        for (line in lines) {
            canvas.drawText(line, x * width, baseline, paint)
            baseline += lineHeight
        }
    }
}
