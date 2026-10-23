package app.ultragrid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ultragrid.GRID_COLUMNS
import app.ultragrid.GridViewModel
import app.ultragrid.SavedTile
import app.ultragrid.Sharing
import app.ultragrid.TileExporter

@Composable
fun ResultScreen(vm: GridViewModel) {
    val context = LocalContext.current
    val share = { tile: SavedTile ->
        Sharing.toInstagram(context, tile.uri)
        vm.markPosted(tile.postOrder)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::backToEditor) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to editor")
            }
            Text("Your grid", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            "Saved to Pictures/${TileExporter.ALBUM}. Post the tiles in order, starting with #1 — " +
                "Instagram puts the newest post top-left, so the picture assembles itself. " +
                "Tip: your current post count should be a multiple of 3 or the grid will be shifted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(vm.tiles, key = { it.postOrder }) { tile ->
                TileCell(
                    tile = tile,
                    posted = tile.postOrder in vm.posted,
                    onClick = { share(tile) },
                    onLongClick = { vm.togglePosted(tile.postOrder) },
                )
            }
        }

        Text(
            "Tap a tile to share it · long-press to mark posted/unposted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        val next = vm.nextToPost
        Button(
            onClick = { next?.let(share) },
            enabled = next != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (next != null) "Post #${next.postOrder} to Instagram" else "All tiles posted")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCell(tile: SavedTile, posted: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(tile.thumbnail.width.toFloat() / tile.thumbnail.height)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Image(
            bitmap = tile.thumbnail,
            contentDescription = "Tile ${tile.postOrder}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (posted) 0.35f else 1f),
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) {
            Text(
                "#${tile.postOrder}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (posted) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Posted",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
        }
    }
}
