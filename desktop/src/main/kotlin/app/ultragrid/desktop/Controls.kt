package app.ultragrid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.awt.Cursor

val HandCursor = PointerIcon(Cursor(Cursor.HAND_CURSOR))

/** Panel section in the style of the brand slides: small caps title, content below. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(title.uppercase(), style = CaptionStyle, color = Ultra.colors.muted)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Chips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val c = Ultra.colors
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) c.text else c.panelHigh)
                    .border(1.dp, if (isSelected) c.text else c.hairline, RoundedCornerShape(10.dp))
                    .pointerHoverIcon(HandCursor)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) c.canvas else c.text,
                )
            }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val c = Ultra.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            Text(valueText, style = MonoStyle, color = c.muted)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = c.text,
                activeTrackColor = c.text,
                inactiveTrackColor = c.hairline,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.height(32.dp).pointerHoverIcon(HandCursor),
        )
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ultra.colors
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) c.text else c.panelHigh)
            .pointerHoverIcon(HandCursor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) c.canvas else c.muted)
    }
}

@Composable
fun GhostButton(text: String, enabled: Boolean = true, highlighted: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ultra.colors
    val color = when {
        !enabled -> c.muted
        highlighted -> c.accent
        else -> c.text
    }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (highlighted) c.accent else c.hairline, RoundedCornerShape(12.dp))
            .pointerHoverIcon(HandCursor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun Swatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ultra.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { bg ->
            val isSelected = bg == selected
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(bg))
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.accent else c.hairline, CircleShape)
                    .pointerHoverIcon(HandCursor)
                    .clickable { onSelect(bg) },
            )
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Ultra.colors
    Row(
        Modifier.fillMaxWidth().pointerHoverIcon(HandCursor).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(40.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(if (checked) c.accent else c.hairline)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
        }
    }
}

fun two(n: Int) = n.toString().padStart(2, '0')
