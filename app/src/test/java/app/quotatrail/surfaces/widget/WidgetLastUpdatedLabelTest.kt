package app.quotatrail.surfaces.widget

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLastUpdatedLabelTest {
    @Test
    fun `full label includes update date and time while compact label keeps time`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val context = RuntimeEnvironment.getApplication()
            val state = state(Instant.parse("2026-06-02T10:15:00Z"))

            val full = state.lastUpdatedLabel(context)
            val compact = state.lastUpdatedLabel(context, compact = true)

            assertTrue(full.startsWith("Updated "))
            assertTrue(full.contains("6/2"))
            assertTrue(full.contains("10:15"))
            assertTrue(compact.startsWith("Updated "))
            assertTrue(compact.contains("10:15"))
            assertFalse(compact.contains("6/2"))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `missing snapshot uses explicit unavailable update label`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("Updated --", state(null).lastUpdatedLabel(context))
    }

    private fun state(lastUpdatedAt: Instant?): WidgetQuotaState = WidgetQuotaState(
        status = WidgetQuotaStatus.Fresh,
        providerName = "Codex",
        providerId = "codex",
        localAccountId = "local-1",
        accountName = "Codex Main",
        tone = WidgetQuotaTone.Success,
        clickTarget = WidgetClickTarget.Home,
        lastUpdatedAt = lastUpdatedAt,
    )
}
