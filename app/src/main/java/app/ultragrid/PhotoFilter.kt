package app.ultragrid

import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/** Colour presets, applied identically to the on-screen preview and the exported files. */
enum class PhotoFilter(val label: String, private val matrix: FloatArray?) {
    ORIGINAL("Original", null),
    VIVID("Vivid", saturation(1.4f)),
    WARM("Warm", channelScale(1.08f, 1.0f, 0.9f)),
    COOL("Cool", channelScale(0.92f, 1.0f, 1.1f)),
    FADE("Fade", contrast(0.85f, 30f)),
    MONO("B&W", saturation(0f)),
    NOIR("Noir", noir()),
    ;

    fun androidColorFilter(): android.graphics.ColorFilter? = matrix?.let { ColorMatrixColorFilter(it) }

    fun composeColorFilter(): ColorFilter? = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it.copyOf())) }
}

private fun saturation(s: Float): FloatArray {
    val r = 0.213f * (1 - s)
    val g = 0.715f * (1 - s)
    val b = 0.072f * (1 - s)
    return floatArrayOf(
        r + s, g, b, 0f, 0f,
        r, g + s, b, 0f, 0f,
        r, g, b + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun channelScale(r: Float, g: Float, b: Float) = floatArrayOf(
    r, 0f, 0f, 0f, 0f,
    0f, g, 0f, 0f, 0f,
    0f, 0f, b, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** [offset] is in 0..255 colour units. */
private fun contrast(scale: Float, offset: Float) = floatArrayOf(
    scale, 0f, 0f, 0f, offset,
    0f, scale, 0f, 0f, offset,
    0f, 0f, scale, 0f, offset,
    0f, 0f, 0f, 1f, 0f,
)

private fun noir(): FloatArray {
    val c = 1.3f
    val offset = -38f
    return floatArrayOf(
        0.213f * c, 0.715f * c, 0.072f * c, 0f, offset,
        0.213f * c, 0.715f * c, 0.072f * c, 0f, offset,
        0.213f * c, 0.715f * c, 0.072f * c, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}
