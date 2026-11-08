package app.klar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klar.HearingRepository
import app.klar.model.Audiogram
import app.klar.model.HearingSettings
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SoundScreen(onStartTest: () -> Unit) {
    val settings by HearingRepository.settings.collectAsStateWithLifecycle()
    val audiogram by HearingRepository.audiogram.collectAsStateWithLifecycle()
    var earSelection by rememberSaveable { mutableIntStateOf(0) } // 0 both, 1 left, 2 right

    Screen {
        LargeHeader("Klang", eyebrow = "Klar")

        SectionHeader("Persönliches Profil")
        GroupCard {
            val a = audiogram
            NavRow(
                icon = KlarIcon.EAR,
                title = if (a == null) "Hörtest machen" else "Hörtest wiederholen",
                subtitle = if (a == null) {
                    "In etwa 3 Minuten herausfinden, welche Töne du schlechter hörst."
                } else {
                    "Zuletzt am ${formatDate(a.timestamp)}"
                },
                onClick = onStartTest,
            )
            if (a != null) {
                Separator()
                AudiogramChart(a, Modifier.padding(16.dp))
            }
        }

        SectionHeader("Equalizer")
        GroupCard {
            Column(Modifier.padding(16.dp)) {
                SegmentedControl(
                    options = listOf("Beide Ohren", "Links", "Rechts"),
                    selectedIndex = earSelection,
                    onSelect = { earSelection = it },
                )
                Spacer(Modifier.height(18.dp))
                val values = when (earSelection) {
                    1 -> settings.eqLeft
                    2 -> settings.eqRight
                    else -> settings.eqLeft.zip(settings.eqRight) { l, r -> (l + r) / 2f }
                }
                EqualizerBands(values) { band, v ->
                    HearingRepository.update { s ->
                        val left = if (earSelection != 2) s.eqLeft.replace(band, v) else s.eqLeft
                        val right = if (earSelection != 1) s.eqRight.replace(band, v) else s.eqRight
                        s.copy(eqLeft = left, eqRight = right)
                    }
                }
            }
        }
        SectionFooter("Hebe die Tonhöhen an, die du schlecht hörst. Hohe Töne (2–8 kHz) machen Sprache verständlich.")
        SecondaryButton("Equalizer zurücksetzen", Modifier.padding(top = 4.dp)) {
            HearingRepository.update { it.copy(eqLeft = HearingSettings.FLAT_EQ, eqRight = HearingSettings.FLAT_EQ) }
        }

        SectionHeader("Dynamik und Balance")
        GroupCard {
            SliderRow(
                title = "Leise Töne anheben",
                valueText = "${(settings.compression * 100).roundToInt()} %",
                value = settings.compression,
                onValueChange = { v -> HearingRepository.update { it.copy(compression = v) } },
                step = 0.05f,
                caption = "Macht Flüstern hörbar, ohne dass Lautes zu laut wird.",
            )
            Separator()
            SliderRow(
                title = "Balance",
                valueText = balanceText(settings.balance),
                value = settings.balance,
                onValueChange = { v -> HearingRepository.update { it.copy(balance = v) } },
                valueRange = -1f..1f,
                fillFrom = 0f,
                step = 0.05f,
            )
        }

        SectionHeader("System")
        GroupCard {
            SwitchRow(
                title = "Zusätzliche Rauschunterdrückung",
                subtitle = "Nutzt zusätzlich den Filter deines Telefons, falls vorhanden.",
                checked = settings.systemNoiseSuppressor,
                onCheckedChange = { on -> HearingRepository.update { it.copy(systemNoiseSuppressor = on) } },
            )
        }
    }
}

private fun List<Float>.replace(index: Int, value: Float) = mapIndexed { i, v -> if (i == index) value else v }

private fun balanceText(b: Float): String {
    val pct = (abs(b) * 100).roundToInt()
    return when {
        pct == 0 -> "Mitte"
        b < 0 -> "Links $pct %"
        else -> "Rechts $pct %"
    }
}

fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.GERMANY).format(Date(timestamp))

fun bandLabel(hz: Int) = if (hz >= 1000) "${hz / 1000}k" else "$hz"

@Composable
private fun EqualizerBands(values: List<Float>, onChange: (Int, Float) -> Unit) {
    val c = KlarTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        HearingSettings.BANDS.forEachIndexed { i, hz ->
            val v = values[i]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val rounded = v.roundToInt()
                Text(
                    if (rounded > 0) "+$rounded" else "$rounded",
                    style = KlarTheme.type.caption,
                    color = if (rounded == 0) c.secondaryLabel else c.label,
                )
                Spacer(Modifier.height(4.dp))
                KlarSlider(
                    value = v,
                    onValueChange = { onChange(i, it) },
                    contentDescription = "${bandLabel(hz)}Hz",
                    stateDescription = "$rounded Dezibel",
                    valueRange = HearingSettings.EQ_MIN_DB..HearingSettings.EQ_MAX_DB,
                    fillFrom = 0f,
                    step = 1f,
                    vertical = true,
                    modifier = Modifier.height(190.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(bandLabel(hz), style = KlarTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel)
            }
        }
    }
}

/**
 * Simplified audiogram: relative hearing loss per frequency, 0 dB at the top.
 * Left ear solid with dots, right ear dashed with crosses (as on clinical charts).
 */
@Composable
fun AudiogramChart(audiogram: Audiogram, modifier: Modifier = Modifier) {
    val c = KlarTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = KlarTheme.type.caption.copy(color = c.secondaryLabel)
    val left = audiogram.lossLeft
    val right = audiogram.lossRight
    val maxLoss = 60f

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp),
        ) {
            val axisWidth = 34.dp.toPx()
            val bottomPad = 22.dp.toPx()
            val plotW = size.width - axisWidth - 8.dp.toPx()
            val plotH = size.height - bottomPad - 8.dp.toPx()
            val top = 6.dp.toPx()
            fun x(i: Int) = axisWidth + plotW * i / (HearingSettings.BANDS.size - 1)
            fun y(loss: Float) = top + plotH * (loss.coerceIn(0f, maxLoss) / maxLoss)

            for (db in 0..60 step 20) {
                val yy = y(db.toFloat())
                drawLine(c.separator, Offset(axisWidth, yy), Offset(axisWidth + plotW, yy), strokeWidth = 1.dp.toPx())
                val t = measurer.measure("$db", labelStyle)
                drawText(t, topLeft = Offset(axisWidth - t.size.width - 8.dp.toPx(), yy - t.size.height / 2f))
            }
            HearingSettings.BANDS.forEachIndexed { i, hz ->
                val t = measurer.measure(bandLabel(hz), labelStyle)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2f, size.height - t.size.height))
            }

            val stroke = 2.dp.toPx()
            for (i in 0 until left.size - 1) {
                drawLine(c.label, Offset(x(i), y(left[i])), Offset(x(i + 1), y(left[i + 1])), strokeWidth = stroke)
                drawLine(
                    c.secondaryLabel, Offset(x(i), y(right[i])), Offset(x(i + 1), y(right[i + 1])),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                )
            }
            val r = 4.5.dp.toPx()
            left.forEachIndexed { i, l ->
                drawCircle(c.card, radius = r + stroke, center = Offset(x(i), y(l)))
                drawCircle(c.label, radius = r, center = Offset(x(i), y(l)), style = Stroke(stroke))
            }
            right.forEachIndexed { i, v ->
                val cx = x(i)
                val cy = y(v)
                drawLine(c.secondaryLabel, Offset(cx - r, cy - r), Offset(cx + r, cy + r), strokeWidth = stroke)
                drawLine(c.secondaryLabel, Offset(cx + r, cy - r), Offset(cx - r, cy + r), strokeWidth = stroke)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp)) {
                Canvas(Modifier.size(10.dp)) { drawCircle(c.label, style = Stroke(2.dp.toPx())) }
            }
            Spacer(Modifier.width(6.dp))
            Text("Links", style = KlarTheme.type.footnote, color = c.label)
            Spacer(Modifier.width(18.dp))
            Text("✕", style = KlarTheme.type.footnote, color = c.secondaryLabel)
            Spacer(Modifier.width(6.dp))
            Text("Rechts", style = KlarTheme.type.footnote, color = c.label)
            Spacer(Modifier.weight(1f))
            Text("dB Hörverlust (relativ)", style = KlarTheme.type.footnote, color = c.secondaryLabel)
        }
    }
}
