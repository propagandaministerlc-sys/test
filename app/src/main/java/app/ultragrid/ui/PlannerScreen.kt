package app.ultragrid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ultragrid.GRID_COLUMNS
import app.ultragrid.PlanItem
import app.ultragrid.PlannerViewModel
import app.ultragrid.Sharing
import app.ultragrid.rememberThumbnail

private const val MAX_PICK = 30

@Composable
fun PlannerScreen(vm: PlannerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris -> vm.add(uris) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Feed planner", onBack) {
            TextButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text("Add photos")
            }
        }
        Hint("Top-left is your newest post. Tap two photos to swap them; tap one to post, mark or remove it.")

        if (vm.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Add the photos you're planning to post to see how your profile will look.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(vm.items) { index, item ->
                    PlannerCell(item, selected = vm.selected == index, onClick = { vm.tap(index) })
                }
            }
        }

        val selected = vm.selected?.let { vm.items.getOrNull(it) }
        if (selected != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::togglePostedSelected, modifier = Modifier.weight(1f)) {
                    Text(if (selected.posted) "Unmark" else "Posted")
                }
                OutlinedButton(
                    onClick = { Sharing.toInstagram(context, selected.uri, "image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
                OutlinedButton(onClick = vm::removeSelected, modifier = Modifier.weight(1f)) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun PlannerCell(item: PlanItem, selected: Boolean, onClick: () -> Unit) {
    val thumbnail = rememberThumbnail(item.uri)
    Box(
        Modifier
            .aspectRatio(3f / 4f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (item.posted) 0.4f else 1f),
            )
        }
        if (item.posted) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Posted",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
    }
}
