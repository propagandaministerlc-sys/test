package app.klar.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.klar.HearingRepository
import app.klar.audio.HearingService
import app.klar.audio.TonePlayer
import app.klar.model.Audiogram
import app.klar.model.Ear
import app.klar.model.HearingSettings
import app.klar.model.HearingTest
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Phase { INTRO, RUNNING, RESULT }

/** Full-screen hearing test: intro → 12 tone steps → result and suggested EQ. */
@Composable
fun HearingTestScreen(onClose: () -> Unit) {
    val c = KlarTheme.colors
    val context = LocalContext.current
    val headphones = rememberHeadphones()
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var result by remember { mutableStateOf<Audiogram?>(null) }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        // The amplifier would mask the test tones.
        if (HearingRepository.running.value) HearingService.stop(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            // Swallow touches so the screen below cannot be used through this overlay.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (phase == Phase.RESULT) "Schließen" else "Abbrechen",
                style = KlarTheme.type.body,
                color = c.label,
                modifier = Modifier.pressable(onClick = onClose),
            )
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "phase",
        ) { p ->
            when (p) {
                Phase.INTRO -> IntroPhase(canStart = headphones != null) { phase = Phase.RUNNING }
                Phase.RUNNING -> RunningPhase { a ->
                    result = a
                    phase = Phase.RESULT
                }
                Phase.RESULT -> result?.let { a ->
                    ResultPhase(
                        audiogram = a,
                        onApply = {
                            val (l, r) = HearingTest.suggestedEq(a)
                            HearingRepository.saveAudiogram(a)
                            HearingRepository.update { it.copy(eqLeft = l, eqRight = r) }
                            onClose()
                        },
                        onDiscard = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPhase(canStart: Boolean, onStart: () -> Unit) {
    val c = KlarTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(KlarIcon.EAR, c.onAccent, size = 38.dp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Hörtest", style = KlarTheme.type.largeTitle, color = c.label)
        Spacer(Modifier.height(8.dp))
        Text(
            "Klar spielt dir nacheinander Töne vor, die langsam lauter werden. So findet die App heraus, " +
                "welche Tonhöhen du schlechter hörst, und passt den Klang daran an.",
            style = KlarTheme.type.body,
            color = c.secondaryLabel,
        )
        Spacer(Modifier.height(24.dp))
        GroupCard {
            Step(1, "Setze beide AirPods ein.")
            Separator()
            Step(2, "Such dir einen ruhigen Ort.")
            Separator()
            Step(3, "Stell die Lautstärke des Telefons auf etwa die Hälfte und ändere sie während des Tests nicht.")
            Separator()
            Step(4, "Tippe, sobald du einen Ton hörst – auch wenn er sehr leise ist.")
        }
        Spacer(Modifier.height(32.dp))
        if (!canStart) {
            Text(
                "Verbinde zuerst deine Kopfhörer.",
                style = KlarTheme.type.footnote,
                color = c.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
        PrimaryButton("Test starten", enabled = canStart, onClick = onStart)
        Text(
            "Kein medizinischer Hörtest. Die Werte sind relativ zueinander.",
            style = KlarTheme.type.footnote,
            color = c.tertiaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val c = KlarTheme.colors
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(c.fill),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = KlarTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = KlarTheme.type.callout, color = c.label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RunningPhase(onFinished: (Audiogram) -> Unit) {
    val c = KlarTheme.colors
    val player = remember { TonePlayer() }
    DisposableEffect(player) { onDispose { player.close() } }

    var step by remember { mutableIntStateOf(0) }
    var tapTime by remember { mutableStateOf<Long?>(null) }
    var acknowledged by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val left = remember { FloatArray(HearingSettings.BANDS.size) }
    val right = remember { FloatArray(HearingSettings.BANDS.size) }

    val (ear, band) = HearingTest.STEPS[step]
    val hz = HearingSettings.BANDS[band]

    LaunchedEffect(step) {
        tapTime = null
        acknowledged = false
        listening = false
        val starts = mutableListOf<Pair<Long, Float>>()
        delay(900)
        listening = true
        var level = HearingTest.START_DB
        var threshold = HearingTest.NOT_HEARD_DB
        while (level <= HearingTest.MAX_DB + 0.01f) {
            val started = player.beep(hz, ear, level)
            starts += started to level
            val tap = tapTime
            if (tap != null) {
                threshold = levelHeardAt(starts, tap)
                break
            }
            level += HearingTest.STEP_DB
        }
        tapTime?.let { threshold = levelHeardAt(starts, it) }
        if (ear == Ear.LEFT) left[band] = threshold else right[band] = threshold
        listening = false
        acknowledged = true
        delay(500)
        if (step < HearingTest.STEPS.lastIndex) {
            step++
        } else {
            onFinished(Audiogram(left.toList(), right.toList(), System.currentTimeMillis()))
        }
    }

    val progress by animateFloatAsState((step + 1f) / HearingTest.STEPS.size, tween(400), label = "progress")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(c.fill, cornerRadius = r)
            drawRoundRect(c.sliderFill, size = Size(size.width * progress, size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Ton ${step + 1} von ${HearingTest.STEPS.size}",
            style = KlarTheme.type.footnote,
            color = c.secondaryLabel,
        )
        Spacer(Modifier.height(36.dp))
        Text(if (ear == Ear.LEFT) "Linkes Ohr" else "Rechtes Ohr", style = KlarTheme.type.title, color = c.label)
        Spacer(Modifier.height(4.dp))
        Text(String.format(Locale.GERMANY, "%,d Hz", hz), style = KlarTheme.type.body, color = c.secondaryLabel)

        Spacer(Modifier.weight(1f))
        HeardButton(acknowledged = acknowledged || tapTime != null) {
            // Taps before the first beep would be guesses, so they are ignored.
            if (listening && tapTime == null) tapTime = SystemClock.uptimeMillis()
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Hörst du nichts, warte einfach. Der Test geht dann von selbst weiter.",
            style = KlarTheme.type.footnote,
            color = c.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/**
 * The level the user reacted to: the last beep that started at least 250 ms
 * before the tap (Bluetooth delay plus reaction time).
 */
private fun levelHeardAt(starts: List<Pair<Long, Float>>, tap: Long): Float =
    starts.lastOrNull { it.first <= tap - 250 }?.second ?: starts.firstOrNull()?.second ?: HearingTest.NOT_HEARD_DB

@Composable
private fun HeardButton(acknowledged: Boolean, onClick: () -> Unit) {
    val c = KlarTheme.colors
    Box(
        Modifier
            .size(220.dp)
            .shadow(if (acknowledged) 0.dp else 16.dp, CircleShape)
            .clip(CircleShape)
            .background(if (acknowledged) c.accent else c.card)
            .pressable(pressedScale = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(
                if (acknowledged) KlarIcon.CHECK else KlarIcon.EAR,
                if (acknowledged) c.onAccent else c.label,
                size = 44.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (acknowledged) "Danke" else "Ich höre\nden Ton",
                style = KlarTheme.type.headline,
                color = if (acknowledged) c.onAccent else c.label,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultPhase(audiogram: Audiogram, onApply: () -> Unit, onDiscard: () -> Unit) {
    val c = KlarTheme.colors
    val (eqLeft, eqRight) = remember(audiogram) { HearingTest.suggestedEq(audiogram) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Dein Hörprofil", style = KlarTheme.type.largeTitle, color = c.label)
        Spacer(Modifier.height(8.dp))
        Text(
            "Je weiter unten ein Punkt liegt, desto schlechter hörst du diese Tonhöhe im Vergleich zu deiner besten.",
            style = KlarTheme.type.body,
            color = c.secondaryLabel,
        )
        Spacer(Modifier.height(20.dp))
        GroupCard {
            AudiogramChart(audiogram, Modifier.padding(16.dp))
        }
        SectionHeader("Vorgeschlagene Anhebung")
        GroupCard {
            EqSummaryRow("Links", eqLeft)
            Separator()
            EqSummaryRow("Rechts", eqRight)
        }
        SectionFooter("Klar hebt jede Tonhöhe um etwa die Hälfte des gemessenen Unterschieds an – so wie es Hörakustiker als Startpunkt machen.")
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Profil übernehmen", onClick = onApply)
        SecondaryButton("Verwerfen", Modifier.padding(bottom = 8.dp), onClick = onDiscard)
    }
}

@Composable
private fun EqSummaryRow(label: String, values: List<Float>) {
    val c = KlarTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KlarTheme.type.body, color = c.label, modifier = Modifier.width(64.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            HearingSettings.BANDS.forEachIndexed { i, hz ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+${values[i].toInt()}", style = KlarTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                    Text(bandLabel(hz), style = KlarTheme.type.caption, color = c.secondaryLabel)
                }
            }
        }
    }
}
