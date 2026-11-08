package app.ultragrid.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Inter, loaded from the font files shared with the Android app. */
val Inter = FontFamily(
    Font(resource = "inter_regular.ttf", weight = FontWeight.Normal),
    Font(resource = "inter_medium.ttf", weight = FontWeight.Medium),
    Font(resource = "inter_semibold.ttf", weight = FontWeight.SemiBold),
    Font(resource = "inter_display_bold.ttf", weight = FontWeight.Bold),
)

@Immutable
data class UltraColors(
    val accent: Color,
    val onAccent: Color,
    val canvas: Color,
    val panel: Color,
    val panelHigh: Color,
    val text: Color,
    val muted: Color,
    val hairline: Color,
    val placeholder: Color,
    val isDark: Boolean,
)

private val DarkUltra = UltraColors(
    accent = Color(0xFF6CD49C),
    onAccent = Color(0xFF06140C),
    canvas = Color(0xFF0A0C0E),
    panel = Color(0xFF111417),
    panelHigh = Color(0xFF1A1E21),
    text = Color(0xFFF1F1EC),
    muted = Color(0xFF8C939A),
    hairline = Color(0xFF23282D),
    placeholder = Color(0xFF1C2024),
    isDark = true,
)

private val LightUltra = UltraColors(
    accent = Color(0xFF2E9E66),
    onAccent = Color(0xFFFFFFFF),
    canvas = Color(0xFFE9ECEF),
    panel = Color(0xFFF6F7F7),
    panelHigh = Color(0xFFFFFFFF),
    text = Color(0xFF1E2A33),
    muted = Color(0xFF66717B),
    hairline = Color(0xFFD9DDE1),
    placeholder = Color(0xFFDCE1E7),
    isDark = false,
)

private fun style(size: Int, weight: FontWeight, spacing: Double, line: Int) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
)

private val UltraTypography = Typography(
    displaySmall = style(36, FontWeight.Bold, -0.03, 39),
    headlineMedium = style(28, FontWeight.Bold, -0.025, 31),
    headlineSmall = style(24, FontWeight.Bold, -0.02, 28),
    titleLarge = style(22, FontWeight.Bold, -0.02, 27),
    titleMedium = style(16, FontWeight.SemiBold, -0.01, 21),
    titleSmall = style(14, FontWeight.SemiBold, -0.005, 19),
    bodyLarge = style(16, FontWeight.Normal, -0.01, 23),
    bodyMedium = style(14, FontWeight.Normal, -0.005, 20),
    bodySmall = style(12, FontWeight.Normal, 0.0, 17),
    labelLarge = style(14, FontWeight.Medium, -0.005, 19),
    labelMedium = style(12, FontWeight.Medium, 0.0, 16),
    labelSmall = style(11, FontWeight.Medium, 0.02, 14),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 0.02.em)
val CaptionStyle = TextStyle(fontFamily = Inter, fontSize = 11.sp, letterSpacing = 0.06.em)

private val LocalUltra = staticCompositionLocalOf { DarkUltra }

object Ultra {
    val colors: UltraColors
        @Composable @ReadOnlyComposable get() = LocalUltra.current
}

@Composable
fun UltraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkUltra else LightUltra
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.text, onPrimary = c.canvas,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.text, onSecondaryContainer = c.canvas,
            background = c.canvas, onBackground = c.text,
            surface = c.panel, onSurface = c.text,
            surfaceVariant = c.panelHigh, onSurfaceVariant = c.muted,
            surfaceContainerHighest = c.panelHigh,
            outline = Color(0xFF3A4046), outlineVariant = c.hairline,
        )
    } else {
        lightColorScheme(
            primary = c.text, onPrimary = Color.White,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.text, onSecondaryContainer = Color.White,
            background = c.canvas, onBackground = c.text,
            surface = c.panel, onSurface = c.text,
            surfaceVariant = c.panelHigh, onSurfaceVariant = c.muted,
            surfaceContainerHighest = c.panelHigh,
            outline = Color(0xFFB8C0C7), outlineVariant = c.hairline,
        )
    }
    CompositionLocalProvider(LocalUltra provides c) {
        MaterialTheme(colorScheme = scheme, typography = UltraTypography, content = content)
    }
}
