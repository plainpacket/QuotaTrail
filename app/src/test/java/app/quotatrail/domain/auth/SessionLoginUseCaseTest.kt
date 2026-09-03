package app.quotatrail.domain.auth

import androidx.room.Room
import app.quotatrail.storage.local.db.QuotaTrailDatabase
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.storage.preferences.CurrentAccountStore
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.providers.SessionImportRouter
import app.quotatrail.providers.SessionImporter
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class SessionLoginUseCaseTest {

    private val providerId = ProviderId("deepseek")
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    private val successImporter = object : SessionImporter {
        override suspend fun importFromApiKey(
            apiKey: String,
            account: ProviderAccount,
            apiBaseUrl: String?,
        ): Result<QuotaSnapshot> = Result.success(snapshot(account.localAccountId))

        override suspend fun importFromCookie(
            cookieJson: String,
            account: ProviderAccount,
        ) = Result.failure<QuotaSnapshot>(UnsupportedOperationException())

        override suspend fun importFromOAuthPkce(
            code: String,
            verifier: String,
            redirectUri: String,
            account: ProviderAccount,
        ) = Result.failure<QuotaSnapshot>(UnsupportedOperationException())
    }

    // --- "first account becomes current" ----------------------------------------------------

    @Test
    fun `first imported account becomes the current account`() = runTest {
        withUseCase(initialSelection = null) { useCase, store, _ ->
            val result = useCase.importApiKey(
                providerId = providerId,
                providerDisplayName = "DeepSeek",
                apiKey = "sk-first",
            )

            assert(result.isSuccess)
            // The store must have received an updateCurrentAccountSelection write.
            assertEquals(providerId, store.selection?.providerId)
        }
    }

    // --- "subsequent account must NOT steal current" ----------------------------------------

    @Test
    fun `second imported account does not replace the current account`() = runTest {
        val existing = CurrentAccountSelection(
            providerId = ProviderId("codex"),
            localAccountId = LocalAccountId("local-already-set"),
        )
        withUseCase(initialSelection = existing) { useCase, store, _ ->
            val result = useCase.importApiKey(
                providerId = providerId,
                providerDisplayName = "DeepSeek",
                apiKey = "sk-second",
            )

            assert(result.isSuccess)
            // Selection must be unchanged — still pointing at the pre-existing account.
            assertEquals(existing, store.selection)
        }
    }

    // --- re-login binds to the existing account ---------------------------------------------

    @Test
    fun `relogin overwrites the existing account in place without creating a duplicate`() = runTest {
        withUseCase(initialSelection = null) { useCase, store, database ->
            val first = useCase.importApiKey(
                providerId = providerId,
                providerDisplayName = "DeepSeek",
                apiKey = "sk-first",
                label = "My DeepSeek",
            ).getOrThrow()
            // Simulate the account having gone stale.
            database.providerAccountDao().updateStatus(
                providerId = providerId.value,
                localAccountId = first.localAccountId.value,
                status = "needs_reauth",
                updatedAt = 0L,
            )
            val selectionBefore = store.selection

            val result = useCase.reloginApiKey(localAccountId = first.localAccountId, apiKey = "sk-renewed")

            assert(result.isSuccess)
            val accounts = database.providerAccountDao().listByProvider(providerId.value)
            assertEquals(1, accounts.size)
            val rebound = accounts.single()
            assertEquals(first.localAccountId.value, rebound.localAccountId)
            assertEquals("My DeepSeek", rebound.displayName)
            assertEquals("active", rebound.status)
            // Re-login never changes which account is current.
            assertEquals(selectionBefore, store.selection)
        }
    }

    @Test
    fun `relogin fails when the target account no longer exists`() = runTest {
        withUseCase(initialSelection = null) { useCase, _, _ ->
            val result = useCase.reloginApiKey(
                localAccountId = LocalAccountId("deepseek-missing"),
                apiKey = "sk-renewed",
            )

            assert(result.isFailure)
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private suspend fun withUseCase(
        initialSelection: CurrentAccountSelection?,
        block: suspend (SessionLoginUseCase, RecordingCurrentAccountStore, QuotaTrailDatabase) -> Unit,
    ) {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuotaTrailDatabase::class.java,
        ).build()
        val store = RecordingCurrentAccountStore(initialSelection)
        val router = SessionImportRouter(mapOf(providerId to successImporter))
        val useCase = SessionLoginUseCase(
            importRouter = router,
            database = database,
            accountDao = database.providerAccountDao(),
            snapshotDao = database.quotaSnapshotDao(),
            currentAccountStore = store,
            clock = fixedClock,
        )
        try {
            block(useCase, store, database)
        } finally {
            database.close()
        }
    }

    private fun snapshot(localAccountId: LocalAccountId): QuotaSnapshot =
        QuotaSnapshot(
            snapshotId = SnapshotId("${localAccountId.value}:snap"),
            providerId = providerId,
            localAccountId = localAccountId,
            providerAccountId = null,
            fetchedAt = fixedClock.instant(),
            source = QuotaSnapshotSource.ApiKeyImport,
            planType = null,
            windows = emptyList(),
            credits = null,
            responseDigest = null,
        )

    /**
     * A fake [CurrentAccountStore] that records every write.
     * Reads always return the value last written (or the initial value).
     */
    private class RecordingCurrentAccountStore(
        initial: CurrentAccountSelection?,
    ) : CurrentAccountStore {
        var selection: CurrentAccountSelection? = initial
            private set

        /** How many times [updateCurrentAccountSelection] was called. */
        var writeCount: Int = 0
            private set

        override suspend fun currentAccountSelection(): CurrentAccountSelection? = selection

        override suspend fun updateCurrentAccountSelection(selection: CurrentAccountSelection?) {
            this.selection = selection
            writeCount++
        }
    }
}
