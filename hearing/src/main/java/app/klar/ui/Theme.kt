package app.klar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Grey-scale palette modelled on the iOS system colours. */
@Immutable
data class KlarColors(
    val background: Color,
    val card: Color,
    val fill: Color,
    val fillStrong: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val thumb: Color,
    val sliderFill: Color,
    val segmentSelected: Color,
    val bar: Color,
    val isDark: Boolean,
)

private val Light = KlarColors(
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    fill = Color(0xFFE5E5EA),
    fillStrong = Color(0xFFD1D1D6),
    label = Color(0xFF1C1C1E),
    secondaryLabel = Color(0xFF6C6C70),
    tertiaryLabel = Color(0xFFAEAEB2),
    separator = Color(0xFFDCDCE0),
    accent = Color(0xFF1C1C1E),
    onAccent = Color(0xFFFFFFFF),
    thumb = Color(0xFFFFFFFF),
    sliderFill = Color(0xFF1C1C1E),
    segmentSelected = Color(0xFFFFFFFF),
    bar = Color(0xF2F9F9FB),
    isDark = false,
)

private val Dark = KlarColors(
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    fill = Color(0xFF2C2C2E),
    fillStrong = Color(0xFF3A3A3C),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF9E9EA4),
    tertiaryLabel = Color(0xFF636366),
    separator = Color(0xFF38383A),
    accent = Color(0xFFF2F2F7),
    onAccent = Color(0xFF000000),
    thumb = Color(0xFFFFFFFF),
    sliderFill = Color(0xFFA1A1A6),
    segmentSelected = Color(0xFF636366),
    bar = Color(0xF20F0F10),
    isDark = true,
)

/** Type scale modelled on the iOS text styles. */
@Immutable
data class KlarType(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
)

private val Type = KlarType(
    largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, lineHeight = 41.sp),
    title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em, lineHeight = 34.sp),
    title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, lineHeight = 25.sp),
    headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, lineHeight = 22.sp),
    body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em, lineHeight = 22.sp),
    callout = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em, lineHeight = 21.sp),
    subhead = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.005).em, lineHeight = 20.sp),
    footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

private val LocalKlarColors = staticCompositionLocalOf { Light }

object KlarTheme {
    val colors: KlarColors
        @Composable @ReadOnlyComposable get() = LocalKlarColors.current
    val type: KlarType
        @Composable @ReadOnlyComposable get() = Type
}

@Composable
fun KlarTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    val scheme = if (colors.isDark) {
        darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.card)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.card)
    }
    CompositionLocalProvider(LocalKlarColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
