package app.quotatrail.presentation.motion

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailMotionTest {
    @Test
    fun `percent animation runs for first numeric value and changed values when animators are enabled`() {
        assertTrue(TrailMotion.shouldAnimatePercent(oldPercent = 40, newPercent = 62, animatorsEnabled = true))
        assertTrue(TrailMotion.shouldAnimatePercent(oldPercent = null, newPercent = 62, animatorsEnabled = true))
        assertFalse(TrailMotion.shouldAnimatePercent(oldPercent = 62, newPercent = 62, animatorsEnabled = true))
        assertFalse(TrailMotion.shouldAnimatePercent(oldPercent = 40, newPercent = 62, animatorsEnabled = false))
        assertFalse(TrailMotion.shouldAnimatePercent(oldPercent = 62, newPercent = null, animatorsEnabled = true))
    }

    @Test
    fun `progress fraction clamps percent into rail bounds`() {
        assertEquals(0f, TrailMotion.progressFraction(null), 0.0001f)
        assertEquals(0f, TrailMotion.progressFraction(-10), 0.0001f)
        assertEquals(0.62f, TrailMotion.progressFraction(62), 0.0001f)
        assertEquals(1f, TrailMotion.progressFraction(130), 0.0001f)
    }

    @Test
    fun `first paint targets start at the visible motion origin when animators are enabled`() {
        assertEquals(0, TrailMotion.initialPercentTarget(percent = 62, animatorsEnabled = true))
        assertEquals(62, TrailMotion.initialPercentTarget(percent = 62, animatorsEnabled = false))
        assertEquals(0f, TrailMotion.initialProgressTarget(percent = 62, animatorsEnabled = true), 0.0001f)
        assertEquals(0.62f, TrailMotion.initialProgressTarget(percent = 62, animatorsEnabled = false), 0.0001f)
        assertEquals(0f, TrailMotion.initialTrendRevealTarget(animatorsEnabled = true), 0.0001f)
        assertEquals(1f, TrailMotion.initialTrendRevealTarget(animatorsEnabled = false), 0.0001f)
    }

    @Test
    fun `bottom tab scale stays subtle`() {
        assertEquals(1f, TrailMotion.bottomTabScale(selected = false), 0.0001f)
        assertEquals(1.06f, TrailMotion.bottomTabScale(selected = true), 0.0001f)
        assertTrue(TrailMotion.bottomTabScale(selected = true) <= 1.06f)
    }

    @Test
    fun `bottom tab indicator target uses selected index fraction`() {
        assertEquals(0f, TrailMotion.bottomTabIndicatorOffsetFraction(selectedIndex = 0, tabCount = 3), 0.0001f)
        assertEquals(1f / 3f, TrailMotion.bottomTabIndicatorOffsetFraction(selectedIndex = 1, tabCount = 3), 0.0001f)
        assertEquals(2f / 3f, TrailMotion.bottomTabIndicatorOffsetFraction(selectedIndex = 2, tabCount = 3), 0.0001f)
        assertEquals(0f, TrailMotion.bottomTabIndicatorOffsetFraction(selectedIndex = -1, tabCount = 3), 0.0001f)
    }

    @Test
    fun `bottom tab sliding indicator geometry matches spaced tab slots`() {
        val middle = TrailMotion.bottomTabIndicatorGeometry(
            selectedIndex = 1,
            tabCount = 3,
            containerWidth = 300f,
            itemSpacing = 4f,
        )
        val last = TrailMotion.bottomTabIndicatorGeometry(
            selectedIndex = 2,
            tabCount = 3,
            containerWidth = 300f,
            itemSpacing = 4f,
        )

        assertEquals(97.3333f, middle.width, 0.0001f)
        assertEquals(101.3333f, middle.offset, 0.0001f)
        assertEquals(97.3333f, last.width, 0.0001f)
        assertEquals(202.6666f, last.offset, 0.0001f)
        assertEquals(0f, TrailMotion.bottomTabIndicatorGeometry(-1, 3, 300f, 4f).offset, 0.0001f)
        assertEquals(0f, TrailMotion.bottomTabIndicatorGeometry(0, 0, 300f, 4f).width, 0.0001f)
    }

    @Test
    fun `bottom tab sliding indicator style stays neutral and borderless`() {
        val style = TrailMotion.bottomTabIndicatorStyle()

        assertEquals(Color(0xFFF5F7FB), style.color)
        assertEquals(0.90f, style.alpha, 0.0001f)
        assertEquals(0f, style.borderWidthDp, 0.0001f)
    }

    @Test
    fun `bottom tab press indication is disabled while sliding indicator owns selection feedback`() {
        assertFalse(TrailMotion.bottomTabPressIndicationEnabled())
    }

    @Test
    fun `manual refresh sweep only runs for new success events when animators are enabled`() {
        assertTrue(TrailMotion.shouldRunRefreshSweep(previousSuccessCount = 0, nextSuccessCount = 1, animatorsEnabled = true))
        assertFalse(TrailMotion.shouldRunRefreshSweep(previousSuccessCount = 1, nextSuccessCount = 1, animatorsEnabled = true))
        assertFalse(TrailMotion.shouldRunRefreshSweep(previousSuccessCount = 0, nextSuccessCount = 1, animatorsEnabled = false))
        assertEquals(-0.35f, TrailMotion.RefreshSweepStartProgress, 0.0001f)
        assertEquals(1.35f, TrailMotion.RefreshSweepEndProgress, 0.0001f)
        assertTrue(TrailMotion.refreshSweepShouldDraw(progress = 0.8f))
        assertFalse(TrailMotion.refreshSweepShouldDraw(progress = 1.35f))
    }

    @Test
    fun `trend bar width stays compact and bounded`() {
        assertEquals(7.4074f, TrailMotion.trendBarWidth(availableWidth = 320f, barCount = 24, minWidth = 4f, maxWidth = 10f), 0.0001f)
        assertEquals(4f, TrailMotion.trendBarWidth(availableWidth = 96f, barCount = 24, minWidth = 4f, maxWidth = 10f), 0.0001f)
        assertEquals(0f, TrailMotion.trendBarWidth(availableWidth = 0f, barCount = 24, minWidth = 4f, maxWidth = 10f), 0.0001f)
    }

    @Test
    fun `trend bar center reserves side insets so edge bars are fully visible`() {
        val barWidth = 8f

        assertEquals(4f, TrailMotion.trendBarCenterX(normalizedX = 0f, width = 100f, barWidth = barWidth), 0.0001f)
        assertEquals(50f, TrailMotion.trendBarCenterX(normalizedX = 0.5f, width = 100f, barWidth = barWidth), 0.0001f)
        assertEquals(96f, TrailMotion.trendBarCenterX(normalizedX = 1f, width = 100f, barWidth = barWidth), 0.0001f)
    }

    @Test
    fun `trend bar top radius forms a rounded cap without exceeding bar height`() {
        assertEquals(4f, TrailMotion.trendBarTopCornerRadius(barWidth = 8f, barHeight = 40f), 0.0001f)
        assertEquals(3f, TrailMotion.trendBarTopCornerRadius(barWidth = 8f, barHeight = 6f), 0.0001f)
        assertEquals(0f, TrailMotion.trendBarTopCornerRadius(barWidth = 8f, barHeight = 0f), 0.0001f)
    }

    @Test
    fun `quota number and rail durations are synchronized and slower`() {
        assertEquals(TrailMotion.PercentDurationMillis, TrailMotion.ProgressRailDurationMillis)
        assertTrue(TrailMotion.PercentDurationMillis >= 560)
    }

    @Test
    fun `status dot breathing and page cascade respect disabled system animations`() {
        assertTrue(TrailMotion.statusDotBreathEnabled(isSemanticTone = true, animatorsEnabled = true))
        assertFalse(TrailMotion.statusDotBreathEnabled(isSemanticTone = false, animatorsEnabled = true))
        assertFalse(TrailMotion.statusDotBreathEnabled(isSemanticTone = true, animatorsEnabled = false))
        assertEquals(0f, TrailMotion.pageCascadeInitialOffsetDp(animatorsEnabled = false), 0.0001f)
        assertTrue(TrailMotion.pageCascadeInitialOffsetDp(animatorsEnabled = true) > 0f)
    }

    @Test
    fun `hourly usage bars sum samples in the same hour bucket`() {
        val bars = TrailMotion.trendHourlyUsageBars(
            listOf(
                TrendUsagePoint(x = 0.5f / 24f, value = 3f),
                TrendUsagePoint(x = 0.5f / 24f, value = 2f),
                TrendUsagePoint(x = 1.5f / 24f, value = 4f),
            ),
        )
        assertEquals(24, bars.size)
        assertEquals(5f, bars[0].value, 0.0001f)
        assertEquals(4f, bars[1].value, 0.0001f)
        assertEquals(0f, bars[2].value, 0.0001f)
    }

    @Test
    fun `hourly usage bars always produce 24 bars and gaps are zero`() {
        // Only buckets 0 (x≈0.5/24) and 23 (x≈23.5/24) receive data; all others must be zero.
        val bars = TrailMotion.trendHourlyUsageBars(
            listOf(
                TrendUsagePoint(x = 0.5f / 24f, value = 7f),
                TrendUsagePoint(x = 23.5f / 24f, value = 11f),
            ),
        )
        assertEquals(24, bars.size)
        assertEquals(7f, bars[0].value, 0.0001f)
        assertEquals(11f, bars[23].value, 0.0001f)
        // An interior bucket with no data must stay at zero.
        assertEquals(0f, bars[10].value, 0.0001f)
    }

    @Test
    fun `usage bar y auto-scales against the busiest bucket`() {
        // value == maxValue reaches the top inset; half-max sits midway; zero rests on the baseline.
        assertEquals(6f, TrailMotion.trendUsageBarY(value = 10f, maxValue = 10f, height = 86f, topInset = 6f, bottomInset = 5f), 0.0001f)
        assertEquals(43.5f, TrailMotion.trendUsageBarY(value = 5f, maxValue = 10f, height = 86f, topInset = 6f, bottomInset = 5f), 0.0001f)
        assertEquals(81f, TrailMotion.trendUsageBarY(value = 0f, maxValue = 10f, height = 86f, topInset = 6f, bottomInset = 5f), 0.0001f)
    }

    @Test
    fun `usage bar y rests on baseline when there is no usage`() {
        assertEquals(81f, TrailMotion.trendUsageBarY(value = 0f, maxValue = 0f, height = 86f, topInset = 6f, bottomInset = 5f), 0.0001f)
    }

    @Test
    fun `remaining percent uses fixed zero to one hundred y axis`() {
        assertEquals(6f, TrailMotion.remainingPercentY(100f, 86f, 6f, 6f), 0.0001f)
        assertEquals(43f, TrailMotion.remainingPercentY(50f, 86f, 6f, 6f), 0.0001f)
        assertEquals(80f, TrailMotion.remainingPercentY(0f, 86f, 6f, 6f), 0.0001f)
    }

    @Test
    fun `remaining history line breaks across missing hourly samples`() {
        val segments = TrailMotion.remainingHistorySegments(
            points = listOf(
                TrendUsagePoint(x = 0.01f, value = 80f),
                TrendUsagePoint(x = 0.02f, value = 79f),
                TrendUsagePoint(x = 0.40f, value = 60f),
            ),
            maximumGapFraction = 0.03f,
        )

        assertEquals(listOf(2, 1), segments.map { it.size })
    }
}
