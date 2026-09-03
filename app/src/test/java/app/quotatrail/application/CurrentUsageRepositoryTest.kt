package app.quotatrail.application

import androidx.room.Room
import app.quotatrail.storage.local.db.QuotaTrailDatabase
import app.quotatrail.storage.local.entity.ProviderAccountEntity
import app.quotatrail.storage.local.entity.QuotaSnapshotEntity
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.settings.NotificationAccountSelection
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.domain.settings.PrimaryQuotaWindowPreferenceReader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class CurrentUsageRepositoryTest {
    @Test
    fun `no current account loads unauthenticated state`() = runTest {
        withRepository { _, _, repository ->
            val state = repository.loadCurrentState()

            assertEquals(CurrentQuotaStatus.Unauthenticated, state.status)
        }
    }

    @Test
    fun `current account loads latest successful snapshot`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(status = "active"))
            db.quotaSnapshotDao().insert(snapshot(fetchedAt = Instant.parse("2026-05-23T11:50:00Z")))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadCurrentState()

            assertEquals(CurrentQuotaStatus.Fresh, state.status)
            assertEquals("Work", state.account?.displayName)
            assertEquals(62, state.primaryWindow?.usedPercent)
        }
    }

    @Test
    fun `current account uses persisted primary quota window`() = runTest {
        withRepository(primaryWindowId = QuotaWindowId("weekly")) { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(status = "active"))
            db.quotaSnapshotDao().insert(snapshot(fetchedAt = Instant.parse("2026-05-23T11:50:00Z")))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadCurrentState()

            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
            assertEquals(listOf(QuotaWindowId("five_hour")), state.secondaryWindows.map { it.windowId })
        }
    }

    @Test
    fun `home account states load every saved account and preserve persisted selection`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "First"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Second"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-2", localAccountId = "local-2"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-2"))

            val result = repository.loadAccountStates()

            assertEquals(listOf("First", "Second"), result.states.map { it.account?.displayName })
            assertEquals(LocalAccountId("local-2"), result.selectedAccountId)
        }
    }

    @Test
    fun `needs reauth current account loads auth required state`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(status = "needs_reauth"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadCurrentState()

            assertEquals(CurrentQuotaStatus.AuthRequired, state.status)
        }
    }

    @Test
    fun `status notification state uses configured account and window`() = runTest {
        withRepository(
            notificationPreferences = NotificationPreferences(
                persistentNotificationAccount = NotificationAccountSelection(
                    providerId = ProviderId("codex"),
                    localAccountId = LocalAccountId("local-2"),
                ),
                persistentNotificationWindowId = QuotaWindowId("weekly"),
            ),
        ) { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "Current"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Configured"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-2", localAccountId = "local-2"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadStatusNotificationStates(
                refreshedState = repository.loadCurrentState(),
            ).single()

            assertEquals("Configured", state.account?.displayName)
            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
        }
    }

    @Test
    fun `default status notification loads one account for Claude and Codex`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(
                account(
                    providerId = "claude",
                    localAccountId = "claude-local",
                    displayName = "Claude account",
                ),
            )
            db.providerAccountDao().upsert(
                account(
                    providerId = "codex",
                    localAccountId = "codex-local",
                    displayName = "Codex account",
                ),
            )
            db.quotaSnapshotDao().insert(
                snapshot(
                    snapshotId = "claude-snapshot",
                    providerId = "claude",
                    localAccountId = "claude-local",
                ),
            )
            db.quotaSnapshotDao().insert(
                snapshot(
                    snapshotId = "codex-snapshot",
                    providerId = "codex",
                    localAccountId = "codex-local",
                ),
            )
            currentAccountReader.selection = CurrentAccountSelection(
                ProviderId("codex"),
                LocalAccountId("codex-local"),
            )

            val states = repository.loadStatusNotificationStates(
                refreshedState = repository.loadCurrentState(),
            )

            assertEquals(listOf("claude", "codex"), states.map { it.account?.providerId?.value })
            assertEquals(listOf("Claude account", "Codex account"), states.map { it.account?.displayName })
        }
    }

    @Test
    fun `requested account state uses supplied account and window`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "Current"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Requested"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-2", localAccountId = "local-2"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadAccountState(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-2"),
                primaryWindowId = QuotaWindowId("weekly"),
            )

            assertEquals("Requested", state.account?.displayName)
            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
        }
    }

    private suspend fun withRepository(
        primaryWindowId: QuotaWindowId = QuotaWindowId("five_hour"),
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        block: suspend (QuotaTrailDatabase, InMemoryCurrentAccountReader, CurrentUsageRepository) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuotaTrailDatabase::class.java,
        ).build()
        val currentAccountReader = InMemoryCurrentAccountReader()
        val repository = CurrentUsageRepository(
            currentAccountReader = currentAccountReader,
            providerAccountDao = db.providerAccountDao(),
            quotaSnapshotDao = db.quotaSnapshotDao(),
            refreshAttemptDao = db.refreshAttemptDao(),
            primaryQuotaWindowPreferenceReader = StaticPrimaryQuotaWindowPreferenceReader(primaryWindowId),
            notificationPreferenceReader = StaticNotificationPreferenceReader(notificationPreferences),
            clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
        )

        try {
            block(db, currentAccountReader, repository)
        } finally {
            db.close()
        }
    }

    private class InMemoryCurrentAccountReader : CurrentAccountReader {
        var selection: CurrentAccountSelection? = null

        override suspend fun currentAccountSelection(): CurrentAccountSelection? = selection
    }

    private class StaticPrimaryQuotaWindowPreferenceReader(
        private val windowId: QuotaWindowId,
    ) : PrimaryQuotaWindowPreferenceReader {
        override suspend fun primaryQuotaWindowId(): QuotaWindowId = windowId
    }

    private class StaticNotificationPreferenceReader(
        private val notificationPreferences: NotificationPreferences,
    ) : NotificationPreferenceReader {
        override suspend fun notificationPreferences(): NotificationPreferences = notificationPreferences
    }

    private fun account(
        status: String = "active",
        providerId: String = "codex",
        localAccountId: String = "local-1",
        displayName: String = "Work",
    ) = ProviderAccountEntity(
        localAccountId = localAccountId,
        providerId = providerId,
        providerAccountId = "acct-$localAccountId",
        displayName = displayName,
        avatarInitial = displayName.first().uppercase(),
        avatarColorKey = localAccountId,
        status = status,
        createdAt = Instant.parse("2026-05-23T11:00:00Z").toEpochMilli(),
        updatedAt = Instant.parse("2026-05-23T11:30:00Z").toEpochMilli(),
        lastSuccessfulRefreshAt = null,
    )

    private fun snapshot(
        fetchedAt: Instant = Instant.parse("2026-05-23T11:50:00Z"),
        snapshotId: String = "snapshot-1",
        providerId: String = "codex",
        localAccountId: String = "local-1",
    ) = QuotaSnapshotEntity(
        snapshotId = snapshotId,
        providerId = providerId,
        localAccountId = localAccountId,
        providerAccountId = "acct-$localAccountId",
        fetchedAt = fetchedAt.toEpochMilli(),
        source = "manualRefresh",
        planType = "plus",
        windowsJson = """[{"windowId":"five_hour","titleKey":"quota_window_five_hour","usedPercent":62,"resetAt":1779555600000,"limitWindowSeconds":18000,"isPrimaryCandidate":true,"availability":"Available"},{"windowId":"weekly","titleKey":"quota_window_weekly","usedPercent":41,"resetAt":1780012800000,"limitWindowSeconds":604800,"isPrimaryCandidate":true,"availability":"Available"}]""",
        creditsJson = null,
        responseDigest = "safe-digest",
    )
}
