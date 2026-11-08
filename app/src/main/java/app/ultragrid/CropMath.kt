package app.ultragrid

/** A rectangle expressed as fractions (0..1) of the source image. */
data class Crop(val left: Float, val top: Float, val width: Float, val height: Float)

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
