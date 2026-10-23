package app.ultragrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ultragrid.Crop
import app.ultragrid.CropMath
import app.ultragrid.GRID_COLUMNS
import app.ultragrid.GridViewModel
import app.ultragrid.MAX_ROWS
import app.ultragrid.TileRatio
import kotlin.math.roundToInt

@Composable
fun EditorScreen(vm: GridViewModel, onPick: () -> Unit) {
    val preview = vm.preview ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "UltraGrid",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::resetCrop) { Text("Reset") }
            TextButton(onClick = onPick) { Text("Change photo") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CropFrame(
                image = preview,
                crop = vm.crop,
                rows = vm.rows,
                onTransform = vm::transform,
                modifier = Modifier.aspectRatio(vm.frameAspect),
            )
        }
        Text(
            "Pinch to zoom · drag to position · numbers show posting order",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Rows")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..MAX_ROWS) {
                FilterChip(selected = vm.rows == n, onClick = { vm.updateRows(n) }, label = { Text("$n") })
            }
        }

        SectionLabel("Tile shape")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TileRatio.entries.forEach { ratio ->
                FilterChip(selected = vm.ratio == ratio, onClick = { vm.updateRatio(ratio) }, label = { Text(ratio.label) })
            }
        }
        Text(
            vm.ratio.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = vm::split, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Split into ${vm.rows * GRID_COLUMNS} posts")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CropFrame(
    image: ImageBitmap,
    crop: Crop,
    rows: Int,
    onTransform: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTransform by rememberUpdatedState(onTransform)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    currentOnTransform(pan.x / size.width, pan.y / size.height, zoom)
                }
            },
    ) {
        val srcLeft = (crop.left * image.width).roundToInt().coerceIn(0, image.width - 1)
        val srcTop = (crop.top * image.height).roundToInt().coerceIn(0, image.height - 1)
        val srcWidth = (crop.width * image.width).roundToInt().coerceIn(1, image.width - srcLeft)
        val srcHeight = (crop.height * image.height).roundToInt().coerceIn(1, image.height - srcTop)
        drawImage(
            image = image,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcWidth, srcHeight),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )

        // The white lines double as a preview of Instagram's gaps between posts.
        val stroke = 2.dp.toPx()
        for (c in 1 until GRID_COLUMNS) {
            val x = size.width * c / GRID_COLUMNS
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        for (r in 1 until rows) {
            val y = size.height * r / rows
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), stroke)
        }

        val radius = 11.dp.toPx()
        val inset = 6.dp.toPx()
        for (r in 0 until rows) for (c in 0 until GRID_COLUMNS) {
            val center = Offset(
                size.width * c / GRID_COLUMNS + inset + radius,
                size.height * r / rows + inset + radius,
            )
            val layout = textMeasurer.measure("${CropMath.postOrder(r, c, rows)}", labelStyle)
            drawCircle(Color.Black.copy(alpha = 0.55f), radius, center)
            drawText(
                layout,
                topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
            )
        }
    }
}
