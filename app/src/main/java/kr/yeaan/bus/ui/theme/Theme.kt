package kr.yeaan.bus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.yeaan.bus.ui.settings.Accent
import kr.yeaan.bus.ui.settings.Appearance
import kr.yeaan.bus.ui.settings.UserSettings

val Navy: Color @Composable get() = MaterialTheme.colorScheme.primary
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Green: Color @Composable get() = MaterialTheme.colorScheme.secondary

/**
 * Colors of the stop-side arrival board. The board stays dark in both light and dark mode,
 * the way the electronic sign at a bus stop does, and the amber signal means "a bus is close".
 */
@Immutable
data class BoardColors(
    val panel: Color, val ink: Color, val muted: Color, val line: Color,
    val signal: Color, val onSignal: Color, val plate: Color, val onPlate: Color,
    /** Raised thumb of a segmented control. */
    val thumb: Color
)

fun boardColors(dark: Boolean) = BoardColors(
    panel = if (dark) Color(0xFF05090F) else Color(0xFF121A26),
    ink = Color(0xFFF2F5F9), muted = Color(0xFF9AA7B8),
    line = if (dark) Color(0xFF232D3B) else Color(0xFF2C3747),
    signal = Color(0xFFFFB547), onSignal = Color(0xFF2B1B00),
    plate = Color(0xFFF2F5F9), onPlate = Color(0xFF121A26),
    thumb = if (dark) Color(0xFF2C3848) else Color.White
)

val LocalBoard = staticCompositionLocalOf { boardColors(false) }
val Board: BoardColors @Composable get() = LocalBoard.current

fun accentColor(accent: Accent, dark: Boolean = false): Color = when (accent) {
    Accent.OCEAN -> if (dark) Color(0xFFA4C9FF) else Color(0xFF245AAF)
    Accent.FOREST -> if (dark) Color(0xFF8BD8BA) else Color(0xFF176B50)
    Accent.VIOLET -> if (dark) Color(0xFFD4BCFF) else Color(0xFF7147A8)
    Accent.AMBER -> if (dark) Color(0xFFFFBE91) else Color(0xFF9C4919)
}

/** Neutral paper and cards carry only a trace of the accent; the accent itself is for plates and actions. */
fun appColors(accent: Accent, dark: Boolean): ColorScheme {
    val primary = accentColor(accent, dark)
    val container = when (accent) {
        Accent.OCEAN -> if (dark) Color(0xFF1D3557) else Color(0xFFE3ECFA)
        Accent.FOREST -> if (dark) Color(0xFF183D32) else Color(0xFFDFF2E8)
        Accent.VIOLET -> if (dark) Color(0xFF3B2854) else Color(0xFFEEE5FA)
        Accent.AMBER -> if (dark) Color(0xFF4D3020) else Color(0xFFFCE9DA)
    }
    val backdrop = when (accent) {
        Accent.OCEAN -> if (dark) Color(0xFF0D131B) else Color(0xFFF1F4F8)
        Accent.FOREST -> if (dark) Color(0xFF0D1512) else Color(0xFFF1F5F3)
        Accent.VIOLET -> if (dark) Color(0xFF120F18) else Color(0xFFF4F2F7)
        Accent.AMBER -> if (dark) Color(0xFF16120E) else Color(0xFFF7F4F1)
    }
    val card = when (accent) {
        Accent.OCEAN -> if (dark) Color(0xFF18212D) else Color.White
        Accent.FOREST -> if (dark) Color(0xFF17231E) else Color.White
        Accent.VIOLET -> if (dark) Color(0xFF211B2A) else Color.White
        Accent.AMBER -> if (dark) Color(0xFF251E18) else Color.White
    }
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = Color(0xFF151B23), primaryContainer = container,
        onPrimaryContainer = primary, secondary = primary, onSecondary = Color(0xFF151B23),
        background = backdrop, onBackground = Color(0xFFE8EDF4),
        surface = card, onSurface = Color(0xFFE8EDF4),
        surfaceVariant = backdrop, onSurfaceVariant = Color(0xFFA3AFBF),
        surfaceContainerLowest = backdrop, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = Color(0xFF242E3B), surfaceContainerHighest = Color(0xFF2C3848),
        outline = Color(0xFF7D8A9B), outlineVariant = Color(0xFF2A3442)
    ) else lightColorScheme(
        primary = primary, onPrimary = Color.White, primaryContainer = container,
        onPrimaryContainer = primary, secondary = primary, onSecondary = Color.White,
        background = backdrop, onBackground = Color(0xFF16202B),
        surface = card, onSurface = Color(0xFF16202B),
        surfaceVariant = Color(0xFFE4E8EE), onSurfaceVariant = Color(0xFF5A6675),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFEBEEF2), surfaceContainerHighest = Color(0xFFE4E8EE),
        outline = Color(0xFF8897A9), outlineVariant = Color(0xFFE1E6EC), error = Color(0xFFAB4038)
    )
}

private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp)
)

@Composable
fun YeaAnTheme(settings: UserSettings = UserSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * settings.textSize.scale),
        LocalBoard provides boardColors(dark)
    ) {
        MaterialTheme(colorScheme = appColors(settings.accent, dark), typography = Type,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
            content = content)
    }
}
