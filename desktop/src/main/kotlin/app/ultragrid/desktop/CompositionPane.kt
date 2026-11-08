package app.ultragrid.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ultragrid.Axis
import app.ultragrid.CanvasMapping
import app.ultragrid.CompositionFormat
import app.ultragrid.CompositionStyle
import app.ultragrid.Divider
import app.ultragrid.FrameCrop
import app.ultragrid.LayoutFamily
import app.ultragrid.LayoutGeometry
import app.ultragrid.LayoutTemplate
import app.ultragrid.PhotoFilter
import app.ultragrid.SnapKind
import java.awt.Cursor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private val ResizeH = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
private val ResizeV = PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
private val MoveCursor = PointerIcon(Cursor(Cursor.MOVE_CURSOR))

@Composable
fun CompositionPane(state: CompositionState, onPickFor: (Int) -> Unit, onAddPhotos: () -> Unit, onExport: () -> Unit) {
    val c = Ultra.colors
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Composition", style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text(
                        "${state.template.family.label.uppercase()} / ${state.frameIds.size} FRAMES · ${state.format.label} ${state.format.caption.uppercase()}",
                        style = CaptionStyle,
                        color = c.muted,
                    )
                }
                Text("${two(state.filledCount)} / ${two(state.frameIds.size)}", style = MonoStyle, color = c.text)
                Spacer(Modifier.width(18.dp))
                GhostButton("Clear", enabled = state.hasPhotos) { state.clearAll() }
                Spacer(Modifier.width(8.dp))
                GhostButton("Add photos", enabled = !state.busy, onClick = onAddPhotos)
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Export JPEG", enabled = state.hasPhotos && !state.busy, onClick = onExport)
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionCanvas(
                    state = state,
                    onTapFrame = { frame ->
                        val from = state.swapFrom
                        when {
                            from != null -> state.swap(from, frame)
                            frame in state.photos -> state.select(frame)
                            else -> onPickFor(frame)
                        }
                    },
                    modifier = Modifier.aspectRatio(state.format.aspect),
                )
            }
            Text(
                if (state.hasPhotos) {
                    "Drag a photo to move it · Scroll to zoom · Drag the handles to resize frames · Drop images onto the window"
                } else {
                    "Click a frame to add a photo, or drop images onto the window"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
        CompositionPanel(state, onPickFor)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompositionCanvas(state: CompositionState, onTapFrame: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ultra.colors
    val measurer = rememberTextMeasurer()
    val reach = with(LocalDensity.current) { 10.dp.toPx() }
    val tap by rememberUpdatedState(onTapFrame)
    var hover by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val hoverIcon = run {
        val p = hover
        if (p == null || canvasSize == Size.Zero) {
            PointerIcon.Default
        } else {
            val mapping = CanvasMapping(canvasSize.width, canvasSize.height, state.style)
            val d = state.activeDivider ?: hitDivider(state.geometry, mapping, p, reach)
            when {
                d != null -> if (d.axis == Axis.ROW) ResizeH else ResizeV
                else -> {
                    val f = state.geometry.frameAt(mapping.toContentX(p.x), mapping.toContentY(p.y))
                    if (f != null && f in state.photos) MoveCursor else HandCursor
                }
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerHoverIcon(hoverIcon)
            .onPointerEvent(PointerEventType.Move) { hover = it.changes.first().position }
            .onPointerEvent(PointerEventType.Exit) { hover = null }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                val mapping = CanvasMapping(size.width.toFloat(), size.height.toFloat(), state.style)
                val frame = state.geometry.frameAt(mapping.toContentX(change.position.x), mapping.toContentY(change.position.y))
                if (frame != null && frame in state.photos) {
                    val r = mapping.frameRect(state.geometry.frames.getValue(frame))
                    state.transformFrame(frame, 0f, 0f, exp(-change.scrollDelta.y * 0.12f), r.width / r.height)
                    change.consume()
                }
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val mapping = CanvasMapping(size.width.toFloat(), size.height.toFloat(), state.style)
                    val geometry = state.geometry
                    val divider = hitDivider(geometry, mapping, down.position, reach)
                    if (divider != null) {
                        state.beginDividerDrag(divider)
                        down.consume()
                        drag(down.id) { change ->
                            val p = if (divider.axis == Axis.ROW) mapping.toContentX(change.position.x) else mapping.toContentY(change.position.y)
                            state.dragDivider(p)
                            hover = change.position
                            change.consume()
                        }
                        state.endDividerDrag()
                        return@awaitEachGesture
                    }
                    val frame = geometry.frameAt(mapping.toContentX(down.position.x), mapping.toContentY(down.position.y))
                    var moved = false
                    var travel = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val pan = event.calculatePan()
                        if (!moved) {
                            travel += pan
                            moved = travel.getDistance() > viewConfiguration.touchSlop
                        }
                        if (moved && frame != null && frame in state.photos) {
                            val rect = state.geometry.frames[frame] ?: break
                            val px = mapping.frameRect(rect)
                            state.transformFrame(frame, pan.x / px.width, pan.y / px.height, 1f, px.width / px.height)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                    if (!moved && frame != null) tap(frame)
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas {
                SkiaRender.drawComposition(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    state.geometry,
                    state.photos,
                    state.crops,
                    state.style,
                    state.filter,
                    placeholder = c.placeholder.toArgb(),
                )
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val mapping = CanvasMapping(size.width, size.height, state.style)
            val bg = Color(state.style.background)
            val lightBackground = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue > 0.5f
            val onEmpty = if (lightBackground) Color(0xFF66717B) else Color(0xFF8C939A)
            val numberStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

            state.geometry.frames.forEach { (id, r) ->
                val px = mapping.frameRect(r)
                if (px.width < 4f || px.height < 4f) return@forEach
                val hasPhoto = id in state.photos
                val isSelected = state.selected == id
                val pad = 10.dp.toPx()
                val color = when {
                    isSelected -> c.accent
                    hasPhoto -> Color.White
                    else -> onEmpty
                }
                val text = measurer.measure(
                    two(id + 1),
                    numberStyle.copy(color = color, shadow = if (hasPhoto && !isSelected) Shadow(Color(0x99000000), blurRadius = 6f) else null),
                )
                if (px.width > text.size.width + 2 * pad && px.height > text.size.height + 2 * pad) {
                    val y = if (hasPhoto) px.bottom - pad - text.size.height else px.top + pad
                    drawText(text, topLeft = Offset(px.left + pad, y))
                }
                if (!hasPhoto) {
                    val arm = minOf(px.width, px.height, 200f) * 0.08f
                    val stroke = 1.5.dp.toPx()
                    drawLine(onEmpty, Offset(px.centerX - arm, px.centerY), Offset(px.centerX + arm, px.centerY), stroke)
                    drawLine(onEmpty, Offset(px.centerX, px.centerY - arm), Offset(px.centerX, px.centerY + arm), stroke)
                }
                if (isSelected || state.swapFrom == id) {
                    val radius = minOf(mapping.radius, px.width / 2f, px.height / 2f)
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        color = c.accent,
                        topLeft = Offset(px.left + w / 2, px.top + w / 2),
                        size = Size(px.width - w, px.height - w),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(w, pathEffect = if (state.swapFrom == id) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null),
                    )
                }
            }

            val active = state.activeDivider
            val snap = state.snap
            if (active != null && snap != null && snap.kind != SnapKind.NONE) {
                val w = 1.dp.toPx()
                if (active.axis == Axis.ROW) {
                    val x = mapping.toPixelX(snap.position)
                    drawLine(c.accent, Offset(x, 0f), Offset(x, size.height), w)
                } else {
                    val y = mapping.toPixelY(snap.position)
                    drawLine(c.accent, Offset(0f, y), Offset(size.width, y), w)
                }
            }

            state.geometry.dividers.forEach { d ->
                val isActive = active != null && active.splitId == d.splitId && active.index == d.index
                val long = 32.dp.toPx()
                val short = 7.dp.toPx()
                val crossLength = if (d.axis == Axis.ROW) {
                    mapping.toPixelY(d.crossEnd) - mapping.toPixelY(d.crossStart)
                } else {
                    mapping.toPixelX(d.crossEnd) - mapping.toPixelX(d.crossStart)
                }
                if (crossLength < long + 16.dp.toPx()) return@forEach
                val cx = if (d.axis == Axis.ROW) mapping.toPixelX(d.position) else mapping.toPixelX((d.crossStart + d.crossEnd) / 2f)
                val cy = if (d.axis == Axis.ROW) mapping.toPixelY((d.crossStart + d.crossEnd) / 2f) else mapping.toPixelY(d.position)
                val sz = if (d.axis == Axis.ROW) Size(short, long) else Size(long, short)
                drawRoundRect(
                    color = if (isActive) c.accent else Color(0xFFF1F1EC),
                    topLeft = Offset(cx - sz.width / 2, cy - sz.height / 2),
                    size = sz,
                    cornerRadius = CornerRadius(short / 2),
                )
                val inner = Color(0xFF1E2A33)
                val stroke = 1.2.dp.toPx()
                if (d.axis == Axis.ROW) {
                    drawLine(inner, Offset(cx, cy - long * 0.28f), Offset(cx, cy + long * 0.28f), stroke)
                } else {
                    drawLine(inner, Offset(cx - long * 0.28f, cy), Offset(cx + long * 0.28f, cy), stroke)
                }
                if (isActive && snap != null && snap.kind != SnapKind.NONE) {
                    val tag = if (snap.kind == SnapKind.MATCH) "Match" else listOf("¼", "½", "¾")[snap.quarter - 1]
                    val t = measurer.measure(tag, numberStyle.copy(color = c.onAccent))
                    val padX = 7.dp.toPx()
                    val padY = 3.dp.toPx()
                    val bw = t.size.width + 2 * padX
                    val bh = t.size.height + 2 * padY
                    val bx = (cx - bw / 2).coerceIn(0f, size.width - bw)
                    val by = (if (d.axis == Axis.ROW) cy - long / 2 - bh - 6.dp.toPx() else cy - bh - short - 6.dp.toPx())
                        .coerceIn(0f, size.height - bh)
                    drawRoundRect(c.accent, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
                    drawText(t, topLeft = Offset(bx + padX, by + padY))
                }
            }
        }
    }
}

private fun hitDivider(geometry: LayoutGeometry, mapping: CanvasMapping, p: Offset, reach: Float): Divider? =
    geometry.dividers
        .mapNotNull { d ->
            val along: Float
            val inCross: Boolean
            if (d.axis == Axis.ROW) {
                along = mapping.toPixelX(d.position)
                inCross = p.y in mapping.toPixelY(d.crossStart)..mapping.toPixelY(d.crossEnd)
            } else {
                along = mapping.toPixelY(d.position)
                inCross = p.x in mapping.toPixelX(d.crossStart)..mapping.toPixelX(d.crossEnd)
            }
            val distance = abs((if (d.axis == Axis.ROW) p.x else p.y) - along)
            val gapReach = maxOf(reach, mapping.gap / 2f + 4f)
            if (distance <= gapReach && inCross) d to distance else null
        }
        .minByOrNull { it.second }
        ?.first

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompositionPanel(state: CompositionState, onPickFor: (Int) -> Unit) {
    val c = Ultra.colors
    var family by remember { mutableStateOf(state.template.family) }
    Column(
        Modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(c.panel)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        val selected = state.selected
        if (selected != null && selected in state.photos) {
            Section("Frame") {
                Text(two(selected + 1), style = MaterialTheme.typography.displaySmall, color = c.accent)
                Text("Independent crop", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("Drag to move. Scroll to zoom.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                val photo = state.photos.getValue(selected)
                val zoom = (state.crops[selected] ?: FrameCrop()).zoom
                SliderRow("Zoom", zoom, 1f..FrameCrop.MAX_ZOOM, "${(zoom * 100).roundToInt()} %") { z ->
                    val r = state.geometry.frames.getValue(selected)
                    val mapping = CanvasMapping(state.format.width.toFloat(), state.format.height.toFloat(), state.style)
                    val px = mapping.frameRect(r)
                    state.setZoom(selected, z, px.width / px.height)
                }
                Text("${photo.width} × ${photo.height} px", style = MonoStyle, color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostButton("Replace") { onPickFor(selected) }
                    GhostButton(if (state.swapFrom != null) "Pick target" else "Swap", highlighted = state.swapFrom != null) {
                        state.swapFrom = if (state.swapFrom == null) selected else null
                    }
                    GhostButton("Reset") { state.resetCrop(selected) }
                    GhostButton("Remove") { state.removePhoto(selected) }
                }
            }
        }

        Section("Layout") {
            Chips(LayoutFamily.entries, family, { it.label }) { family = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LayoutTemplate.entries.filter { it.family == family }.forEach { t ->
                    TemplateTile(t, t == state.template, state.format.aspect) { state.selectTemplate(t) }
                }
            }
        }

        Section("Format") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionFormat.entries.forEach { f -> FormatTile(f, f == state.format) { state.format = f } }
            }
        }

        Section("Spacing") {
            SliderRow("Frame gap", state.style.gap, 0f..CompositionStyle.MAX_GAP, "${(state.style.gap / CompositionStyle.MAX_GAP * 100).roundToInt()}") { v ->
                state.style = state.style.copy(gap = v)
            }
            Toggle("Outer border", state.style.outerBorder) { state.style = state.style.copy(outerBorder = it) }
        }

        Section("Corners") {
            SliderRow("Corner radius", state.style.cornerRadius, 0f..CompositionStyle.MAX_RADIUS, "${(state.style.cornerRadius / CompositionStyle.MAX_RADIUS * 100).roundToInt()}") { v ->
                state.style = state.style.copy(cornerRadius = v)
            }
        }

        Section("Background") {
            Swatches(CompositionStyle.BACKGROUNDS, state.style.background) { state.style = state.style.copy(background = it) }
        }

        Section("Filter") {
            Chips(PhotoFilter.entries, state.filter, { it.label }) { state.filter = it }
        }
    }
}

@Composable
private fun TemplateTile(template: LayoutTemplate, selected: Boolean, aspect: Float, onClick: () -> Unit) {
    val c = Ultra.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.panelHigh)
                .border(1.dp, if (selected) c.accent else c.hairline, RoundedCornerShape(14.dp))
                .pointerHoverIcon(HandCursor)
                .clickable(onClick = onClick)
                .padding(11.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) { drawTemplate(template, if (selected) c.accent else c.text, aspect) }
        }
        Text(template.label, style = MaterialTheme.typography.labelSmall, color = if (selected) c.accent else c.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FormatTile(format: CompositionFormat, selected: Boolean, onClick: () -> Unit) {
    val c = Ultra.colors
    Column(
        Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.panelHigh)
            .border(1.dp, if (selected) c.accent else c.hairline, RoundedCornerShape(14.dp))
            .pointerHoverIcon(HandCursor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val w = if (format.aspect >= 1f) 36.dp else 36.dp * format.aspect
            val h = if (format.aspect >= 1f) 36.dp / format.aspect else 36.dp
            Box(Modifier.width(w).height(h).border(1.5.dp, if (selected) c.accent else c.text, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.height(8.dp))
        Text(format.label, style = MaterialTheme.typography.titleSmall, color = if (selected) c.accent else c.text)
        Text(format.caption.uppercase(), style = CaptionStyle, color = c.muted)
    }
}
