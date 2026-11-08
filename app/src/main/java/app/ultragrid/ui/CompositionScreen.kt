package app.ultragrid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ultragrid.Axis
import app.ultragrid.CanvasMapping
import app.ultragrid.CompositionFormat
import app.ultragrid.CompositionRenderer
import app.ultragrid.CompositionStyle
import app.ultragrid.CompositionViewModel
import app.ultragrid.Divider
import app.ultragrid.LayoutFamily
import app.ultragrid.LayoutGeometry
import app.ultragrid.LayoutTemplate
import app.ultragrid.PhotoFilter
import app.ultragrid.Sharing
import app.ultragrid.SnapKind
import java.util.Locale
import kotlin.math.abs

private enum class CompositionTab(val label: String) { LAYOUT("Layout"), FORMAT("Format"), STYLE("Style"), FILTER("Filter") }

private fun two(n: Int) = String.format(Locale.US, "%02d", n)

@Composable
fun CompositionScreen(vm: CompositionViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var pendingFrame by rememberSaveable { mutableIntStateOf(-1) }
    var tab by rememberSaveable { mutableStateOf(CompositionTab.LAYOUT) }
    var swapFrom by remember { mutableStateOf<Int?>(null) }

    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pendingFrame >= 0) vm.setPhoto(pendingFrame, uri)
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        vm.addPhotos(uris)
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pickFor = { frame: Int ->
        pendingFrame = frame
        single.launch(imagesOnly)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Composition", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${vm.template.family.label.uppercase()} / ${vm.frameIds.size} FRAMES",
                    style = CaptionStyle,
                    color = Ultra.colors.muted,
                )
            }
            Text(
                "${two(vm.filledCount)} / ${two(vm.frameIds.size)}",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = vm::clearAll, enabled = vm.hasPhotos) { Text("Clear") }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionCanvas(
                vm = vm,
                swapFrom = swapFrom,
                onTapFrame = { frame ->
                    val from = swapFrom
                    when {
                        from != null -> {
                            vm.swap(from, frame)
                            swapFrom = null
                        }
                        frame in vm.photos -> vm.select(frame)
                        else -> pickFor(frame)
                    }
                },
                onSnap = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                modifier = Modifier.aspectRatio(vm.format.aspect),
            )
        }

        val selected = vm.selected
        if (selected != null && selected in vm.photos) {
            FrameBar(
                frame = selected,
                swapping = swapFrom != null,
                onReplace = { pickFor(selected) },
                onSwap = { swapFrom = if (swapFrom == null) selected else null },
                onReset = { vm.resetCrop(selected) },
                onRemove = { vm.removePhoto(selected) },
            )
        } else {
            Text(
                if (vm.hasPhotos) "Drag to move · Pinch to zoom · Drag the handles to resize" else "Tap a frame to add a photo",
                style = MaterialTheme.typography.bodySmall,
                color = Ultra.colors.muted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }

        TabStrip(tab, onSelect = { tab = it })
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 10.dp),
        ) {
            when (tab) {
                CompositionTab.LAYOUT -> LayoutPanel(vm)
                CompositionTab.FORMAT -> FormatPanel(vm)
                CompositionTab.STYLE -> StylePanel(vm)
                CompositionTab.FILTER -> ChoiceChips(PhotoFilter.entries, vm.filter, { it.label }, vm::selectFilter)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(
                onClick = { multiple.launch(imagesOnly) },
                enabled = !vm.busy,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Add photos") }
            OutlinedButton(
                onClick = { vm.save() },
                enabled = vm.hasPhotos && !vm.busy,
                modifier = Modifier.weight(0.8f).height(52.dp),
            ) { Text("Save") }
            Button(
                onClick = { vm.save { uri -> Sharing.toInstagram(context, uri) } },
                enabled = vm.hasPhotos && !vm.busy,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Share") }
        }
    }
}

/** Live preview with frame numbers, divider handles and snapping guides. */
@Composable
private fun CompositionCanvas(
    vm: CompositionViewModel,
    swapFrom: Int?,
    onTapFrame: (Int) -> Unit,
    onSnap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ultra.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reach = with(density) { 18.dp.toPx() }
    val tap by rememberUpdatedState(onTapFrame)
    val snapped by rememberUpdatedState(onSnap)

    Box(
        modifier.pointerInput(vm) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val mapping = CanvasMapping(size.width.toFloat(), size.height.toFloat(), vm.style)
                val geometry = vm.geometry

                val divider = hitDivider(geometry, mapping, down.position, reach)
                if (divider != null) {
                    vm.beginDividerDrag(divider)
                    down.consume()
                    drag(down.id) { change ->
                        val p = if (divider.axis == Axis.ROW) {
                            mapping.toContentX(change.position.x)
                        } else {
                            mapping.toContentY(change.position.y)
                        }
                        if (vm.dragDivider(p)) snapped()
                        change.consume()
                    }
                    vm.endDividerDrag()
                    return@awaitEachGesture
                }

                val frame = geometry.frameAt(mapping.toContentX(down.position.x), mapping.toContentY(down.position.y))
                var moved = false
                var travel = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (!moved) {
                        travel += pan
                        moved = travel.getDistance() > viewConfiguration.touchSlop || abs(zoom - 1f) > 0.02f
                    }
                    if (moved && frame != null && frame in vm.photos) {
                        val rect = vm.geometry.frames[frame] ?: break
                        val px = mapping.frameRect(rect)
                        vm.transformFrame(frame, pan.x / px.width, pan.y / px.height, zoom, px.width / px.height)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
                if (!moved && frame != null) tap(frame)
            }
        },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas {
                CompositionRenderer.draw(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    vm.geometry,
                    vm.photos,
                    vm.crops,
                    vm.style,
                    vm.filter,
                    placeholderColor = colors.placeholder.toArgb(),
                )
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val mapping = CanvasMapping(size.width, size.height, vm.style)
            val lightBackground = Color(vm.style.background).approxLuminance() > 0.5f
            val onEmpty = if (lightBackground) Color(0xFF66717B) else colors.muted
            val numberStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

            vm.geometry.frames.forEach { (id, r) ->
                val px = mapping.frameRect(r)
                if (px.width < 4f || px.height < 4f) return@forEach
                val hasPhoto = id in vm.photos
                val isSelected = vm.selected == id
                val pad = 8.dp.toPx()
                val label = two(id + 1)
                val color = when {
                    isSelected -> colors.accent
                    hasPhoto -> Color.White
                    else -> onEmpty
                }
                val text = measurer.measure(
                    label,
                    numberStyle.copy(
                        color = color,
                        shadow = if (hasPhoto && !isSelected) Shadow(Color(0x99000000), blurRadius = 6f) else null,
                    ),
                )
                if (px.width > text.size.width + 2 * pad && px.height > text.size.height + 2 * pad) {
                    val y = if (hasPhoto) px.bottom - pad - text.size.height else px.top + pad
                    drawText(text, topLeft = Offset(px.left + pad, y))
                }
                if (!hasPhoto) {
                    val arm = minOf(px.width, px.height, 160f) * 0.09f
                    val cx = px.centerX
                    val cy = px.centerY
                    val stroke = 1.6.dp.toPx()
                    drawLine(onEmpty, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke)
                    drawLine(onEmpty, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke)
                }
                if (isSelected || swapFrom == id) {
                    val radius = minOf(mapping.radius, px.width / 2f, px.height / 2f)
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        color = colors.accent,
                        topLeft = Offset(px.left + w / 2, px.top + w / 2),
                        size = Size(px.width - w, px.height - w),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(
                            width = w,
                            pathEffect = if (swapFrom == id) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                        ),
                    )
                }
            }

            // Snapping guide across the whole split, with ¼ / ½ / ¾ or "Match".
            val active = vm.activeDivider
            val snap = vm.snap
            if (active != null && snap != null && snap.kind != SnapKind.NONE) {
                val guide = colors.accent
                val w = 1.dp.toPx()
                if (active.axis == Axis.ROW) {
                    val x = mapping.toPixelX(snap.position)
                    drawLine(guide, Offset(x, 0f), Offset(x, size.height), w)
                } else {
                    val y = mapping.toPixelY(snap.position)
                    drawLine(guide, Offset(0f, y), Offset(size.width, y), w)
                }
            }

            // Handles on every divider.
            vm.geometry.dividers.forEach { d ->
                val isActive = active != null && active.splitId == d.splitId && active.index == d.index
                val long = 30.dp.toPx()
                val short = 7.dp.toPx()
                val crossLength = if (d.axis == Axis.ROW) {
                    mapping.toPixelY(d.crossEnd) - mapping.toPixelY(d.crossStart)
                } else {
                    mapping.toPixelX(d.crossEnd) - mapping.toPixelX(d.crossStart)
                }
                if (crossLength < long + 16.dp.toPx()) return@forEach
                val (cx, cy) = if (d.axis == Axis.ROW) {
                    mapping.toPixelX(d.position) to mapping.toPixelY((d.crossStart + d.crossEnd) / 2f)
                } else {
                    mapping.toPixelX((d.crossStart + d.crossEnd) / 2f) to mapping.toPixelY(d.position)
                }
                val sz = if (d.axis == Axis.ROW) Size(short, long) else Size(long, short)
                drawRoundRect(
                    color = if (isActive) colors.accent else Color(0xFFF1F1EC),
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
                    val t = measurer.measure(tag, numberStyle.copy(color = colors.onAccent))
                    val padX = 7.dp.toPx()
                    val padY = 3.dp.toPx()
                    val bw = t.size.width + 2 * padX
                    val bh = t.size.height + 2 * padY
                    val bx = (cx - bw / 2).coerceIn(0f, size.width - bw)
                    val by = (if (d.axis == Axis.ROW) cy - long / 2 - bh - 6.dp.toPx() else cy - bh - short - 6.dp.toPx())
                        .coerceIn(0f, size.height - bh)
                    drawRoundRect(colors.accent, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
                    drawText(t, topLeft = Offset(bx + padX, by + padY))
                }
            }
        }
    }
}

private fun Color.approxLuminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** The divider whose line is within [reach] px of [p], closest first. */
private fun hitDivider(geometry: LayoutGeometry, mapping: CanvasMapping, p: Offset, reach: Float): Divider? =
    geometry.dividers
        .mapNotNull { d ->
            val (along, cross, start, end) = if (d.axis == Axis.ROW) {
                listOf(mapping.toPixelX(d.position), p.y, mapping.toPixelY(d.crossStart), mapping.toPixelY(d.crossEnd))
            } else {
                listOf(mapping.toPixelY(d.position), p.x, mapping.toPixelX(d.crossStart), mapping.toPixelX(d.crossEnd))
            }
            val distance = abs((if (d.axis == Axis.ROW) p.x else p.y) - along)
            if (distance <= reach && cross in start..end) d to distance else null
        }
        .minByOrNull { it.second }
        ?.first

@Composable
private fun FrameBar(
    frame: Int,
    swapping: Boolean,
    onReplace: () -> Unit,
    onSwap: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Frame", style = MaterialTheme.typography.bodyMedium, color = Ultra.colors.muted)
        Spacer(Modifier.width(6.dp))
        Text(two(frame + 1), style = MaterialTheme.typography.titleLarge, color = Ultra.colors.accent)
        Spacer(Modifier.weight(1f))
        SmallAction("Replace", onReplace)
        SmallAction(if (swapping) "Pick target" else "Swap", onSwap, highlighted = swapping)
        SmallAction("Reset", onReset)
        SmallAction("Remove", onRemove)
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (highlighted) Ultra.colors.accent else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun TabStrip(selected: CompositionTab, onSelect: (CompositionTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ultra.colors.panel)
            .padding(4.dp),
    ) {
        CompositionTab.entries.forEach { t ->
            val isSelected = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) scheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LayoutPanel(vm: CompositionViewModel) {
    var family by rememberSaveable { mutableStateOf(vm.template.family) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceChips(LayoutFamily.entries, family, { it.label }) { family = it }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LayoutTemplate.entries.filter { it.family == family }.forEach { t ->
                TemplateTile(t, selected = t == vm.template, aspect = vm.format.aspect) { vm.selectTemplate(t) }
            }
        }
    }
}

@Composable
private fun TemplateTile(template: LayoutTemplate, selected: Boolean, aspect: Float, onClick: () -> Unit) {
    val colors = Ultra.colors
    val line = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panel)
                .border(1.dp, if (selected) colors.accent else colors.hairline, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) { drawTemplate(template, line, aspect) }
        }
        Text(
            template.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FormatPanel(vm: CompositionViewModel) {
    val colors = Ultra.colors
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompositionFormat.entries.forEach { f ->
            val selected = f == vm.format
            Column(
                Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.panel)
                    .border(1.dp, if (selected) colors.accent else colors.hairline, RoundedCornerShape(16.dp))
                    .clickable { vm.selectFormat(f) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    val boxW = if (f.aspect >= 1f) 40.dp else 40.dp * f.aspect
                    val boxH = if (f.aspect >= 1f) 40.dp / f.aspect else 40.dp
                    Box(
                        Modifier
                            .width(boxW)
                            .height(boxH)
                            .border(1.5.dp, if (selected) colors.accent else MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(f.label, style = MaterialTheme.typography.titleSmall, color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface)
                Text(f.caption.uppercase(), style = CaptionStyle.copy(fontSize = 10.sp), color = colors.muted)
            }
        }
    }
}

@Composable
private fun StylePanel(vm: CompositionViewModel) {
    val colors = Ultra.colors
    val style = vm.style
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LabeledSlider("Frame gap", style.gap, 0f..CompositionStyle.MAX_GAP) { v -> vm.updateStyle { it.copy(gap = v) } }
        LabeledSlider("Corner radius", style.cornerRadius, 0f..CompositionStyle.MAX_RADIUS) { v ->
            vm.updateStyle { it.copy(cornerRadius = v) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Outer border", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
            Switch(checked = style.outerBorder, onCheckedChange = { on -> vm.updateStyle { it.copy(outerBorder = on) } })
            Spacer(Modifier.weight(1f))
            CompositionStyle.BACKGROUNDS.forEach { bg ->
                val selected = style.background == bg
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(bg))
                        .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.hairline, CircleShape)
                        .clickable { vm.updateStyle { it.copy(background = bg) } },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(
            String.format(Locale.US, "%3d", (value / range.endInclusive * 100).toInt()),
            style = MonoStyle,
            color = Ultra.colors.muted,
            modifier = Modifier.width(36.dp).padding(start = 6.dp),
        )
    }
}
