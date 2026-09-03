package app.quotatrail.sync

import app.quotatrail.domain.model.*
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CompositeRefreshProviderTest {
    private val testSnapshot = QuotaSnapshot(
        snapshotId = SnapshotId("s1"), providerId = ProviderId("codex"),
        localAccountId = LocalAccountId("a1"), providerAccountId = null,
        fetchedAt = Instant.EPOCH, source = QuotaSnapshotSource.ManualRefresh,
        planType = null, windows = emptyList(), credits = null, responseDigest = null)

    @Test
    fun dispatchesToCorrectProvider() = runTest {
        val codexProvider = RefreshProvider { _, _ -> ProviderRefreshResult.Success(testSnapshot) }
        val composite = CompositeRefreshProvider(mapOf(ProviderId("codex") to codexProvider))
        val account = ProviderAccount.createNew(LocalAccountId("a1"), ProviderId("codex"), null, "Test", Instant.EPOCH)
        val result = composite.refresh(account, RefreshTrigger.Manual)
        assertTrue(result is ProviderRefreshResult.Success)
    }

    @Test
    fun missingProvider_returnsFailure() = runTest {
        val composite = CompositeRefreshProvider(emptyMap())
        val account = ProviderAccount.createNew(LocalAccountId("a1"), ProviderId("deepseek"), null, "Test", Instant.EPOCH)
        val result = composite.refresh(account, RefreshTrigger.Manual)
        assertTrue(result is ProviderRefreshResult.Failure)
        val failure = result as ProviderRefreshResult.Failure
        assertTrue(failure.error.diagnosticsDigest?.contains("deepseek") == true)
    }
}
