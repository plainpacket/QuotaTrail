package app.quotatrail.application

import app.quotatrail.domain.model.LocalAccountId
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
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPrimaryWindowResolutionTest {

    // --- helpers ---

    private fun makeWindow(
        id: String,
        isPrimaryCandidate: Boolean = false,
    ) = QuotaWindow(
        windowId = QuotaWindowId(id),
        titleKey = "key_$id",
        usedPercent = 50,
        resetAt = null,
        limitWindowSeconds = null,
        isPrimaryCandidate = isPrimaryCandidate,
        availability = QuotaWindowAvailability.Available,
    )

    private fun makeSnapshot(windows: List<QuotaWindow>) = QuotaSnapshot(
        snapshotId = SnapshotId("snap-1"),
        providerId = ProviderId("codex"),
        localAccountId = LocalAccountId("local-1"),
        providerAccountId = null,
        fetchedAt = Instant.parse("2026-05-23T11:50:00Z"),
        source = QuotaSnapshotSource.BackgroundRefresh,
        planType = null,
        windows = windows,
        credits = null,
        responseDigest = null,
    )

    private fun makeState(
        primaryWindow: QuotaWindow?,
        snapshot: QuotaSnapshot?,
    ) = CurrentQuotaState(
        status = CurrentQuotaStatus.Fresh,
        freshness = CurrentQuotaFreshness.Fresh,
        account = null,
        snapshot = snapshot,
        latestAttempt = null,
        primaryWindow = primaryWindow,
        secondaryWindows = emptyList(),
        primaryWindowCanAlert = false,
        error = null,
    )

    // --- tests ---

    @Test
    fun `returns primaryWindow when already set`() {
        val window = makeWindow("five_hour", isPrimaryCandidate = true)
        val state = makeState(primaryWindow = window, snapshot = makeSnapshot(listOf(window)))

        val resolved = resolveNotificationPrimaryWindow(state)

        assertEquals(window, resolved)
    }

    @Test
    fun `falls back to isPrimaryCandidate window when primaryWindow is null`() {
        val candidate = makeWindow("weekly", isPrimaryCandidate = true)
        val other = makeWindow("monthly", isPrimaryCandidate = false)
        val state = makeState(primaryWindow = null, snapshot = makeSnapshot(listOf(other, candidate)))

        val resolved = resolveNotificationPrimaryWindow(state)

        assertEquals(candidate, resolved)
    }

    @Test
    fun `falls back to first window when no isPrimaryCandidate and primaryWindow is null`() {
        val first = makeWindow("window-a", isPrimaryCandidate = false)
        val second = makeWindow("window-b", isPrimaryCandidate = false)
        val state = makeState(primaryWindow = null, snapshot = makeSnapshot(listOf(first, second)))

        val resolved = resolveNotificationPrimaryWindow(state)

        assertEquals(first, resolved)
    }

    @Test
    fun `returns null when primaryWindow is null and snapshot is null`() {
        val state = makeState(primaryWindow = null, snapshot = null)

        val resolved = resolveNotificationPrimaryWindow(state)

        assertNull(resolved)
    }

    @Test
    fun `returns null when primaryWindow is null and snapshot has empty windows`() {
        val state = makeState(primaryWindow = null, snapshot = makeSnapshot(emptyList()))

        val resolved = resolveNotificationPrimaryWindow(state)

        assertNull(resolved)
    }
}
