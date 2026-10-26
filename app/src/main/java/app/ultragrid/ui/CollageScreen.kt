package app.ultragrid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ultragrid.CollageRenderer
import app.ultragrid.CollageTemplate
import app.ultragrid.CollageViewModel
import app.ultragrid.PhotoFilter
import app.ultragrid.Sharing
import app.ultragrid.TileRatio
import app.ultragrid.cellAt

@Composable
fun CollageScreen(vm: CollageViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var pendingCell by rememberSaveable { mutableIntStateOf(-1) }
    var styleTab by rememberSaveable { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pendingCell >= 0) vm.setPhoto(pendingCell, uri)
    }
    val pickFor = { index: Int ->
        pendingCell = index
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Collage", onBack) {
            TextButton(onClick = vm::clearPhotos, enabled = vm.photos.isNotEmpty()) { Text("Clear") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CollagePreview(
                vm = vm,
                onCellTap = pickFor,
                modifier = Modifier.aspectRatio(vm.ratio.width.toFloat() / vm.ratio.height),
            )
        }
        Hint("Tap a cell to choose its photo", Modifier.fillMaxWidth())

        TabRow(selectedTabIndex = styleTab) {
            listOf("Layout", "Style").forEachIndexed { index, title ->
                Tab(selected = styleTab == index, onClick = { styleTab = index }, text = { Text(title) })
            }
        }
        if (styleTab == 0) {
            SectionLabel("Layout")
            ChoiceChips(CollageTemplate.entries, vm.template, { it.label }, vm::updateTemplate)
            SectionLabel("Shape")
            ChoiceChips(TileRatio.entries, vm.ratio, { it.label }, vm::updateRatio)
        } else {
            SectionLabel("Filter")
            ChoiceChips(PhotoFilter.entries, vm.filter, { it.label }, vm::updateFilter)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Spacing")
                Slider(
                    value = vm.gap,
                    onValueChange = vm::updateGap,
                    valueRange = 0f..0.06f,
                    modifier = Modifier.weight(1f),
                )
                listOf(CollageRenderer.BACKGROUND_WHITE, CollageRenderer.BACKGROUND_BLACK).forEach { color ->
                    val selected = vm.background == color
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            )
                            .clickable { vm.updateBackground(color) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.save() },
                enabled = vm.hasPhotos && !vm.busy,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("Save")
            }
            Button(
                onClick = { vm.save { uri -> Sharing.toInstagram(context, uri) } },
                enabled = vm.hasPhotos && !vm.busy,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("Share to Instagram")
            }
        }
    }
}

@Composable
private fun CollagePreview(vm: CollageViewModel, onCellTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val template = vm.template
    Canvas(
        modifier.pointerInput(template) {
            detectTapGestures { position ->
                val index = template.cellAt(position.x / size.width, position.y / size.height)
                if (index >= 0) currentOnCellTap(index)
            }
        },
    ) {
        drawIntoCanvas {
            CollageRenderer.draw(
                it.nativeCanvas,
                size.width,
                size.height,
                vm.template,
                vm.photos,
                vm.gap,
                vm.background,
                vm.filter,
                showPlaceholders = true,
            )
        }
    }
}
