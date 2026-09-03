package app.quotatrail.storage.repository

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.QuotaModelBucket
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaSnapshotWindowRoundTripTest {
    private fun snapshot(windows: List<QuotaWindow>) = QuotaSnapshot(
        snapshotId = SnapshotId("snap"),
        providerId = ProviderId("p"),
        localAccountId = LocalAccountId("a"),
        providerAccountId = ProviderAccountId("pa"),
        fetchedAt = Instant.parse("2026-05-31T00:00:00Z"),
        source = QuotaSnapshotSource.ManualRefresh,
        planType = "pro",
        windows = windows,
        credits = null,
        responseDigest = null,
    )

    @Test
    fun balanceWindow_survivesEntityRoundTrip() {
        val window = QuotaWindow(
            windowId = QuotaWindowId("balance"),
            titleKey = "deepseek_balance",
            usedPercent = null,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.Balance,
            balanceAmount = "9.49",
            balanceCurrency = "CNY",
            subLabel = "Granted 0 · Topped up 9.49",
        )

        val restored = snapshot(listOf(window)).toEntity().toDomain().windows.single()

        assertEquals(QuotaWindowDisplayKind.Balance, restored.displayKind)
        assertEquals("9.49", restored.balanceAmount)
        assertEquals("CNY", restored.balanceCurrency)
        assertEquals("Granted 0 · Topped up 9.49", restored.subLabel)
    }

    @Test
    fun usageCountWindow_survivesEntityRoundTrip() {
        val window = QuotaWindow(
            windowId = QuotaWindowId("cursor_plan"),
            titleKey = "cursor_plan_window",
            usedPercent = 64,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.UsageCount,
            usedCount = 320,
            limitCount = 500,
        )

        val restored = snapshot(listOf(window)).toEntity().toDomain().windows.single()

        assertEquals(QuotaWindowDisplayKind.UsageCount, restored.displayKind)
        assertEquals(320, restored.usedCount)
        assertEquals(500, restored.limitCount)
    }

    @Test
    fun multiModelBuckets_surviveEntityRoundTrip() {
        val window = QuotaWindow(
            windowId = QuotaWindowId("antigravity_claude_window"),
            titleKey = "antigravity_claude_window",
            usedPercent = 15,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.MultiModelFraction,
            modelBuckets = listOf(
                QuotaModelBucket("claude-sonnet-4-6", "Claude Sonnet 4.6", 0.92, Instant.parse("2026-06-01T00:00:00Z")),
                QuotaModelBucket("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", 0.6, null),
            ),
        )

        val restored = snapshot(listOf(window)).toEntity().toDomain().windows.single()

        assertEquals(QuotaWindowDisplayKind.MultiModelFraction, restored.displayKind)
        assertEquals(2, restored.modelBuckets.size)
        assertEquals("Claude Sonnet 4.6", restored.modelBuckets[0].displayName)
        assertEquals(0.92, restored.modelBuckets[0].remainingFraction, 0.0001)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), restored.modelBuckets[0].resetAt)
        assertEquals(null, restored.modelBuckets[1].resetAt)
    }
}
