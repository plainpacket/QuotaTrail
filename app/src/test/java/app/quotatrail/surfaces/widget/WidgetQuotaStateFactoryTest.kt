package app.quotatrail.surfaces.widget

import app.quotatrail.domain.model.AccountStatus
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.CurrentQuotaFreshness
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetQuotaStateFactoryTest {
    private val factory = WidgetQuotaStateFactory()

    @Test
    fun `builds fields from selected windows in snapshot order`() {
        val state = freshState(
            windows = listOf(
                percentWindow("five_hour", usedPercent = 13),
                percentWindow("weekly", usedPercent = 58),
                percentWindow("monthly", usedPercent = 29),
            ),
        )
        val result = factory.create(state, selectedWindowIds = listOf("weekly", "five_hour"))

        // 顺序按 snapshot 天然顺序（five_hour 在前），不按选择顺序。
        assertEquals(listOf("five_hour", "weekly"), result.fields.map { it.windowId })
        assertEquals(87, result.fields[0].percent) // 100 - 13
        assertFalse(result.fields[0].isBalance)
        assertEquals(Instant.parse("2026-06-02T10:00:00Z"), result.lastUpdatedAt)
    }

    @Test
    fun `usage count window renders as percent not count`() {
        val state = freshState(
            windows = listOf(usageCountWindow("calls", usedPercent = 40, usedCount = 40, limitCount = 100)),
        )
        val result = factory.create(state, selectedWindowIds = listOf("calls"))
        assertEquals(1, result.fields.size)
        assertFalse(result.fields[0].isBalance)
        assertEquals(60, result.fields[0].percent)
    }

    @Test
    fun `balance window renders as amount`() {
        val state = freshState(
            windows = listOf(balanceWindow("balance", amount = "8.50", currency = "USD")),
        )
        val result = factory.create(state, selectedWindowIds = listOf("balance"))
        assertTrue(result.fields[0].isBalance)
        assertEquals("8.50", result.fields[0].balanceAmount)
        assertEquals("USD", result.fields[0].balanceCurrency)
    }

    @Test
    fun `unavailable windows are excluded`() {
        val state = freshState(
            windows = listOf(
                percentWindow("five_hour", usedPercent = 10),
                QuotaWindow(
                    windowId = QuotaWindowId("weekly"), titleKey = "weekly", usedPercent = null,
                    resetAt = null, limitWindowSeconds = null, isPrimaryCandidate = false,
                    availability = QuotaWindowAvailability.Missing,
                ),
            ),
        )
        val result = factory.create(state, selectedWindowIds = listOf("five_hour", "weekly"))
        assertEquals(listOf("five_hour"), result.fields.map { it.windowId })
    }

    @Test
    fun `depleted percent window remains visible at zero percent with reset time`() {
        val depleted = percentWindow("claude_5h_window", usedPercent = 100).copy(
            availability = QuotaWindowAvailability.Depleted,
        )
        val result = factory.create(
            freshState(windows = listOf(depleted)),
            selectedWindowIds = listOf("claude_5h_window"),
        )

        val field = result.fields.single()
        assertEquals(0, field.percent)
        assertEquals(Instant.parse("2026-06-02T14:30:00Z"), field.resetAt)
        assertEquals(WidgetQuotaTone.Danger, field.tone)
    }

    @Test
    fun `unconfigured with accounts yields onboarding state`() {
        val result = factory.unconfigured(hasAccounts = true)
        assertTrue(result.isUnconfigured)
        assertTrue(result.hasAccounts)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun `unconfigured without accounts opens home rather than provider selection`() {
        val result = factory.unconfigured(hasAccounts = false)
        assertTrue(result.isUnconfigured)
        assertFalse(result.hasAccounts)
        assertEquals(WidgetClickTarget.Home, result.clickTarget)
    }

    // ---- helpers ----
    private fun freshState(windows: List<QuotaWindow>): CurrentQuotaState {
        val account = ProviderAccount(
            localAccountId = LocalAccountId("acc-1"),
            providerId = ProviderId("codex"),
            providerAccountId = ProviderAccountId("p-1"),
            displayName = "个人号",
            avatarInitial = "个",
            avatarColorKey = "acc-1",
            status = AccountStatus.Active,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
            lastSuccessfulRefreshAt = Instant.parse("2026-06-02T10:00:00Z"),
        )
        val snapshot = QuotaSnapshot(
            snapshotId = SnapshotId("s-1"),
            providerId = ProviderId("codex"),
            localAccountId = LocalAccountId("acc-1"),
            providerAccountId = ProviderAccountId("p-1"),
            fetchedAt = Instant.parse("2026-06-02T10:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
            planType = null,
            windows = windows,
            credits = null,
            responseDigest = null,
        )
        return CurrentQuotaState(
            status = CurrentQuotaStatus.Fresh,
            freshness = CurrentQuotaFreshness.Fresh,
            account = account,
            snapshot = snapshot,
            latestAttempt = null,
            primaryWindow = windows.firstOrNull(),
            secondaryWindows = windows.drop(1),
            primaryWindowCanAlert = false,
            error = null,
        )
    }

    private fun percentWindow(id: String, usedPercent: Int) = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = id, usedPercent = usedPercent,
        resetAt = Instant.parse("2026-06-02T14:30:00Z"), limitWindowSeconds = null,
        isPrimaryCandidate = true, availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.Percent,
    )

    private fun usageCountWindow(id: String, usedPercent: Int, usedCount: Int, limitCount: Int) = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = id, usedPercent = usedPercent,
        resetAt = Instant.parse("2026-06-02T14:30:00Z"), limitWindowSeconds = null,
        isPrimaryCandidate = true, availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.UsageCount, usedCount = usedCount, limitCount = limitCount,
    )

    private fun balanceWindow(id: String, amount: String, currency: String) = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = id, usedPercent = null,
        resetAt = null, limitWindowSeconds = null, isPrimaryCandidate = false,
        availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.Balance, balanceAmount = amount, balanceCurrency = currency,
    )
}
