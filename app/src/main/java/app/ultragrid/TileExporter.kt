package app.ultragrid

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object TileExporter {
    const val ALBUM = "UltraGrid"
    private const val THUMBNAIL_WIDTH = 360

    /**
     * Cuts [crop] of [source] into a rows × columns grid, saves each tile as a JPEG in
     * Pictures/UltraGrid and returns them in on-screen (top-left first) order.
     */
    suspend fun export(
        context: Context,
        source: Bitmap,
        crop: Crop,
        rows: Int,
        columns: Int,
        ratio: TileRatio,
    ): List<SavedTile> = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val left = crop.left * source.width
        val top = crop.top * source.height
        val tileWidth = crop.width * source.width / columns
        val tileHeight = crop.height * source.height / rows
        val dst = Rect(0, 0, ratio.width, ratio.height)
        val total = rows * columns

        // Saved in posting order so the gallery lists them in a sensible sequence.
        val tiles = (1..total).map { order ->
            val index = total - order
            val row = index / columns
            val column = index % columns
            val src = Rect(
                (left + column * tileWidth).roundToInt().coerceIn(0, source.width - 1),
                (top + row * tileHeight).roundToInt().coerceIn(0, source.height - 1),
                (left + (column + 1) * tileWidth).roundToInt().coerceIn(1, source.width),
                (top + (row + 1) * tileHeight).roundToInt().coerceIn(1, source.height),
            )
            val tile = Bitmap.createBitmap(ratio.width, ratio.height, Bitmap.Config.ARGB_8888)
            Canvas(tile).drawBitmap(source, src, dst, paint)
            val uri = save(context, tile, "UltraGrid_${stamp}_%02d.jpg".format(Locale.US, order))
            val thumbnail = Bitmap.createScaledBitmap(
                tile,
                THUMBNAIL_WIDTH,
                THUMBNAIL_WIDTH * ratio.height / ratio.width,
                true,
            )
            tile.recycle()
            SavedTile(uri, thumbnail.asImageBitmap(), order, row, column)
        }
        tiles.sortedBy { it.row * columns + it.column }
    }

    private fun save(context: Context, bitmap: Bitmap, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create $name")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                ?: throw IOException("Couldn't write $name")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}
