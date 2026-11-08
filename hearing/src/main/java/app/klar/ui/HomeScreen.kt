package app.klar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klar.HearingRepository
import app.klar.Meters
import app.klar.audio.Headphones
import app.klar.audio.HearingService
import app.klar.model.MicSource
import app.klar.model.Preset
import kotlin.math.roundToInt

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val settings by HearingRepository.settings.collectAsStateWithLifecycle()
    val running by HearingRepository.running.collectAsStateWithLifecycle()
    val meters by HearingRepository.meters.collectAsStateWithLifecycle()
    val error by HearingRepository.error.collectAsStateWithLifecycle()
    val headphones = rememberHeadphones()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            HearingService.start(context)
        } else {
            permissionDenied = true
        }
    }

    fun toggle() {
        if (running) {
            HearingService.stop(context)
            return
        }
        if (headphones == null) {
            HearingRepository.reportError("Verbinde zuerst deine AirPods oder andere Kopfhörer. Ohne Kopfhörer würde es pfeifen.")
            return
        }
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) HearingService.start(context) else permissionLauncher.launch(missing.toTypedArray())
    }

    val headsetLabel = if (headphones?.name?.contains("AirPods", ignoreCase = true) != false) "AirPods" else "Kopfhörer"

    Screen {
        LargeHeader("Hören", eyebrow = "Klar")

        DeviceChip(headphones, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))

        PowerButton(
            running = running,
            level = levelFraction(meters.outputDb),
            enabled = running || headphones != null,
            onClick = ::toggle,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        StatusText(running, meters, headphones, Modifier.align(Alignment.CenterHorizontally))

        if (permissionDenied) {
            MessageCard(
                text = "Klar braucht Zugriff auf das Mikrofon, um deine Umgebung zu verstärken.",
                actionLabel = "Einstellungen öffnen",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onDismiss = { permissionDenied = false },
            )
        }
        error?.let { MessageCard(it, onDismiss = { HearingRepository.reportError(null) }) }

        SectionHeader("Situation")
        SegmentedControl(
            options = Preset.entries.map { it.label },
            selectedIndex = settings.preset?.ordinal,
            onSelect = { i -> HearingRepository.update { it.withPreset(Preset.entries[i]) } },
        )
        SectionFooter(settings.preset?.description ?: "Eigene Einstellung. Wähle eine Situation, um zurückzusetzen.")

        SectionHeader("Anpassen")
        GroupCard {
            SliderRow(
                title = "Lautstärke",
                valueText = "+${settings.volumeDb.roundToInt()} dB",
                value = settings.volumeDb,
                onValueChange = { v -> HearingRepository.update { it.copy(volumeDb = v) } },
                valueRange = 0f..app.klar.model.HearingSettings.MAX_VOLUME_DB,
                step = 1f,
                startIcon = KlarIcon.SPEAKER_LOW,
                endIcon = KlarIcon.SPEAKER_HIGH,
            )
            Separator()
            SliderRow(
                title = "Stimmen hervorheben",
                valueText = percent(settings.voiceFocus),
                value = settings.voiceFocus,
                onValueChange = { v -> HearingRepository.update { it.copy(voiceFocus = v) } },
                step = 0.05f,
            )
            Separator()
            SliderRow(
                title = "Störgeräusche reduzieren",
                valueText = percent(settings.noiseReduction),
                value = settings.noiseReduction,
                onValueChange = { v -> HearingRepository.update { it.copy(noiseReduction = v) } },
                step = 0.05f,
            )
        }

        SectionHeader("Mikrofon")
        SegmentedControl(
            options = listOf("Telefon", headsetLabel),
            selectedIndex = settings.micSource.ordinal,
            onSelect = { i -> HearingRepository.update { it.copy(micSource = MicSource.entries[i]) } },
        )
        SectionFooter(
            when {
                settings.micSource == MicSource.PHONE ->
                    "Beste Klangqualität. Lege das Telefon nah an dein Gegenüber – es wirkt dann wie ein Funkmikrofon."
                headphones != null && !headphones.hasMic ->
                    "Deine Kopfhörer bieten gerade kein Mikrofon an. Verbinde sie neu oder nutze das Telefon-Mikrofon."
                else ->
                    "Nutzt die Mikrofone der $headsetLabel. Etwas weniger Verzögerung, aber Klang in Telefonqualität."
            },
        )

        AnimatedVisibility(
            visible = running,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                SectionHeader("Live")
                LiveCard(meters)
            }
        }
    }
}

private fun percent(v: Float) = "${(v * 100).roundToInt()} %"

/** Maps a dBFS level to 0..1 for meters. */
fun levelFraction(db: Float, floor: Float = -70f, top: Float = -10f) = ((db - floor) / (top - floor)).coerceIn(0f, 1f)

@Composable
private fun DeviceChip(headphones: Headphones?, modifier: Modifier = Modifier) {
    val c = KlarTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.card)
            .padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(KlarIcon.HEADPHONES, if (headphones != null) c.label else c.tertiaryLabel, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            headphones?.name ?: "Keine Kopfhörer verbunden",
            style = KlarTheme.type.subhead,
            color = if (headphones != null) c.label else c.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        if (headphones != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .background(c.label, CircleShape),
            )
        }
    }
}

@Composable
private fun PowerButton(
    running: Boolean,
    level: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KlarTheme.colors
    val animatedLevel by animateFloatAsState(if (running) level else 0f, tween(140), label = "level")
    val background by animateColorAsState(if (running) c.accent else c.card, tween(300), label = "bg")
    val foreground by animateColorAsState(
        when {
            running -> c.onAccent
            enabled -> c.label
            else -> c.tertiaryLabel
        },
        tween(300),
        label = "fg",
    )
    val transition = rememberInfiniteTransition(label = "breathe")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )

    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(250.dp)) {
            if (running) {
                val base = 80.dp.toPx()
                drawCircle(
                    c.label.copy(alpha = 0.06f + 0.10f * animatedLevel),
                    radius = base + 10.dp.toPx() + 30.dp.toPx() * animatedLevel,
                )
                drawCircle(
                    c.label.copy(alpha = 0.18f * (1f - breathe)),
                    radius = base + breathe * 44.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .size(160.dp)
                .shadow(if (running) 0.dp else 14.dp, CircleShape)
                .clip(CircleShape)
                .background(background)
                .pressable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = if (running) "Verstärkung stoppen" else "Verstärkung starten" },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(KlarIcon.POWER, foreground, size = 54.dp)
        }
    }
}

@Composable
private fun StatusText(running: Boolean, meters: Meters, headphones: Headphones?, modifier: Modifier = Modifier) {
    val c = KlarTheme.colors
    val (title, subtitle) = when {
        running && meters.speech > 0.5f -> "Aktiv" to "Stimme erkannt"
        running -> "Aktiv" to "Hört zu …"
        headphones == null -> "Aus" to "Verbinde deine AirPods, um zu starten"
        else -> "Aus" to "Tippe, um die Verstärkung zu starten"
    }
    Column(modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = KlarTheme.type.title3, color = c.label)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = KlarTheme.type.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LiveCard(meters: Meters) {
    val c = KlarTheme.colors
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MeterLine("Umgebung", levelFraction(meters.inputDb, floor = -80f, top = -20f))
            MeterLine("In deinen Ohren", levelFraction(meters.outputDb))
        }
        Separator()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stimme", style = KlarTheme.type.footnote, color = c.secondaryLabel)
                Text(
                    if (meters.speech > 0.5f) "Erkannt" else "–",
                    style = KlarTheme.type.headline,
                    color = c.label,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Geräusche gedämpft", style = KlarTheme.type.footnote, color = c.secondaryLabel)
                Text("−${meters.reductionDb.roundToInt()} dB", style = KlarTheme.type.headline, color = c.label)
            }
        }
    }
}

@Composable
private fun MeterLine(label: String, fraction: Float) {
    val c = KlarTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KlarTheme.type.subhead, color = c.label, modifier = Modifier.width(130.dp))
        LevelBar(fraction, Modifier.weight(1f))
    }
}
