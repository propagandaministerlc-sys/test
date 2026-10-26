package app.ultragrid.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ultragrid.GRID_COLUMNS
import app.ultragrid.GridViewModel
import app.ultragrid.SavedTile
import app.ultragrid.Sharing
import app.ultragrid.SplitMode
import app.ultragrid.TileExporter

@Composable
fun ResultScreen(vm: GridViewModel, onAddToPlanner: (List<Uri>) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (vm.mode == SplitMode.GRID) {
            GridResult(vm, onAddToPlanner)
        } else {
            CarouselResult(vm, onAddToPlanner)
        }
    }
}

@Composable
private fun ColumnScope.GridResult(vm: GridViewModel, onAddToPlanner: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val share = { tile: SavedTile ->
        Sharing.toInstagram(context, tile.uri)
        vm.markPosted(tile.postOrder)
    }

    ScreenHeader("Your grid", onBack = vm::backToEditor)
    Hint(
        "Saved to Pictures/${TileExporter.ALBUM}. Post the tiles in order, starting with #1. " +
            "Instagram puts the newest post top-left, so the picture builds up as you go. " +
            "Tip: your current post count should be a multiple of 3, or the grid will be shifted.",
        Modifier.padding(vertical = 8.dp),
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
                modifier = Modifier.aspectRatio(tile.thumbnail.width.toFloat() / tile.thumbnail.height),
            )
        }
    }

    Hint("Tap a tile to share it · long-press to mark posted/unposted", Modifier.padding(vertical = 8.dp))
    val next = vm.nextToPost
    Button(
        onClick = { next?.let(share) },
        enabled = next != null,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(if (next != null) "Post #${next.postOrder} to Instagram" else "All tiles posted")
    }
    OutlinedButton(
        onClick = { onAddToPlanner(vm.tiles.map { it.uri }) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text("Add to feed planner")
    }
}

@Composable
private fun ColumnScope.CarouselResult(vm: GridViewModel, onAddToPlanner: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val slides = vm.tiles.sortedBy { it.postOrder }

    ScreenHeader("Your carousel", onBack = vm::backToEditor)
    Hint(
        "Saved ${slides.size} slides to Pictures/${TileExporter.ALBUM}. Post them together as one " +
            "carousel, in order, and they'll swipe as one continuous picture.",
        Modifier.padding(vertical = 8.dp),
    )

    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(slides, key = { it.postOrder }) { slide ->
                TileCell(
                    tile = slide,
                    posted = false,
                    onClick = { Sharing.toInstagram(context, slide.uri) },
                    onLongClick = {},
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .aspectRatio(slide.thumbnail.width.toFloat() / slide.thumbnail.height),
                )
            }
        }
    }

    Button(
        onClick = { Sharing.carouselToInstagram(context, slides.map { it.uri }) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text("Post carousel to Instagram")
    }
    OutlinedButton(
        onClick = { slides.firstOrNull()?.let { onAddToPlanner(listOf(it.uri)) } },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text("Add cover to feed planner")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCell(
    tile: SavedTile,
    posted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Image(
            bitmap = tile.thumbnail,
            contentDescription = "Image ${tile.postOrder}",
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
