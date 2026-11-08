package app.ultragrid.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ultragrid.R

/** Inter for text, Inter Display for headlines (tighter, made for large sizes). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

/** Brand colours that Material's scheme has no slot for. */
@Immutable
data class UltraColors(
    val accent: Color,
    val onAccent: Color,
    val canvas: Color,
    val panel: Color,
    val muted: Color,
    val hairline: Color,
    val placeholder: Color,
    val isDark: Boolean,
)

private val DarkUltra = UltraColors(
    accent = Color(0xFF6CD49C),
    onAccent = Color(0xFF06140C),
    canvas = Color(0xFF0A0C0E),
    panel = Color(0xFF15181B),
    muted = Color(0xFF8C939A),
    hairline = Color(0xFF262B30),
    placeholder = Color(0xFF1C2024),
    isDark = true,
)

private val LightUltra = UltraColors(
    accent = Color(0xFF2E9E66),
    onAccent = Color(0xFFFFFFFF),
    canvas = Color(0xFFF3F4F4),
    panel = Color(0xFFFFFFFF),
    muted = Color(0xFF66717B),
    hairline = Color(0xFFD9DDE1),
    placeholder = Color(0xFFE1E5EC),
    isDark = false,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF1F1EC),
    onPrimary = Color(0xFF0A0C0E),
    primaryContainer = Color(0xFF1F2328),
    onPrimaryContainer = Color(0xFFF1F1EC),
    secondary = Color(0xFF6CD49C),
    onSecondary = Color(0xFF06140C),
    secondaryContainer = Color(0xFFF1F1EC),
    onSecondaryContainer = Color(0xFF0A0C0E),
    tertiary = Color(0xFF6CD49C),
    background = Color(0xFF0A0C0E),
    onBackground = Color(0xFFF1F1EC),
    surface = Color(0xFF0A0C0E),
    onSurface = Color(0xFFF1F1EC),
    surfaceVariant = Color(0xFF1C2024),
    onSurfaceVariant = Color(0xFF8C939A),
    surfaceContainerLowest = Color(0xFF07090A),
    surfaceContainerLow = Color(0xFF111417),
    surfaceContainer = Color(0xFF15181B),
    surfaceContainerHigh = Color(0xFF1A1E21),
    surfaceContainerHighest = Color(0xFF15181B),
    outline = Color(0xFF3A4046),
    outlineVariant = Color(0xFF262B30),
    inverseSurface = Color(0xFFF1F1EC),
    inverseOnSurface = Color(0xFF0A0C0E),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E2A33),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E5EC),
    onPrimaryContainer = Color(0xFF1E2A33),
    secondary = Color(0xFF2E9E66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1E2A33),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF2E9E66),
    background = Color(0xFFF3F4F4),
    onBackground = Color(0xFF1E2A33),
    surface = Color(0xFFF3F4F4),
    onSurface = Color(0xFF1E2A33),
    surfaceVariant = Color(0xFFE1E5EC),
    onSurfaceVariant = Color(0xFF66717B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFB8C0C7),
    outlineVariant = Color(0xFFD9DDE1),
    inverseSurface = Color(0xFF1E2A33),
    inverseOnSurface = Color(0xFFF3F4F4),
)

private fun tight(size: Int, weight: FontWeight, spacing: Double, line: Int) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
)

private val UltraTypography = Typography(
    displayLarge = tight(52, FontWeight.Bold, -0.035, 54),
    displayMedium = tight(44, FontWeight.Bold, -0.035, 46),
    displaySmall = tight(36, FontWeight.Bold, -0.03, 39),
    headlineLarge = tight(32, FontWeight.Bold, -0.03, 35),
    headlineMedium = tight(28, FontWeight.Bold, -0.025, 31),
    headlineSmall = tight(24, FontWeight.Bold, -0.02, 28),
    titleLarge = tight(22, FontWeight.Bold, -0.02, 27),
    titleMedium = tight(17, FontWeight.SemiBold, -0.01, 22),
    titleSmall = tight(15, FontWeight.SemiBold, -0.005, 20),
    bodyLarge = tight(17, FontWeight.Normal, -0.01, 24),
    bodyMedium = tight(15, FontWeight.Normal, -0.005, 21),
    bodySmall = tight(13, FontWeight.Normal, 0.0, 18),
    labelLarge = tight(15, FontWeight.Medium, -0.005, 20),
    labelMedium = tight(13, FontWeight.Medium, 0.0, 17),
    labelSmall = tight(11, FontWeight.Medium, 0.02, 14),
)

/** Monospace for counters such as "01 / 06". */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 0.02.em)

/** Small caps-style captions such as "BENTO / 6 FRAMES". */
val CaptionStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.04.em)

private val LocalUltraColors = staticCompositionLocalOf { DarkUltra }

object Ultra {
    val colors: UltraColors
        @Composable @ReadOnlyComposable get() = LocalUltraColors.current
}

@Composable
fun UltraGridTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalUltraColors provides if (dark) DarkUltra else LightUltra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = UltraTypography,
            content = content,
        )
    }
}
