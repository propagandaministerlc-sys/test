package app.ultragrid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.ultragrid.GridViewModel

@Composable
fun UltraGridApp(vm: GridViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::load)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val snackbar = remember { SnackbarHostState() }
    val message = vm.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }

    BackHandler(enabled = vm.tiles.isNotEmpty()) { vm.backToEditor() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.tiles.isNotEmpty() -> ResultScreen(vm)
                vm.preview != null -> EditorScreen(vm, onPick = pick)
                else -> EmptyScreen(busy = vm.busy, onPick = pick)
            }
        }
    }
}
