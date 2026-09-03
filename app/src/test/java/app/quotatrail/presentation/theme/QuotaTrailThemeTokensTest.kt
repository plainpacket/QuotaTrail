package app.quotatrail.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QuotaTrailThemeTokensTest {
    @Test
    fun `field instrument color tokens match design values`() {
        assertEquals(Color(0xFF1A1C1E), LightTrailPalette.primary)
        assertEquals(Color(0xFF5F6065), LightTrailPalette.secondary)
        assertEquals(Color(0xFF7A797F), LightTrailPalette.tertiary)
        assertEquals(Color(0xFFF4F1EA), LightTrailPalette.neutral)
        assertEquals(Color(0xFFE8E3D9), LightTrailPalette.neutralAlt)
        assertEquals(Color(0xFFFFFCF6), LightTrailPalette.surface)
        assertEquals(Color(0xFFF7F3EB), LightTrailPalette.surfaceSoft)
        assertEquals(Color(0xFFD1CBC0), LightTrailPalette.border)
        assertEquals(Color(0xFF3154D5), LightTrailPalette.accent)
        assertEquals(Color(0xFFE3E7FF), LightTrailPalette.accentSoft)
        assertEquals(Color(0xFFE7852F), LightTrailPalette.signalAmber)
        assertEquals(Color(0xFF1F7A63), LightTrailPalette.success)
        assertEquals(Color(0xFFDDF2E9), LightTrailPalette.successSoft)
        assertEquals(Color(0xFFB96517), LightTrailPalette.warning)
        assertEquals(Color(0xFFFFEBCF), LightTrailPalette.warningSoft)
        assertEquals(Color(0xFFB54444), LightTrailPalette.danger)
        assertEquals(Color(0xFFFFE1DF), LightTrailPalette.dangerSoft)
    }

    @Test
    fun `field instrument spacing tokens match design values`() {
        assertEquals(4.dp, TrailSpacing.xs)
        assertEquals(8.dp, TrailSpacing.sm)
        assertEquals(12.dp, TrailSpacing.md)
        assertEquals(16.dp, TrailSpacing.lg)
        assertEquals(20.dp, TrailSpacing.xl)
        assertEquals(24.dp, TrailSpacing.xxl)
    }

    @Test
    fun `field instrument shape tokens match design values`() {
        assertEquals(RoundedCornerShape(4.dp), QuotaTrailShapes.xs)
        assertEquals(RoundedCornerShape(8.dp), QuotaTrailShapes.sm)
        assertEquals(RoundedCornerShape(12.dp), QuotaTrailShapes.md)
        assertEquals(RoundedCornerShape(18.dp), QuotaTrailShapes.lg)
        assertEquals(RoundedCornerShape(28.dp), QuotaTrailShapes.xl)
        assertEquals(RoundedCornerShape(32.dp), QuotaTrailShapes.screen)
        assertEquals(RoundedCornerShape(999.dp), QuotaTrailShapes.pill)
    }

    @Test
    fun `field instrument typography tokens expose design text styles`() {
        assertEquals(32.sp, QuotaTrailTypography.display.fontSize)
        assertEquals(FontWeight(700), QuotaTrailTypography.display.fontWeight)
        assertEquals(20.sp, QuotaTrailTypography.title.fontSize)
        assertEquals(FontWeight(760), QuotaTrailTypography.title.fontWeight)
        assertEquals(14.sp, QuotaTrailTypography.body.fontSize)
        assertEquals(FontWeight(500), QuotaTrailTypography.body.fontWeight)
        assertEquals(12.sp, QuotaTrailTypography.label.fontSize)
        assertEquals(FontWeight(720), QuotaTrailTypography.label.fontWeight)
        assertEquals(32.sp, QuotaTrailTypography.number.fontSize)
        assertEquals(FontWeight(750), QuotaTrailTypography.number.fontWeight)
    }

    @Test
    fun `field instrument typography keeps implementation-safe letter spacing`() {
        // Token names, sizes, and weights follow DESIGN.md; app-level UI guardrails keep letter spacing at 0.sp.
        assertEquals(0.sp, QuotaTrailTypography.display.letterSpacing)
        assertEquals(0.sp, QuotaTrailTypography.title.letterSpacing)
        assertEquals(0.sp, QuotaTrailTypography.body.letterSpacing)
        assertEquals(0.sp, QuotaTrailTypography.label.letterSpacing)
        assertEquals(0.sp, QuotaTrailTypography.number.letterSpacing)
    }

    @Test
    fun `material typography roles map to quotatrail tokens`() {
        val material = QuotaTrailTypography.material

        assertEquals(QuotaTrailTypography.display, material.headlineMedium)
        assertEquals(QuotaTrailTypography.body, material.bodySmall)
        assertEquals(QuotaTrailTypography.label, material.labelSmall)
    }

    @Test
    fun `font schemes map display body label and number families`() {
        val system = QuotaTrailTypography.forScheme(QuotaTrailFontScheme.SystemDefault)
        val geist = QuotaTrailTypography.forScheme(QuotaTrailFontScheme.GeistHybrid)
        val inter = QuotaTrailTypography.forScheme(QuotaTrailFontScheme.InterJetBrainsMono)
        val monoFocus = QuotaTrailTypography.forScheme(QuotaTrailFontScheme.MonoFocusGeistMono)

        assertEquals(FontFamily.Default, system.display.fontFamily)
        assertNotEquals(FontFamily.Default, geist.display.fontFamily)
        assertNotEquals(geist.display.fontFamily, geist.number.fontFamily)
        assertNotEquals(inter.display.fontFamily, inter.number.fontFamily)
        assertEquals(monoFocus.display.fontFamily, monoFocus.body.fontFamily)
        assertEquals(monoFocus.display.fontFamily, monoFocus.number.fontFamily)
        assertEquals("tnum", geist.number.fontFeatureSettings)
    }

    @Test
    fun `material color roles map to quotatrail tokens`() {
        val material = materialScheme(LightTrailPalette, dark = false)

        assertEquals(LightTrailPalette.accentSoft, material.primaryContainer)
        assertEquals(LightTrailPalette.border, material.outlineVariant)
        assertEquals(LightTrailPalette.accent, material.surfaceTint)
        assertEquals(LightTrailPalette.surfaceSoft, material.surfaceContainerLow)
        assertEquals(LightTrailPalette.neutral, material.surfaceContainer)
        assertEquals(LightTrailPalette.neutralAlt, material.surfaceContainerHighest)
    }
}
