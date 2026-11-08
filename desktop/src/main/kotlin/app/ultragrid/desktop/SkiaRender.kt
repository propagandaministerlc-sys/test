package app.ultragrid.desktop

import app.ultragrid.CanvasMapping
import app.ultragrid.CompositionFormat
import app.ultragrid.CompositionStyle
import app.ultragrid.CropMath
import app.ultragrid.FrameCrop
import app.ultragrid.LayoutGeometry
import app.ultragrid.LayoutMath
import app.ultragrid.LayoutNode
import app.ultragrid.PhotoFilter
import app.ultragrid.TileRatio
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Image loading, drawing and export with Skia (the engine Compose Desktop draws with). */
object SkiaRender {
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

    /** Decodes an image file (orientation from EXIF is applied by Skia) and caps its size. */
    fun load(file: File, maxEdge: Int = 2560): Image {
        val image = Image.makeFromEncoded(file.readBytes())
        val edge = max(image.width, image.height)
        if (edge <= maxEdge) return image
        val scale = maxEdge.toFloat() / edge
        val w = (image.width * scale).roundToInt()
        val h = (image.height * scale).roundToInt()
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            sampling,
            null,
            true,
        )
        return surface.makeImageSnapshot()
    }

    fun saveJpeg(image: Image, file: File) {
        val data = image.encodeToData(EncodedImageFormat.JPEG, 93) ?: error("JPEG encoding failed")
        file.writeBytes(data.bytes)
    }

    /** The shared Android colour matrix, with offsets converted from 0..255 to Skia's 0..1. */
    fun colorFilter(filter: PhotoFilter): ColorFilter? = filter.matrix?.let { m ->
        val c = m.copyOf()
        for (i in intArrayOf(4, 9, 14, 19)) c[i] = c[i] / 255f
        ColorFilter.makeMatrix(ColorMatrix(*c))
    }

    fun drawComposition(
        canvas: Canvas,
        width: Float,
        height: Float,
        geometry: LayoutGeometry,
        photos: Map<Int, Image>,
        crops: Map<Int, FrameCrop>,
        style: CompositionStyle,
        filter: PhotoFilter,
        placeholder: Int? = null,
    ) {
        canvas.drawRect(Rect.makeWH(width, height), Paint().apply { color = style.background })
        val mapping = CanvasMapping(width, height, style)
        val photoPaint = Paint().apply {
            isAntiAlias = true
            colorFilter = colorFilter(filter)
        }
        val placeholderPaint = placeholder?.let { Paint().apply { color = it; isAntiAlias = true } }

        geometry.frames.forEach { (id, r) ->
            val px = mapping.frameRect(r)
            if (px.width <= 1f || px.height <= 1f) return@forEach
            val radius = min(mapping.radius, min(px.width, px.height) / 2f)
            val rrect = RRect.makeLTRB(px.left, px.top, px.right, px.bottom, radius)
            val photo = photos[id]
            if (photo == null) {
                placeholderPaint?.let { canvas.drawRRect(rrect, it) }
                return@forEach
            }
            val imageAspect = photo.width.toFloat() / photo.height
            val frameAspect = px.width / px.height
            val crop = (crops[id] ?: FrameCrop()).clamped(imageAspect, frameAspect)
            val c = CropMath.crop(imageAspect, frameAspect, crop.zoom, crop.offsetX, crop.offsetY)
            val src = Rect.makeLTRB(
                c.left * photo.width,
                c.top * photo.height,
                (c.left + c.width) * photo.width,
                (c.top + c.height) * photo.height,
            )
            canvas.save()
            canvas.clipRRect(rrect, ClipMode.INTERSECT, true)
            canvas.drawImageRect(photo, src, Rect.makeLTRB(px.left, px.top, px.right, px.bottom), sampling, photoPaint, true)
            canvas.restore()
        }
    }

    fun renderComposition(
        format: CompositionFormat,
        layout: LayoutNode,
        photos: Map<Int, Image>,
        crops: Map<Int, FrameCrop>,
        style: CompositionStyle,
        filter: PhotoFilter,
    ): Image {
        val surface = Surface.makeRasterN32Premul(format.width, format.height)
        drawComposition(
            surface.canvas,
            format.width.toFloat(),
            format.height.toFloat(),
            LayoutMath.geometry(layout),
            photos,
            crops,
            style,
            filter,
        )
        return surface.makeImageSnapshot()
    }

    /** Source rectangle (pixels) of the whole split for a canvas of the given aspect ratio. */
    fun splitSource(image: Image, canvasAspect: Float, crop: FrameCrop): Rect {
        val imageAspect = image.width.toFloat() / image.height
        val clamped = crop.clamped(imageAspect, canvasAspect)
        val c = CropMath.crop(imageAspect, canvasAspect, clamped.zoom, clamped.offsetX, clamped.offsetY)
        return Rect.makeLTRB(
            c.left * image.width,
            c.top * image.height,
            (c.left + c.width) * image.width,
            (c.top + c.height) * image.height,
        )
    }

    fun drawSplit(canvas: Canvas, width: Float, height: Float, image: Image, crop: FrameCrop, filter: PhotoFilter) {
        val paint = Paint().apply { colorFilter = colorFilter(filter) }
        canvas.drawImageRect(image, splitSource(image, width / height, crop), Rect.makeWH(width, height), sampling, paint, true)
    }

    /** Renders each tile, row by row, at the tile ratio's full size. */
    fun renderTiles(
        image: Image,
        crop: FrameCrop,
        columns: Int,
        rows: Int,
        ratio: TileRatio,
        filter: PhotoFilter,
    ): List<List<Image>> {
        val aspect = (columns * ratio.width).toFloat() / (rows * ratio.height)
        val src = splitSource(image, aspect, crop)
        val tileW = src.width / columns
        val tileH = src.height / rows
        val paint = Paint().apply { colorFilter = colorFilter(filter) }
        return (0 until rows).map { r ->
            (0 until columns).map { c ->
                val surface = Surface.makeRasterN32Premul(ratio.width, ratio.height)
                val part = Rect.makeXYWH(src.left + c * tileW, src.top + r * tileH, tileW, tileH)
                surface.canvas.drawImageRect(
                    image,
                    part,
                    Rect.makeWH(ratio.width.toFloat(), ratio.height.toFloat()),
                    sampling,
                    paint,
                    true,
                )
                surface.makeImageSnapshot()
            }
        }
    }
}
