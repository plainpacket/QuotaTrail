package app.quotatrail.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class QuotaTrailColorPalette(
    val isDark: Boolean,
    val primary: Color, val secondary: Color, val tertiary: Color,
    val neutral: Color, val neutralAlt: Color, val surface: Color, val surfaceSoft: Color,
    val border: Color, val accent: Color, val accentSoft: Color,
    val surfaceRaised: Color, val surfaceSunken: Color, val routeLine: Color,
    val signalBlue: Color, val signalMint: Color, val signalAmber: Color, val shadow: Color,
    val success: Color, val successSoft: Color,
    val warning: Color, val warningSoft: Color,
    val danger: Color, val dangerSoft: Color,
)

val LightTrailPalette = QuotaTrailColorPalette(
    isDark = false,
    primary = Color(0xFF1A1C1E), secondary = Color(0xFF5F6065), tertiary = Color(0xFF7A797F),
    neutral = Color(0xFFF4F1EA), neutralAlt = Color(0xFFE8E3D9), surface = Color(0xFFFFFCF6), surfaceSoft = Color(0xFFF7F3EB),
    border = Color(0xFFD1CBC0), accent = Color(0xFF3154D5), accentSoft = Color(0xFFE3E7FF),
    surfaceRaised = Color(0xFFFFFEFA), surfaceSunken = Color(0xFFEDE8DE), routeLine = Color(0xFFB5AFA5),
    signalBlue = Color(0xFF3154D5), signalMint = Color(0xFF1F8A70), signalAmber = Color(0xFFE7852F), shadow = Color(0xFF302E29),
    success = Color(0xFF1F7A63), successSoft = Color(0xFFDDF2E9),
    warning = Color(0xFFB96517), warningSoft = Color(0xFFFFEBCF),
    danger = Color(0xFFB54444), dangerSoft = Color(0xFFFFE1DF),
)

val DarkTrailPalette = QuotaTrailColorPalette(
    isDark = true,
    primary = Color(0xFFF0EEE8), secondary = Color(0xFFB8B5AE), tertiary = Color(0xFF8B8984),
    neutral = Color(0xFF111216), neutralAlt = Color(0xFF25262C), surface = Color(0xFF1A1B20), surfaceSoft = Color(0xFF202126),
    border = Color(0xFF3C3C43), accent = Color(0xFFAFC6FF), accentSoft = Color(0xFF263667),
    surfaceRaised = Color(0xFF24252B), surfaceSunken = Color(0xFF0D0E11), routeLine = Color(0xFF4A4A51),
    signalBlue = Color(0xFFAFC6FF), signalMint = Color(0xFF62D4B3), signalAmber = Color(0xFFFFB86A), shadow = Color(0xFF000000),
    success = Color(0xFF62D4B3), successSoft = Color(0xFF17372F),
    warning = Color(0xFFFFB86A), warningSoft = Color(0xFF402B18),
    danger = Color(0xFFFF8A84), dangerSoft = Color(0xFF461F21),
)

internal val LocalTrailPalette = staticCompositionLocalOf { LightTrailPalette }

/** Same-name object + @Composable fun coexist (different namespaces), mirroring MaterialTheme. */
object QuotaTrailTheme {
    val colors: QuotaTrailColorPalette
        @Composable @ReadOnlyComposable get() = LocalTrailPalette.current
}
