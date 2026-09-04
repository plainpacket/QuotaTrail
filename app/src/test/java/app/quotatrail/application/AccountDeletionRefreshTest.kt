package app.quotatrail.application

import androidx.room.Room
import app.quotatrail.providers.codex.CodexRefreshProvider
import app.quotatrail.providers.codex.CodexSessionCipher
import app.quotatrail.providers.codex.CodexTokenRefresh
import app.quotatrail.providers.codex.CodexUsageFetcher
import app.quotatrail.providers.codex.auth.CodexTokenRefresher
import app.quotatrail.providers.codex.dto.CodexUsageResponseDto
import app.quotatrail.providers.codex.network.CodexUsageClient
import app.quotatrail.providers.codex.session.CodexSessionPayload
import app.quotatrail.sync.RefreshProvider
import app.quotatrail.domain.account.AccountDeleteUseCase
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import app.quotatrail.domain.account.NoopDeletedAccountStateCleaner
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.RefreshAttemptId
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.claude.ClaudeRefreshProvider
import app.quotatrail.providers.claude.dto.ClaudeUsageResponseDto
import app.quotatrail.providers.claude.network.ClaudeUsageClient
import app.quotatrail.providers.claude.session.ClaudeSessionPayload
import app.quotatrail.providers.common.auth.OAuthTokenClient
import app.quotatrail.storage.local.db.QuotaTrailDatabase
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.storage.preferences.CurrentAccountStore
import app.quotatrail.storage.repository.AccountDeletionRepository
import app.quotatrail.storage.repository.RoomQuotaSnapshotStore
import app.quotatrail.storage.repository.RoomRefreshAttemptStore
import app.quotatrail.storage.repository.toEntity
import app.quotatrail.storage.secure.EncryptedPayload
import app.quotatrail.storage.secure.FakeSecureSessionStore
import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.sync.AttemptIdProvider
import app.quotatrail.sync.UsageSyncCoordinator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Room persistence and refresh flow, with synthetic credentials and a test-only cipher. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountDeletionRefreshTest {
    @Test
    fun deletingClaudeDuringRefreshDoesNotRecreateData() = verifyDeletion("claude")

    @Test
    fun deletingCodexDuringRefreshDoesNotRecreateData() = verifyDeletion("codex")

    private fun verifyDeletion(providerName: String) = runTest {
        val now = Instant.parse("2026-09-04T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val account = ProviderAccount.createNew(
            LocalAccountId("delete-race"), ProviderId(providerName), null, "Review", now,
        )
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), QuotaTrailDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            db.providerAccountDao().upsert(account.toEntity())
            val store = FakeSecureSessionStore()
            val initialSession = ClaudeSessionPayload(
                accessToken = "synthetic-old", refreshToken = "synthetic-refresh",
                tokenExpiresAtEpochSeconds = now.epochSecond - 1,
            )
            store.save(ProviderSessionEnvelope(
                providerId = providerName, localAccountId = "delete-race", providerAccountId = null,
                schemaVersion = 1, payloadCiphertext = Json.encodeToString(initialSession).encodeToByteArray(),
                payloadNonce = byteArrayOf(1), createdAt = now.toString(), updatedAt = now.toString(),
            ))
            val refreshEntered = CompletableDeferred<Unit>()
            val allowRefreshToFinish = CompletableDeferred<Unit>()
            var refreshCalls = 0
            val provider: RefreshProvider = if (providerName == "claude") ClaudeRefreshProvider(
                usageFetcher = { ClaudeUsageClient.Result.Success(ClaudeUsageResponseDto(
                    five_hour = ClaudeUsageResponseDto.Window(25.0, "2026-09-04T05:00:00Z"),
                )) },
                tokenRefresher = {
                    refreshCalls++
                    refreshEntered.complete(Unit)
                    allowRefreshToFinish.await()
                    OAuthTokenClient.Result.Success(OAuthTokenClient.OAuthTokens(
                        "synthetic-new", "synthetic-next", 28_800, null,
                    ))
                },
                sessionStore = store,
                payloadCipher = object : PayloadCipher {
                    override fun encrypt(plaintext: ByteArray) = EncryptedPayload(plaintext, byteArrayOf(1))
                    override fun decrypt(ciphertext: ByteArray, nonce: ByteArray) = ciphertext
                },
                clock = clock,
            ) else CodexRefreshProvider(
                sessionStore = store,
                sessionCipher = object : CodexSessionCipher {
                    override fun decrypt(envelope: ProviderSessionEnvelope) = Result.success(
                        CodexSessionPayload("synthetic-old", "synthetic-refresh", null, null,
                            lastRefresh = now.minusSeconds(3600), accessTokenExpiresAt = now.minusSeconds(1)),
                    )
                    override fun encrypt(session: CodexSessionPayload, envelope: ProviderSessionEnvelope, updatedAt: Instant) =
                        envelope.copy(payloadCiphertext = session.refreshToken.encodeToByteArray(), updatedAt = updatedAt.toString())
                },
                tokenRefresh = CodexTokenRefresh { session ->
                    refreshCalls++
                    refreshEntered.complete(Unit)
                    allowRefreshToFinish.await()
                    CodexTokenRefresher.Result.Success(session.copy(
                        accessToken = "synthetic-new", refreshToken = "synthetic-next",
                        accessTokenExpiresAt = now.plusSeconds(28_800),
                    ))
                },
                usageFetcher = CodexUsageFetcher { _, _ -> CodexUsageClient.Result.Success(
                    Json.decodeFromString<CodexUsageResponseDto>("""{"rate_limit":{"primary_window":{"used_percent":25,"limit_window_seconds":604800,"reset_at":1789084800}}}"""),
                ) },
                clock = clock,
            )
            val coordinator = UsageSyncCoordinator(
                accountExists = { db.providerAccountDao().getById(it.localAccountId.value) != null },
                provider = provider,
                snapshotStore = RoomQuotaSnapshotStore(db.quotaSnapshotDao()),
                attemptStore = RoomRefreshAttemptStore(db.refreshAttemptDao()),
                attemptIdProvider = AttemptIdProvider { RefreshAttemptId("delete-race-attempt") },
                clock = clock,
            )
            val refresh = async { coordinator.refresh(account, RefreshTrigger.Periodic) }
            refreshEntered.await()
            val deletion = AccountDeletionRepository(
                db, store,
                object : CurrentAccountStore {
                    override suspend fun currentAccountSelection(): CurrentAccountSelection? = null
                    override suspend fun updateCurrentAccountSelection(selection: CurrentAccountSelection?) = Unit
                },
                NoopDeletedAccountStateCleaner,
            )
            var republished = false
            val useCase = CoordinatedAccountDeletion(
                coordinator, AccountDeleteUseCase(deletion::deleteAccount),
                onDeleted = {
                    assertNull(db.providerAccountDao().getById("delete-race"))
                    republished = true
                },
            )
            val deleting = async { useCase.deleteAccount(account.providerId, account.localAccountId) }
            runCurrent()
            assertFalse("Deletion waits for the in-flight refresh", deleting.isCompleted)
            allowRefreshToFinish.complete(Unit)
            refresh.await()
            deleting.await()
            assertTrue(republished)
            // A worker may still hold an account list loaded before deletion.
            coordinator.refresh(account, RefreshTrigger.Periodic)
            assertEquals(1, refreshCalls)

            val sessionRecreated = store.load(providerName, "delete-race") != null
            val snapshotRecreated = db.quotaSnapshotDao().getLatestForAccount(providerName, "delete-race") != null
            val attemptRecreated = db.refreshAttemptDao().getLatestForAccount(providerName, "delete-race") != null
            assertNull("The account row must stay deleted", db.providerAccountDao().getById("delete-race"))
            assertTrue(
                "Deleted data recreated: session=$sessionRecreated, snapshot=$snapshotRecreated, attempt=$attemptRecreated",
                !sessionRecreated && !snapshotRecreated && !attemptRecreated,
            )
        } finally {
            db.close()
        }
    }
}
