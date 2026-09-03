package app.quotatrail.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quotatrail.R
import app.quotatrail.domain.theme.ThemeMode
import app.quotatrail.domain.theme.resolveDarkAppearance

enum class QuotaTrailFontScheme {
    SystemDefault,
    GeistHybrid,
    InterJetBrainsMono,
    MonoFocusGeistMono,
}

data class QuotaTrailTextStyles(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val number: TextStyle,
) {
    val material: Typography = Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = display,
        headlineSmall = display,
        titleLarge = title,
        titleMedium = title.copy(fontSize = 17.sp, lineHeight = 20.sp),
        titleSmall = title,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = body,
        labelLarge = label,
        labelMedium = label,
        labelSmall = label,
    )
}

object QuotaTrailFontFamilies {
    private val weights = arrayOf(
        FontWeight.W400,
        FontWeight.W500,
        FontWeight.W600,
        FontWeight.W700,
        FontWeight.W800,
        FontWeight(850),
    )

    val system = FontFamily.Default
    val geistSans = bundledFontFamily(R.font.geist_variable)
    val geistMono = bundledFontFamily(R.font.geist_mono_variable)
    val inter = bundledFontFamily(R.font.inter_variable)
    val jetBrainsMono = bundledFontFamily(R.font.jetbrains_mono_variable)

    private fun bundledFontFamily(resourceId: Int): FontFamily =
        FontFamily(*weights.map { weight -> Font(resourceId, weight = weight) }.toTypedArray())
}

private val SystemQuotaTrailTextStyles = buildQuotaTrailTextStyles(
    uiFamily = QuotaTrailFontFamilies.system,
    numberFamily = QuotaTrailFontFamilies.system,
)

private val LocalQuotaTrailTextStyles = staticCompositionLocalOf { SystemQuotaTrailTextStyles }

object QuotaTrailTypography {
    val display: TextStyle = SystemQuotaTrailTextStyles.display
    val title: TextStyle = SystemQuotaTrailTextStyles.title
    val body: TextStyle = SystemQuotaTrailTextStyles.body
    val label: TextStyle = SystemQuotaTrailTextStyles.label
    val number: TextStyle = SystemQuotaTrailTextStyles.number
    val material: Typography = SystemQuotaTrailTextStyles.material

    val current: QuotaTrailTextStyles
        @Composable get() = LocalQuotaTrailTextStyles.current

    fun forScheme(scheme: QuotaTrailFontScheme): QuotaTrailTextStyles =
        when (scheme) {
            QuotaTrailFontScheme.SystemDefault -> SystemQuotaTrailTextStyles
            QuotaTrailFontScheme.GeistHybrid -> buildQuotaTrailTextStyles(
                uiFamily = QuotaTrailFontFamilies.geistSans,
                numberFamily = QuotaTrailFontFamilies.geistMono,
            )
            QuotaTrailFontScheme.InterJetBrainsMono -> buildQuotaTrailTextStyles(
                uiFamily = QuotaTrailFontFamilies.inter,
                numberFamily = QuotaTrailFontFamilies.jetBrainsMono,
            )
            QuotaTrailFontScheme.MonoFocusGeistMono -> buildQuotaTrailTextStyles(
                uiFamily = QuotaTrailFontFamilies.geistMono,
                numberFamily = QuotaTrailFontFamilies.geistMono,
            )
        }
}

private fun buildQuotaTrailTextStyles(
    uiFamily: FontFamily,
    numberFamily: FontFamily,
): QuotaTrailTextStyles =
    QuotaTrailTextStyles(
        display = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight(700),
            fontSize = 32.sp,
            lineHeight = 37.sp,
            letterSpacing = 0.sp,
        ),
        title = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight(760),
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        body = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight(500),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
        ),
        label = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight(720),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        ),
        number = TextStyle(
            fontFamily = numberFamily,
            fontWeight = FontWeight(750),
            fontSize = 32.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
        ),
    )

object QuotaTrailShapes {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(18.dp)
    val xl = RoundedCornerShape(28.dp)
    val screen = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(999.dp)
    val instrument = RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomEnd = 8.dp, bottomStart = 28.dp)
    val dock = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 8.dp, bottomStart = 8.dp)

    val material = Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
    )
}

private fun lightScheme(p: QuotaTrailColorPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.surface,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    inversePrimary = p.accent,
    secondary = p.secondary,
    onSecondary = p.surface,
    secondaryContainer = p.neutralAlt,
    onSecondaryContainer = p.secondary,
    tertiary = p.tertiary,
    onTertiary = p.surface,
    tertiaryContainer = p.neutralAlt,
    onTertiaryContainer = p.tertiary,
    background = p.neutral,
    onBackground = p.primary,
    surface = p.surface,
    onSurface = p.primary,
    surfaceVariant = p.surfaceSoft,
    onSurfaceVariant = p.secondary,
    surfaceTint = p.accent,
    inverseSurface = p.primary,
    inverseOnSurface = p.surface,
    outline = p.border,
    outlineVariant = p.border,
    scrim = p.primary,
    surfaceBright = p.surface,
    surfaceDim = p.neutralAlt,
    surfaceContainer = p.neutral,
    surfaceContainerHigh = p.neutralAlt,
    surfaceContainerHighest = p.neutralAlt,
    surfaceContainerLow = p.surfaceSoft,
    surfaceContainerLowest = p.surface,
    error = p.danger,
    onError = p.surface,
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
    primaryFixed = p.accentSoft,
    primaryFixedDim = p.accentSoft,
    onPrimaryFixed = p.accent,
    onPrimaryFixedVariant = p.accent,
    secondaryFixed = p.neutralAlt,
    secondaryFixedDim = p.neutralAlt,
    onSecondaryFixed = p.primary,
    onSecondaryFixedVariant = p.secondary,
    tertiaryFixed = p.neutralAlt,
    tertiaryFixedDim = p.neutralAlt,
    onTertiaryFixed = p.primary,
    onTertiaryFixedVariant = p.tertiary,
)

private fun darkScheme(p: QuotaTrailColorPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF10131E),
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    secondary = p.secondary,
    onSecondary = p.primary,
    secondaryContainer = p.neutralAlt,
    onSecondaryContainer = p.primary,
    tertiary = p.tertiary,
    onTertiary = p.primary,
    background = p.neutral,
    onBackground = p.primary,
    surface = p.surface,
    onSurface = p.primary,
    surfaceVariant = p.surfaceSoft,
    onSurfaceVariant = p.secondary,
    surfaceTint = p.accent,
    inverseSurface = p.primary,
    inverseOnSurface = p.neutral,
    outline = p.border,
    outlineVariant = p.border,
    scrim = Color(0xFF000000),
    error = p.danger,
    onError = Color(0xFF2B0F0F),
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
)

internal fun materialScheme(palette: QuotaTrailColorPalette, dark: Boolean) =
    if (dark) darkScheme(palette) else lightScheme(palette)

@Composable
fun QuotaTrailTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScheme: QuotaTrailFontScheme = QuotaTrailFontScheme.SystemDefault,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkAppearance(themeMode, isSystemInDarkTheme())
    val palette = if (dark) DarkTrailPalette else LightTrailPalette
    val typography = QuotaTrailTypography.forScheme(fontScheme)
    CompositionLocalProvider(LocalTrailPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette, dark),
            typography = typography.material,
            shapes = QuotaTrailShapes.material,
        ) {
            CompositionLocalProvider(
                LocalQuotaTrailTextStyles provides typography,
                content = content,
            )
        }
    }
}
