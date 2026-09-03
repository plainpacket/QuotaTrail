package app.quotatrail.application

import androidx.room.Room
import app.quotatrail.storage.local.db.QuotaTrailDatabase
import app.quotatrail.storage.local.entity.ProviderAccountEntity
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.storage.repository.RoomQuotaSnapshotStore
import app.quotatrail.storage.repository.RoomRefreshAttemptStore
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.RefreshAttemptId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.sync.AttemptIdProvider
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.UsageSyncCoordinator
import app.quotatrail.sync.RefreshProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class HomeUsageSyncCoordinatorUseCaseTest {
    @Test
    fun `manual refresh routes through coordinator with manual trigger`() = runTest {
        withUseCase { db, currentAccountReader, provider, useCase ->
            db.providerAccountDao().upsert(account())
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            useCase.refreshCurrentState()

            assertEquals(listOf(RefreshTrigger.Manual), provider.triggers)
        }
    }

    @Test
    fun `manual refresh propagates coordinator infrastructure exceptions`() = runTest {
        withUseCase { db, currentAccountReader, provider, useCase ->
            db.providerAccountDao().upsert(account())
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))
            provider.exceptionToThrow = IllegalStateException("store failed")

            try {
                useCase.refreshCurrentState()
                fail("Expected infrastructure exception to propagate")
            } catch (exception: IllegalStateException) {
                assertEquals("store failed", exception.message)
            }
        }
    }

    private suspend fun withUseCase(
        block: suspend (
            QuotaTrailDatabase,
            InMemoryCurrentAccountReader,
            RecordingRefreshProvider,
            HomeUsageSyncCoordinatorUseCase,
        ) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuotaTrailDatabase::class.java,
        ).build()
        val currentAccountReader = InMemoryCurrentAccountReader()
        val provider = RecordingRefreshProvider()
        val clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC)
        val refreshCoordinator = UsageSyncCoordinator(
            provider = provider,
            snapshotStore = RoomQuotaSnapshotStore(db.quotaSnapshotDao()),
            attemptStore = RoomRefreshAttemptStore(db.refreshAttemptDao()),
            attemptIdProvider = AttemptIdProvider { RefreshAttemptId("attempt-1") },
            clock = clock,
        )
        val currentQuotaStateRepository = CurrentUsageRepository(
            currentAccountReader = currentAccountReader,
            providerAccountDao = db.providerAccountDao(),
            quotaSnapshotDao = db.quotaSnapshotDao(),
            refreshAttemptDao = db.refreshAttemptDao(),
            clock = clock,
        )
        val useCase = HomeUsageSyncCoordinatorUseCase(
            currentAccountStore = CurrentQuotaRefreshAccountStore(
                currentAccountReader = currentAccountReader,
                providerAccountDao = db.providerAccountDao(),
            ),
            refreshCoordinator = refreshCoordinator,
            currentQuotaStateLoader = currentQuotaStateRepository,
        )

        try {
            block(db, currentAccountReader, provider, useCase)
        } finally {
            db.close()
        }
    }

    private class InMemoryCurrentAccountReader : CurrentAccountReader {
        var selection: CurrentAccountSelection? = null

        override suspend fun currentAccountSelection(): CurrentAccountSelection? = selection
    }

    private inner class RecordingRefreshProvider : RefreshProvider {
        val triggers = mutableListOf<RefreshTrigger>()
        var exceptionToThrow: RuntimeException? = null

        override suspend fun refresh(account: ProviderAccount, trigger: RefreshTrigger): ProviderRefreshResult {
            triggers += trigger
            exceptionToThrow?.let { throw it }
            return ProviderRefreshResult.Success(snapshot())
        }
    }

    private fun account() = ProviderAccountEntity(
        localAccountId = "local-1",
        providerId = "codex",
        providerAccountId = "acct-1",
        displayName = "Work",
        avatarInitial = "W",
        avatarColorKey = "local-1",
        status = "active",
        createdAt = Instant.parse("2026-05-23T11:00:00Z").toEpochMilli(),
        updatedAt = Instant.parse("2026-05-23T11:30:00Z").toEpochMilli(),
        lastSuccessfulRefreshAt = null,
    )

    private fun snapshot() = QuotaSnapshot(
        snapshotId = SnapshotId("snapshot-1"),
        providerId = ProviderId("codex"),
        localAccountId = LocalAccountId("local-1"),
        providerAccountId = ProviderAccountId("acct-1"),
        fetchedAt = Instant.parse("2026-05-23T12:00:00Z"),
        source = QuotaSnapshotSource.AppOpenRefresh,
        planType = "plus",
        windows = listOf(
            QuotaWindow(
                windowId = QuotaWindowId("five_hour"),
                titleKey = "quota_window_five_hour",
                usedPercent = 62,
                resetAt = Instant.parse("2026-05-23T17:00:00Z"),
                limitWindowSeconds = 18_000,
                isPrimaryCandidate = true,
                availability = QuotaWindowAvailability.Available,
            ),
        ),
        credits = null,
        responseDigest = "safe-digest",
    )
}
