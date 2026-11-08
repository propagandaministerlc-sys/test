package app.ultragrid

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Draws a composition; used for both the live preview and the exported JPEG. */
object CompositionRenderer {

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        geometry: LayoutGeometry,
        photos: Map<Int, Bitmap>,
        crops: Map<Int, FrameCrop>,
        style: CompositionStyle,
        filter: PhotoFilter,
        placeholderColor: Int? = null,
    ) {
        canvas.drawColor(style.background)
        val mapping = CanvasMapping(width, height, style)
        val colorFilter = filter.androidColorFilter()
        val placeholderPaint = placeholderColor?.let { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it } }
        val matrix = Matrix()

        geometry.frames.forEach { (id, r) ->
            val px = mapping.frameRect(r)
            if (px.width <= 1f || px.height <= 1f) return@forEach
            val dst = RectF(px.left, px.top, px.right, px.bottom)
            val radius = minOf(mapping.radius, px.width / 2f, px.height / 2f)
            val photo = photos[id]
            if (photo == null) {
                placeholderPaint?.let { canvas.drawRoundRect(dst, radius, radius, it) }
                return@forEach
            }
            val imageAspect = photo.width.toFloat() / photo.height
            val frameAspect = px.width / px.height
            val crop = (crops[id] ?: FrameCrop()).clamped(imageAspect, frameAspect)
            val c = CropMath.crop(imageAspect, frameAspect, crop.zoom, crop.offsetX, crop.offsetY)
            val src = RectF(
                c.left * photo.width,
                c.top * photo.height,
                (c.left + c.width) * photo.width,
                (c.top + c.height) * photo.height,
            )
            matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL)
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
                this.colorFilter = colorFilter
            }
            canvas.drawRoundRect(dst, radius, radius, paint)
        }
    }

    /** Renders the composition at the format's full size. */
    fun render(
        format: CompositionFormat,
        layout: LayoutNode,
        photos: Map<Int, Bitmap>,
        crops: Map<Int, FrameCrop>,
        style: CompositionStyle,
        filter: PhotoFilter,
    ): Bitmap {
        val out = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        draw(
            Canvas(out),
            format.width.toFloat(),
            format.height.toFloat(),
            LayoutMath.geometry(layout),
            photos,
            crops,
            style,
            filter,
        )
        return out
    }
}
