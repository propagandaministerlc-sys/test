package app.ultragrid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import app.ultragrid.CollageViewModel
import app.ultragrid.GridViewModel
import app.ultragrid.PlannerViewModel
import app.ultragrid.SplitMode

enum class Screen { HOME, GRID, CAROUSEL, COLLAGE, PLANNER }

class NavViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.HOME)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UltraGridApp(
    nav: NavViewModel,
    grid: GridViewModel,
    collage: CollageViewModel,
    planner: PlannerViewModel,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(grid::load)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val goHome = { nav.screen = Screen.HOME }

    val snackbar = remember { SnackbarHostState() }
    MessageEffect(grid.message, snackbar, grid::clearMessage)
    MessageEffect(collage.message, snackbar, collage::clearMessage)
    MessageEffect(planner.message, snackbar, planner::clearMessage)

    val editingSplit = nav.screen == Screen.GRID || nav.screen == Screen.CAROUSEL
    BackHandler(enabled = nav.screen != Screen.HOME) {
        if (editingSplit && grid.tiles.isNotEmpty()) grid.backToEditor() else goHome()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            when (nav.screen) {
                Screen.HOME -> HomeScreen(onOpen = { screen ->
                    when (screen) {
                        Screen.GRID -> grid.startMode(SplitMode.GRID)
                        Screen.CAROUSEL -> grid.startMode(SplitMode.CAROUSEL)
                        else -> Unit
                    }
                    nav.screen = screen
                })
                Screen.GRID, Screen.CAROUSEL -> when {
                    grid.tiles.isNotEmpty() -> ResultScreen(grid, onAddToPlanner = planner::add)
                    grid.preview != null -> EditorScreen(grid, onPick = pick, onBack = goHome)
                    else -> PickPhotoScreen(grid.mode, busy = grid.busy, onPick = pick, onBack = goHome)
                }
                Screen.COLLAGE -> CollageScreen(collage, onBack = goHome)
                Screen.PLANNER -> PlannerScreen(planner, onBack = goHome)
            }
        }
    }
}

@Composable
private fun PickPhotoScreen(mode: SplitMode, busy: Boolean, onPick: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader(mode.title, onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Glyph(if (mode == SplitMode.GRID) GlyphKind.GRID else GlyphKind.CAROUSEL, Modifier.size(96.dp))
            Text(
                if (mode == SplitMode.GRID) {
                    "Pick a photo to spread across your profile grid."
                } else {
                    "Pick a wide photo to turn into a seamless swipe."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Choose a photo")
                }
            }
        }
    }
}
