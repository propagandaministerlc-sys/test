package app.klar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** iOS-style press feedback: the element dims and shrinks slightly instead of a ripple. */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, label = "pressScale")
    val alpha by animateFloatAsState(if (pressed) 0.7f else 1f, label = "pressAlpha")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

/** Scrollable page with room for the status bar and the tab bar. */
@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    val nav = WindowInsets.navigationBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = nav.calculateBottomPadding() + TabBarHeight + 28.dp),
        content = content,
    )
}

@Composable
fun LargeHeader(title: String, eyebrow: String? = null) {
    val c = KlarTheme.colors
    Column(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp)) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = KlarTheme.type.caption, color = c.secondaryLabel)
        }
        Text(title, style = KlarTheme.type.largeTitle, color = c.label)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = KlarTheme.type.footnote,
        color = KlarTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
fun SectionFooter(text: String) {
    Text(
        text,
        style = KlarTheme.type.footnote,
        color = KlarTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
    )
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KlarTheme.colors.card),
        content = content,
    )
}

@Composable
fun Separator(inset: Boolean = true) {
    Box(
        Modifier
            .padding(start = if (inset) 16.dp else 0.dp)
            .fillMaxWidth()
            .height(0.6.dp)
            .background(KlarTheme.colors.separator),
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = KlarTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) c.accent else c.fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KlarTheme.type.headline, color = if (enabled) c.onAccent else c.tertiaryLabel)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(pressedScale = 0.98f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KlarTheme.type.headline.copy(fontWeight = FontWeight.Medium), color = KlarTheme.colors.label)
    }
}

/** iOS-style switch in grey tones. */
@Composable
fun KlarSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = KlarTheme.colors
    val knobOffset by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    val track by animateColorAsState(if (checked) c.accent else c.fillStrong, label = "track")
    val knob by animateColorAsState(
        if (checked && c.isDark) c.onAccent else c.thumb,
        label = "knobColor",
    )
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(knob, CircleShape),
        )
    }
}

/** iOS-style segmented control with a sliding selection pill. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KlarTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.fill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            segment * (selectedIndex ?: 0),
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        if (selectedIndex != null) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(segment)
                    .fillMaxHeight()
                    .shadow(if (c.isDark) 0.dp else 3.dp, RoundedCornerShape(9.dp))
                    .background(c.segmentSelected, RoundedCornerShape(9.dp)),
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val isSelected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) }
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = KlarTheme.type.subhead.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = c.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Slider with a thin track and a round white thumb, horizontal or vertical.
 * @param fillFrom value the filled part starts from (e.g. 0 for a ±dB slider)
 * @param step snap increment, 0 for continuous
 */
@Composable
fun KlarSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    fillFrom: Float = valueRange.start,
    step: Float = 0f,
    vertical: Boolean = false,
    stateDescription: String? = null,
) {
    val c = KlarTheme.colors
    val thumbSize = 28.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }
    var lengthPx by remember { mutableFloatStateOf(0f) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val onChange by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start

    fun fractionOf(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun snap(v: Float): Float {
        val s = if (step > 0f) {
            valueRange.start + ((v - valueRange.start) / step).roundToInt() * step
        } else {
            v
        }
        return s.coerceIn(valueRange.start, valueRange.endInclusive)
    }
    fun valueAt(pos: Float): Float {
        val usable = (lengthPx - thumbPx).coerceAtLeast(1f)
        var f = ((pos - thumbPx / 2f) / usable).coerceIn(0f, 1f)
        if (vertical) f = 1f - f
        return snap(valueRange.start + f * span)
    }

    val dragState = rememberDraggableState { delta ->
        dragPos += delta
        onChange(valueAt(dragPos))
    }
    val fraction = fractionOf(value)
    val fillFraction = fractionOf(fillFrom)

    Box(
        modifier
            .then(if (vertical) Modifier.width(44.dp) else Modifier.fillMaxWidth().height(44.dp))
            .onSizeChanged { lengthPx = if (vertical) it.height.toFloat() else it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                if (stateDescription != null) this.stateDescription = stateDescription
                setProgress { target ->
                    onChange(snap(target))
                    true
                }
            }
            .pointerInput(vertical) {
                detectTapGestures { o -> onChange(valueAt(if (vertical) o.y else o.x)) }
            }
            .draggable(
                state = dragState,
                orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
                onDragStarted = { o ->
                    dragPos = if (vertical) o.y else o.x
                    onChange(valueAt(dragPos))
                },
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val thickness = 5.dp.toPx()
            val start = thumbPx / 2f
            val usable = (if (vertical) size.height else size.width) - thumbPx
            val radius = CornerRadius(thickness / 2f)
            val a = minOf(fraction, fillFraction)
            val b = maxOf(fraction, fillFraction)
            if (vertical) {
                val x = size.width / 2f - thickness / 2f
                drawRoundRect(c.fill, Offset(x, start), Size(thickness, usable), radius)
                // Vertical: top is the maximum.
                val top = start + (1f - b) * usable
                val bottom = start + (1f - a) * usable
                drawRoundRect(c.sliderFill, Offset(x, top), Size(thickness, bottom - top), radius)
            } else {
                val y = size.height / 2f - thickness / 2f
                drawRoundRect(c.fill, Offset(start, y), Size(usable, thickness), radius)
                drawRoundRect(c.sliderFill, Offset(start + a * usable, y), Size((b - a) * usable, thickness), radius)
            }
        }
        val travel = (lengthPx - thumbPx).coerceAtLeast(0f) * (if (vertical) 1f - fraction else fraction)
        Box(
            Modifier
                .align(if (vertical) Alignment.TopCenter else Alignment.CenterStart)
                .offset { if (vertical) IntOffset(0, travel.roundToInt()) else IntOffset(travel.roundToInt(), 0) }
                .size(thumbSize)
                .shadow(4.dp, CircleShape)
                .background(c.thumb, CircleShape),
        )
    }
}

/** Title + value on one line, slider underneath, optional icons at both ends. */
@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    fillFrom: Float = valueRange.start,
    step: Float = 0f,
    startIcon: KlarIcon? = null,
    endIcon: KlarIcon? = null,
    caption: String? = null,
) {
    val c = KlarTheme.colors
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = KlarTheme.type.body, color = c.label, modifier = Modifier.weight(1f))
            Text(valueText, style = KlarTheme.type.body, color = c.secondaryLabel)
        }
        if (caption != null) {
            Text(caption, style = KlarTheme.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (startIcon != null) {
                Glyph(startIcon, c.secondaryLabel, size = 18.dp)
                Spacer(Modifier.width(10.dp))
            }
            KlarSlider(
                value = value,
                onValueChange = onValueChange,
                contentDescription = title,
                stateDescription = valueText,
                valueRange = valueRange,
                fillFrom = fillFrom,
                step = step,
                modifier = Modifier.weight(1f),
            )
            if (endIcon != null) {
                Spacer(Modifier.width(10.dp))
                Glyph(endIcon, c.secondaryLabel, size = 20.dp)
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = KlarTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KlarTheme.type.body, color = c.label)
            if (subtitle != null) {
                Text(subtitle, style = KlarTheme.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        KlarSwitch(checked, onCheckedChange)
    }
}

/** Icon in a rounded grey tile, as in iOS settings lists. */
@Composable
fun IconTile(icon: KlarIcon, modifier: Modifier = Modifier, filled: Boolean = false) {
    val c = KlarTheme.colors
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (filled) c.accent else c.fill),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, if (filled) c.onAccent else c.label, size = 20.dp)
    }
}

@Composable
fun NavRow(icon: KlarIcon, title: String, subtitle: String?, onClick: () -> Unit) {
    val c = KlarTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, filled = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = KlarTheme.type.headline, color = c.label)
            if (subtitle != null) {
                Text(subtitle, style = KlarTheme.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Glyph(KlarIcon.CHEVRON_RIGHT, c.tertiaryLabel, size = 16.dp)
    }
}

@Composable
fun TipRow(icon: KlarIcon, title: String, text: String) {
    val c = KlarTheme.colors
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = KlarTheme.type.headline, color = c.label)
            Text(text, style = KlarTheme.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Horizontal level meter. */
@Composable
fun LevelBar(fraction: Float, modifier: Modifier = Modifier) {
    val c = KlarTheme.colors
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMedium), label = "level")
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(c.fill, cornerRadius = r)
        if (f > 0.001f) drawRoundRect(c.sliderFill, size = Size(size.width * f, size.height), cornerRadius = r)
    }
}

@Composable
fun MessageCard(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val c = KlarTheme.colors
    GroupCard(Modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Glyph(KlarIcon.INFO, c.label, size = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = KlarTheme.type.subhead, color = c.label)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (actionLabel != null && onAction != null) {
                        Text(
                            actionLabel,
                            style = KlarTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = c.label,
                            modifier = Modifier.pressable(onClick = onAction),
                        )
                    }
                    Text(
                        "Schließen",
                        style = KlarTheme.type.subhead,
                        color = c.secondaryLabel,
                        modifier = Modifier.pressable(onClick = onDismiss),
                    )
                }
            }
        }
    }
}

val TabBarHeight = 58.dp
