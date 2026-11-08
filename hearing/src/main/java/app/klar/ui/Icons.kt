package app.klar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class KlarIcon {
    WAVEFORM, SLIDERS, INFO, POWER, HEADPHONES, MIC, PHONE, SPEAKER_LOW, SPEAKER_HIGH,
    EAR, CHEVRON_RIGHT, CLOSE, CHECK, SHIELD, SPARKLE,
}

/** Line icons drawn in code, in the spirit of SF Symbols. */
@Composable
fun Glyph(icon: KlarIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) { drawIcon(icon, tint) }
}

private fun DrawScope.drawIcon(icon: KlarIcon, tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.085f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
    fun path(block: Path.() -> Unit, fill: Boolean = false) {
        val path = Path().apply(block)
        if (fill) drawPath(path, tint) else drawPath(path, tint, style = stroke)
    }

    when (icon) {
        KlarIcon.WAVEFORM -> {
            val heights = floatArrayOf(0.28f, 0.56f, 0.86f, 0.56f, 0.28f)
            heights.forEachIndexed { i, hh ->
                val x = 0.14f + i * 0.18f
                line(x, 0.5f - hh / 2, x, 0.5f + hh / 2)
            }
        }
        KlarIcon.SLIDERS -> {
            val ys = floatArrayOf(0.24f, 0.5f, 0.76f)
            val knobs = floatArrayOf(0.66f, 0.34f, 0.58f)
            for (i in 0..2) {
                line(0.1f, ys[i], 0.9f, ys[i])
                drawCircle(tint, radius = w * 0.1f, center = p(knobs[i], ys[i]))
            }
        }
        KlarIcon.INFO -> {
            drawCircle(tint, radius = w * 0.42f, center = p(0.5f, 0.5f), style = stroke)
            drawCircle(tint, radius = sw * 0.75f, center = p(0.5f, 0.31f))
            line(0.5f, 0.46f, 0.5f, 0.72f)
        }
        KlarIcon.POWER -> {
            drawArc(
                tint, startAngle = -55f, sweepAngle = 290f, useCenter = false,
                topLeft = p(0.16f, 0.18f), size = Size(w * 0.68f, h * 0.68f), style = stroke,
            )
            line(0.5f, 0.1f, 0.5f, 0.46f)
        }
        KlarIcon.HEADPHONES -> {
            drawArc(
                tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = p(0.14f, 0.16f), size = Size(w * 0.72f, h * 0.72f), style = stroke,
            )
            line(0.14f, 0.52f, 0.14f, 0.62f)
            line(0.86f, 0.52f, 0.86f, 0.62f)
            drawRoundRect(tint, topLeft = p(0.1f, 0.54f), size = Size(w * 0.2f, h * 0.34f), cornerRadius = CornerRadius(w * 0.08f))
            drawRoundRect(tint, topLeft = p(0.7f, 0.54f), size = Size(w * 0.2f, h * 0.34f), cornerRadius = CornerRadius(w * 0.08f))
        }
        KlarIcon.MIC -> {
            drawRoundRect(
                tint, topLeft = p(0.36f, 0.08f), size = Size(w * 0.28f, h * 0.5f),
                cornerRadius = CornerRadius(w * 0.14f), style = stroke,
            )
            drawArc(
                tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = p(0.2f, 0.22f), size = Size(w * 0.6f, h * 0.52f), style = stroke,
            )
            line(0.5f, 0.74f, 0.5f, 0.9f)
            line(0.36f, 0.9f, 0.64f, 0.9f)
        }
        KlarIcon.PHONE -> {
            drawRoundRect(
                tint, topLeft = p(0.27f, 0.06f), size = Size(w * 0.46f, h * 0.88f),
                cornerRadius = CornerRadius(w * 0.1f), style = stroke,
            )
            line(0.44f, 0.8f, 0.56f, 0.8f)
        }
        KlarIcon.SPEAKER_LOW, KlarIcon.SPEAKER_HIGH -> {
            path({
                moveTo(w * 0.1f, h * 0.38f)
                lineTo(w * 0.28f, h * 0.38f)
                lineTo(w * 0.48f, h * 0.2f)
                lineTo(w * 0.48f, h * 0.8f)
                lineTo(w * 0.28f, h * 0.62f)
                lineTo(w * 0.1f, h * 0.62f)
                close()
            }, fill = true)
            drawArc(
                tint, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = p(0.42f, 0.34f), size = Size(w * 0.32f, h * 0.32f), style = stroke,
            )
            if (icon == KlarIcon.SPEAKER_HIGH) {
                drawArc(
                    tint, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = p(0.36f, 0.2f), size = Size(w * 0.6f, h * 0.6f), style = stroke,
                )
            }
        }
        KlarIcon.EAR -> {
            path({
                moveTo(w * 0.28f, h * 0.4f)
                cubicTo(w * 0.28f, h * 0.12f, w * 0.76f, h * 0.08f, w * 0.76f, h * 0.4f)
                cubicTo(w * 0.76f, h * 0.56f, w * 0.6f, h * 0.6f, w * 0.57f, h * 0.72f)
                cubicTo(w * 0.53f, h * 0.88f, w * 0.38f, h * 0.92f, w * 0.3f, h * 0.82f)
            })
            path({
                moveTo(w * 0.42f, h * 0.42f)
                cubicTo(w * 0.42f, h * 0.28f, w * 0.63f, h * 0.27f, w * 0.63f, h * 0.42f)
                cubicTo(w * 0.63f, h * 0.5f, w * 0.53f, h * 0.52f, w * 0.51f, h * 0.6f)
            })
        }
        KlarIcon.CHEVRON_RIGHT -> path({
            moveTo(w * 0.38f, h * 0.2f)
            lineTo(w * 0.64f, h * 0.5f)
            lineTo(w * 0.38f, h * 0.8f)
        })
        KlarIcon.CLOSE -> {
            line(0.26f, 0.26f, 0.74f, 0.74f)
            line(0.74f, 0.26f, 0.26f, 0.74f)
        }
        KlarIcon.CHECK -> path({
            moveTo(w * 0.2f, h * 0.53f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.8f, h * 0.3f)
        })
        KlarIcon.SHIELD -> {
            path({
                moveTo(w * 0.5f, h * 0.08f)
                lineTo(w * 0.84f, h * 0.2f)
                cubicTo(w * 0.84f, h * 0.56f, w * 0.72f, h * 0.78f, w * 0.5f, h * 0.92f)
                cubicTo(w * 0.28f, h * 0.78f, w * 0.16f, h * 0.56f, w * 0.16f, h * 0.2f)
                close()
            })
            path({
                moveTo(w * 0.36f, h * 0.5f)
                lineTo(w * 0.47f, h * 0.61f)
                lineTo(w * 0.65f, h * 0.4f)
            })
        }
        KlarIcon.SPARKLE -> path({
            moveTo(w * 0.5f, h * 0.08f)
            cubicTo(w * 0.54f, h * 0.36f, w * 0.64f, h * 0.46f, w * 0.92f, h * 0.5f)
            cubicTo(w * 0.64f, h * 0.54f, w * 0.54f, h * 0.64f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.46f, h * 0.64f, w * 0.36f, h * 0.54f, w * 0.08f, h * 0.5f)
            cubicTo(w * 0.36f, h * 0.46f, w * 0.46f, h * 0.36f, w * 0.5f, h * 0.08f)
            close()
        }, fill = true)
    }
}
