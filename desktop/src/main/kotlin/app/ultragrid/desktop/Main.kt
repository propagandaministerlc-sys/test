package app.ultragrid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private enum class Page(val title: String, val glyph: GlyphKind) {
    COMPOSITION("Composition", GlyphKind.COMPOSITION),
    GRID("Profile grid", GlyphKind.GRID),
    CAROUSEL("Carousel", GlyphKind.CAROUSEL),
}

fun main() = application {
    val icon = remember { loadIcon() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "UltraGrid",
        icon = icon,
        state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(1100, 720) }
        UltraGridDesktop(window)
    }
}

private fun loadIcon(): Painter {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream("ultragrid.png")!!.use { it.readBytes() }
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

@Composable
private fun UltraGridDesktop(window: ComposeWindow) {
    val scope = rememberCoroutineScope()
    val status = remember { StatusState() }
    val composition = remember { CompositionState(scope, status) }
    val grid = remember { SplitState(SplitKind.GRID, scope, status) }
    val carousel = remember { SplitState(SplitKind.CAROUSEL, scope, status) }
    var page by remember { mutableStateOf(Page.COMPOSITION) }
    var dark by remember { mutableStateOf(true) }

    // Images dropped onto the window go to the open tool.
    val onDrop by rememberUpdatedState { files: List<File> ->
        val images = files.filter(Dialogs::isImage)
        if (images.isEmpty()) {
            status.show("Only image files can be added")
        } else {
            when (page) {
                Page.COMPOSITION -> composition.addPhotos(images)
                Page.GRID -> grid.open(images.first())
                Page.CAROUSEL -> carousel.open(images.first())
            }
        }
    }
    DisposableEffect(window) {
        val target = object : DropTarget() {
            override fun drop(event: DropTargetDropEvent) {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                @Suppress("UNCHECKED_CAST")
                val files = runCatching {
                    event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                }.getOrDefault(emptyList())
                event.dropComplete(true)
                onDrop(files)
            }
        }
        window.contentPane.dropTarget = target
        onDispose { window.contentPane.dropTarget = null }
    }

    val message = status.message
    LaunchedEffect(status.serial) {
        if (message != null) {
            delay(5000)
            status.clear()
        }
    }

    UltraTheme(dark) {
        val c = Ultra.colors
        Row(Modifier.fillMaxSize().background(c.canvas)) {
            Sidebar(page, onSelect = { page = it }, dark = dark, onToggleTheme = { dark = !dark })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (page) {
                    Page.COMPOSITION -> CompositionPane(
                        composition,
                        onPickFor = { frame ->
                            Dialogs.openImages(window, multiple = false).firstOrNull()?.let { composition.setPhoto(frame, it) }
                        },
                        onAddPhotos = { composition.addPhotos(Dialogs.openImages(window, multiple = true)) },
                        onExport = {
                            Dialogs.saveJpeg(window, "Export composition", "ultragrid_${composition.template.family.label.lowercase()}.jpg")
                                ?.let(composition::export)
                        },
                    )
                    Page.GRID, Page.CAROUSEL -> {
                        val split = if (page == Page.GRID) grid else carousel
                        SplitPane(
                            split,
                            onOpen = { Dialogs.openImages(window, multiple = false).firstOrNull()?.let(split::open) },
                            onExport = {
                                val suggested = (split.fileName.ifBlank { "ultragrid" }) + if (page == Page.GRID) "_grid.jpg" else "_slide.jpg"
                                Dialogs.saveJpeg(window, "Export tiles", suggested)?.let(split::export)
                            },
                        )
                    }
                }
                if (message != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.text)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.canvas)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(page: Page, onSelect: (Page) -> Unit, dark: Boolean, onToggleTheme: () -> Unit) {
    val c = Ultra.colors
    Column(
        Modifier
            .width(250.dp)
            .fillMaxHeight()
            .background(c.panel)
            .padding(horizontal = 18.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Glyph(GlyphKind.COMPOSITION, Modifier.size(22.dp), c.text)
            Spacer(Modifier.width(10.dp))
            Text(
                "ULTRAGRID",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.02).em),
                color = c.text,
            )
        }
        Spacer(Modifier.height(36.dp))
        Text("TOOLS", style = CaptionStyle, color = c.muted, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(10.dp))
        Page.entries.forEachIndexed { index, p ->
            val selected = p == page
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) c.panelHigh else c.panel)
                    .pointerHoverIcon(HandCursor)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(p.glyph, Modifier.size(22.dp), if (selected) c.accent else c.text)
                Spacer(Modifier.width(12.dp))
                Text(p.title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
                Text(two(index + 1), style = MonoStyle, color = if (selected) c.accent else c.muted)
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                append("Built one frame\n")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("at a time.") }
            },
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 28.sp),
            color = c.text,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("v1.1", style = MonoStyle, color = c.muted, modifier = Modifier.weight(1f))
            GhostButton(if (dark) "Light" else "Dark", onClick = onToggleTheme)
        }
    }
}
