package app.ultragrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyScreen(busy: Boolean, onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GridGlyph(Modifier.size(96.dp))
        Spacer(Modifier.height(24.dp))
        Text("UltraGrid", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Turn one photo into a seamless Instagram profile grid.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Choose a photo")
            }
        }
    }
}

@Composable
private fun GridGlyph(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val gap = size.width * 0.06f
        val cell = (size.width - gap * 2) / 3
        for (r in 0 until 3) for (c in 0 until 3) {
            drawRoundRect(
                color = color.copy(alpha = if ((r + c) % 2 == 0) 1f else 0.6f),
                topLeft = Offset(c * (cell + gap), r * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.18f),
            )
        }
    }
}
