package app.ultragrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("UltraGrid", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Everything for a better-looking Instagram profile.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HomeCard(
            GlyphKind.GRID,
            "Profile grid",
            "Split one photo across 3–15 posts that join up on your profile.",
        ) { onOpen(Screen.GRID) }
        HomeCard(
            GlyphKind.CAROUSEL,
            "Carousel",
            "Cut a wide photo into 2–10 slides that swipe seamlessly.",
        ) { onOpen(Screen.CAROUSEL) }
        HomeCard(
            GlyphKind.COLLAGE,
            "Collage",
            "Combine several photos into one post with a layout.",
        ) { onOpen(Screen.COLLAGE) }
        HomeCard(
            GlyphKind.PLANNER,
            "Feed planner",
            "Arrange upcoming posts and preview your profile before posting.",
        ) { onOpen(Screen.PLANNER) }
    }
}

@Composable
private fun HomeCard(glyph: GlyphKind, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Glyph(glyph, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
