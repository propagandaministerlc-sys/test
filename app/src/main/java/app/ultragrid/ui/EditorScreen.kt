package app.ultragrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ultragrid.Crop
import app.ultragrid.EditorTab
import app.ultragrid.GridViewModel
import app.ultragrid.PhotoFilter
import app.ultragrid.SplitMode
import app.ultragrid.TEXT_COLORS
import app.ultragrid.TextOverlay
import app.ultragrid.TileRatio
import kotlin.math.roundToInt

@Composable
fun EditorScreen(vm: GridViewModel, onPick: () -> Unit, onBack: () -> Unit) {
    val preview = vm.preview ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(vm.mode.title, onBack) {
            TextButton(onClick = vm::resetCrop) { Text("Reset") }
            TextButton(onClick = onPick) { Text("Photo") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CropFrame(
                image = preview,
                crop = vm.crop,
                rows = vm.rows,
                columns = vm.columns,
                filter = vm.filter,
                overlay = vm.overlay,
                label = vm::orderOf,
                onTransform = vm::transform,
                modifier = Modifier.aspectRatio(vm.frameAspect),
            )
        }
        Hint(
            if (vm.tab == EditorTab.TEXT && !vm.overlay.isEmpty) {
                "Drag to move the text · pinch to resize it"
            } else {
                "Pinch to zoom · drag to position · numbers show posting order"
            },
            Modifier.fillMaxWidth(),
        )

        TabRow(selectedTabIndex = vm.tab.ordinal) {
            EditorTab.entries.forEach { tab ->
                Tab(selected = vm.tab == tab, onClick = { vm.tab = tab }, text = { Text(tab.label) })
            }
        }

        when (vm.tab) {
            EditorTab.LAYOUT -> {
                SectionLabel(vm.mode.countLabel)
                ChoiceChips(vm.mode.counts.toList(), vm.count, { "$it" }, vm::updateCount)
                SectionLabel("Shape")
                ChoiceChips(TileRatio.entries, vm.ratio, { it.label }, vm::updateRatio)
                Hint(vm.ratio.description)
            }
            EditorTab.FILTER -> {
                SectionLabel("Filter")
                ChoiceChips(PhotoFilter.entries, vm.filter, { it.label }, vm::updateFilter)
            }
            EditorTab.TEXT -> TextControls(vm.overlay, vm::updateOverlay)
        }

        Button(onClick = vm::split, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                val total = vm.rows * vm.columns
                Text(if (vm.mode == SplitMode.GRID) "Split into $total posts" else "Create $total slides")
            }
        }
    }
}

@Composable
private fun TextControls(overlay: TextOverlay, onChange: (TextOverlay) -> Unit) {
    OutlinedTextField(
        value = overlay.text,
        onValueChange = { onChange(overlay.copy(text = it)) },
        placeholder = { Text("Add text across your picture") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TEXT_COLORS.forEach { color ->
            val selected = overlay.color == color
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onChange(overlay.copy(color = color)) },
            )
        }
        Spacer(Modifier.weight(1f))
        FilterChip(
            selected = overlay.bold,
            onClick = { onChange(overlay.copy(bold = !overlay.bold)) },
            label = { Text("Bold") },
        )
    }
}

@Composable
private fun CropFrame(
    image: ImageBitmap,
    crop: Crop,
    rows: Int,
    columns: Int,
    filter: PhotoFilter,
    overlay: TextOverlay,
    label: (Int, Int) -> Int,
    onTransform: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTransform by rememberUpdatedState(onTransform)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val colorFilter = remember(filter) { filter.composeColorFilter() }

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
            colorFilter = colorFilter,
            filterQuality = FilterQuality.Medium,
        )
        drawIntoCanvas { overlay.draw(it.nativeCanvas, size.width, size.height) }

        // The white lines double as a preview of Instagram's gaps between posts.
        val stroke = 2.dp.toPx()
        for (c in 1 until columns) {
            val x = size.width * c / columns
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        for (r in 1 until rows) {
            val y = size.height * r / rows
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), stroke)
        }

        val radius = 11.dp.toPx()
        val inset = 6.dp.toPx()
        for (r in 0 until rows) for (c in 0 until columns) {
            val center = Offset(
                size.width * c / columns + inset + radius,
                size.height * r / rows + inset + radius,
            )
            val layout = textMeasurer.measure("${label(r, c)}", labelStyle)
            drawCircle(Color.Black.copy(alpha = 0.55f), radius, center)
            drawText(
                layout,
                topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
            )
        }
    }
}
