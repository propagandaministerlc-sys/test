package app.klar.ui

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.klar.audio.AudioDevices
import app.klar.audio.Headphones

enum class Tab(val label: String, val icon: KlarIcon) {
    HEAR("Hören", KlarIcon.WAVEFORM),
    SOUND("Klang", KlarIcon.SLIDERS),
    MORE("Mehr", KlarIcon.INFO),
}

@Composable
fun KlarRoot() {
    val c = KlarTheme.colors
    var tab by rememberSaveable { mutableStateOf(Tab.HEAR) }
    var showTest by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        Crossfade(tab, animationSpec = tween(180), label = "tab") { current ->
            when (current) {
                Tab.HEAR -> HomeScreen()
                Tab.SOUND -> SoundScreen(onStartTest = { showTest = true })
                Tab.MORE -> MoreScreen()
            }
        }
        TabBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))

        AnimatedVisibility(
            visible = showTest,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200)),
        ) {
            HearingTestScreen(onClose = { showTest = false })
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = KlarTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.bar),
    ) {
        Separator(inset = false)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(TabBarHeight),
        ) {
            Tab.entries.forEach { t ->
                val isSelected = t == selected
                val tint = if (isSelected) c.label else c.tertiaryLabel
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(t) }
                        .semantics {
                            role = Role.Tab
                            this.selected = isSelected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Glyph(t.icon, tint, size = 24.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        style = KlarTheme.type.caption.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = tint,
                    )
                }
            }
        }
    }
}

/** Currently connected headphones, updated live when devices come and go. */
@Composable
fun rememberHeadphones(): Headphones? {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    var headphones by remember { mutableStateOf(AudioDevices.headphones(am)) }
    DisposableEffect(am) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                headphones = AudioDevices.headphones(am)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                headphones = AudioDevices.headphones(am)
            }
        }
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { am.unregisterAudioDeviceCallback(callback) }
    }
    return headphones
}
