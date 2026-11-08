package app.ultragrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private data class Tool(val screen: Screen, val glyph: GlyphKind, val title: String, val subtitle: String)

private val Tools = listOf(
    Tool(Screen.GRID, GlyphKind.GRID, "Profile grid", "One photo across 3–15 posts that join up on your profile."),
    Tool(Screen.CAROUSEL, GlyphKind.CAROUSEL, "Carousel", "A wide photo cut into slides that swipe seamlessly."),
    Tool(Screen.PLANNER, GlyphKind.PLANNER, "Feed planner", "Arrange upcoming posts and preview your profile."),
)

/** Wordmark as on the brand slides: tight, bold, all caps. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        "ULTRAGRID",
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.02).em),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val colors = Ultra.colors
    val total = Tools.size + 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.weight(1f))
            Text("01 / ${String.format("%02d", total)}", style = MonoStyle, color = colors.muted)
        }
        Spacer(Modifier.height(36.dp))
        Text("Your media.\nYour composition.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Custom photo grids for Instagram.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )
        Spacer(Modifier.height(28.dp))

        // Hero: the composition editor.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.panel)
                .clickable { onOpen(Screen.COMPOSITION) }
                .padding(20.dp),
        ) {
            Row {
                Text("GRID / BENTO", style = CaptionStyle, color = colors.muted)
                Spacer(Modifier.weight(1f))
                Text("01", style = MonoStyle, color = colors.accent)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(colors.canvas)
                        .padding(24.dp),
                ) {
                    Glyph(GlyphKind.COMPOSITION, Modifier.fillMaxSize())
                }
            }
            Text("Composition", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Start with a grid. Make it yours. Resize frames, crop each photo, set gaps and corners.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.panel),
        ) {
            Tools.forEachIndexed { index, tool ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(start = 76.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.hairline),
                    )
                }
                ToolRow(number = index + 2, tool = tool) { onOpen(tool.screen) }
            }
        }

        Spacer(Modifier.height(44.dp))
        Text(
            buildAnnotatedString {
                append("Built one frame\n")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("at a time.") }
            },
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToolRow(number: Int, tool: Tool, onClick: () -> Unit) {
    val colors = Ultra.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Glyph(tool.glyph, Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.title, style = MaterialTheme.typography.titleMedium)
            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Spacer(Modifier.width(4.dp))
        Text(String.format("%02d", number), style = MonoStyle, color = colors.muted)
    }
}
