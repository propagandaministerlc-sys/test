package app.ultragrid.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ultragrid.FrameCrop
import app.ultragrid.PhotoFilter
import app.ultragrid.TileRatio
import java.awt.Cursor
import kotlin.math.exp
import kotlin.math.roundToInt

/** Profile grid and carousel: one photo split into tiles. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SplitPane(state: SplitState, onOpen: () -> Unit, onExport: () -> Unit) {
    val c = Ultra.colors
    val measurer = rememberTextMeasurer()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.kind.title, style = MaterialTheme.typography.headlineMedium, color = c.text)
                    val total = state.rows * state.columns
                    Text(
                        if (state.kind == SplitKind.GRID) {
                            "GRID / ${state.rows} ROWS · $total POSTS · ${state.ratio.label}"
                        } else {
                            "CAROUSEL / ${state.columns} SLIDES · ${state.ratio.label}"
                        },
                        style = CaptionStyle,
                        color = c.muted,
                    )
                }
                GhostButton(if (state.image == null) "Open photo" else "Change photo", enabled = !state.busy, onClick = onOpen)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    if (state.kind == SplitKind.GRID) "Export tiles" else "Export slides",
                    enabled = state.image != null && !state.busy,
                    onClick = onExport,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                val image = state.image
                if (image == null) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(c.panel)
                            .pointerHoverIcon(HandCursor)
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 64.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Glyph(if (state.kind == SplitKind.GRID) GlyphKind.GRID else GlyphKind.CAROUSEL, Modifier.size(84.dp), c.text)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (state.kind == SplitKind.GRID) {
                                "Open a photo to spread across\nyour profile grid."
                            } else {
                                "Open a wide photo to turn into\na seamless swipe."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Or drop it onto the window.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                } else {
                    Box(
                        Modifier
                            .aspectRatio(state.aspect)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
                            .onPointerEvent(PointerEventType.Scroll) { e ->
                                state.transform(0f, 0f, exp(-e.changes.first().scrollDelta.y * 0.12f))
                            }
                            .pointerInput(state) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    state.transform(drag.x / size.width, drag.y / size.height, 1f)
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawIntoCanvas { SkiaRender.drawSplit(it.nativeCanvas, size.width, size.height, image, state.crop, state.filter) }
                            val tileW = size.width / state.columns
                            val tileH = size.height / state.rows
                            val line = Color(0xCCFFFFFF)
                            val stroke = 1.dp.toPx()
                            for (col in 1 until state.columns) drawLine(line, Offset(col * tileW, 0f), Offset(col * tileW, size.height), stroke)
                            for (row in 1 until state.rows) drawLine(line, Offset(0f, row * tileH), Offset(size.width, row * tileH), stroke)
                            val style = TextStyle(
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White,
                                shadow = Shadow(Color(0xAA000000), blurRadius = 8f),
                            )
                            for (row in 0 until state.rows) {
                                for (col in 0 until state.columns) {
                                    val t = measurer.measure(two(state.label(row, col)), style)
                                    val pad = 10.dp.toPx()
                                    drawText(t, topLeft = Offset(col * tileW + pad, (row + 1) * tileH - pad - t.size.height))
                                }
                            }
                        }
                    }
                }
            }
            Text(
                if (state.kind == SplitKind.GRID) {
                    "Numbers show the posting order: post 01 first. Instagram puts the newest post top-left, so the picture builds up correctly."
                } else {
                    "Drag to frame the photo · Scroll to zoom · Slides are numbered in swipe order."
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }

        Column(
            Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(c.panel)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Section(state.kind.countLabel) {
                SliderRow(
                    if (state.kind == SplitKind.GRID) "Rows of 3" else "Slides",
                    state.count.toFloat(),
                    state.kind.counts.first.toFloat()..state.kind.counts.last.toFloat(),
                    two(state.count),
                    steps = state.kind.counts.last - state.kind.counts.first - 1,
                ) { state.count = it.roundToInt() }
            }
            Section("Tile shape") {
                Chips(TileRatio.entries, state.ratio, { it.label }) { state.ratio = it }
                Text(state.ratio.description, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Section("Crop") {
                SliderRow("Zoom", state.crop.zoom, 1f..FrameCrop.MAX_ZOOM, "${(state.crop.zoom * 100).roundToInt()} %") { state.setZoom(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Reset crop", enabled = state.image != null) { state.resetCrop() }
                }
            }
            Section("Filter") {
                Chips(PhotoFilter.entries, state.filter, { it.label }) { state.filter = it }
            }
            Section("Export") {
                Text(
                    "Each tile is saved as a ${state.ratio.width} × ${state.ratio.height} JPEG, named name_01.jpg, name_02.jpg … " +
                        if (state.kind == SplitKind.GRID) "in posting order." else "in swipe order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }
    }
}
