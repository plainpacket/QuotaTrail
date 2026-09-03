package app.quotatrail.providers.codex

import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.codex.auth.CodexTokenRefresher
import app.quotatrail.providers.codex.dto.CodexRateLimitDto
import app.quotatrail.providers.codex.dto.CodexUsageResponseDto
import app.quotatrail.providers.codex.dto.CodexWindowDto
import app.quotatrail.providers.codex.network.CodexUsageClient
import app.quotatrail.providers.codex.session.CodexSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexRefreshProviderTest {
    private val now: Instant = Instant.parse("2026-05-23T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `refresh loads stored session refreshes token fetches usage and saves updated session`() = runTest {
        val account = account()
        val initialEnvelope = envelope(localAccountId = "local-1")
        val store = RecordingSecureSessionStore(initialEnvelope)
        val cipher = RecordingCodexSessionCipher(
            decryptedSession = CodexSessionPayload(
                accessToken = "old-access",
                refreshToken = "old-refresh",
                idToken = "old-id",
                accountId = "acct-1",
                lastRefresh = Instant.parse("2026-05-23T10:00:00Z"),
            ),
        )
        val tokenRefresh = RecordingTokenRefresh(
            CodexSessionPayload(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                idToken = "new-id",
                accountId = "acct-1",
                lastRefresh = now,
            ),
        )
        val usageFetcher = RecordingUsageFetcher(successfulUsageDto())
        val provider = provider(
            store = store,
            cipher = cipher,
            tokenRefresh = tokenRefresh,
            usageFetcher = usageFetcher,
        )

        val result = provider.refresh(account, RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Success)
        val snapshot = (result as ProviderRefreshResult.Success).snapshot
        assertEquals(QuotaSnapshotSource.BackgroundRefresh, snapshot.source)
        assertEquals(62, snapshot.windows.first().usedPercent)
        assertEquals(listOf("old-refresh"), tokenRefresh.refreshTokens)
        assertEquals(listOf("new-access" to "acct-1"), usageFetcher.requests)
        assertEquals(listOf("new-refresh"), cipher.encryptedRefreshTokens)
        assertEquals("local-1", store.savedEnvelopes.single().localAccountId)
    }

    @Test
    fun `missing session returns auth required without calling network`() = runTest {
        val tokenRefresh = RecordingTokenRefresh(
            CodexSessionPayload(
                accessToken = "unused",
                refreshToken = "unused",
                idToken = null,
                accountId = null,
                lastRefresh = now,
            ),
        )
        val usageFetcher = RecordingUsageFetcher(successfulUsageDto())
        val provider = provider(
            store = RecordingSecureSessionStore(initialEnvelope = null),
            cipher = RecordingCodexSessionCipher(
                decryptedSession = CodexSessionPayload(
                    accessToken = "unused",
                    refreshToken = "unused",
                    idToken = null,
                    accountId = null,
                    lastRefresh = now,
                ),
            ),
            tokenRefresh = tokenRefresh,
            usageFetcher = usageFetcher,
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Failure)
        assertEquals("error_auth_required", (result as ProviderRefreshResult.Failure).error.safeMessageKey)
        assertTrue(tokenRefresh.refreshTokens.isEmpty())
        assertTrue(usageFetcher.requests.isEmpty())
    }

    @Test
    fun `session save failure returns structured retryable failure`() = runTest {
        val provider = provider(
            store = ThrowingSaveSecureSessionStore(envelope(localAccountId = "local-1")),
            cipher = RecordingCodexSessionCipher(
                decryptedSession = CodexSessionPayload(
                    accessToken = "old-access",
                    refreshToken = "old-refresh",
                    idToken = null,
                    accountId = "acct-1",
                    lastRefresh = now,
                    accessTokenExpiresAt = now,
                ),
            ),
            tokenRefresh = RecordingTokenRefresh(
                CodexSessionPayload(
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                    idToken = null,
                    accountId = "acct-1",
                    lastRefresh = now,
                ),
            ),
            usageFetcher = RecordingUsageFetcher(successfulUsageDto()),
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Failure)
        val failure = result as ProviderRefreshResult.Failure
        assertEquals("error_network", failure.error.safeMessageKey)
        assertEquals("codex_refresh_session_save_failed", failure.error.diagnosticsDigest)
    }

    @Test
    fun `valid access token fetches usage without rotating refresh token`() = runTest {
        val cipher = RecordingCodexSessionCipher(
            decryptedSession = CodexSessionPayload(
                accessToken = "still-valid-access",
                refreshToken = "refresh-0",
                idToken = null,
                accountId = "acct-1",
                lastRefresh = now,
                accessTokenExpiresAt = now.plusSeconds(30 * 60),
            ),
        )
        val tokenRefresh = RecordingTokenRefresh(
            cipher.currentSession.copy(accessToken = "should-not-be-used"),
        )
        val usageFetcher = RecordingUsageFetcher(successfulUsageDto())
        val provider = provider(
            store = RecordingSecureSessionStore(envelope("local-1")),
            cipher = cipher,
            tokenRefresh = tokenRefresh,
            usageFetcher = usageFetcher,
        )

        val result = provider.refresh(account(), RefreshTrigger.Widget)

        assertTrue(result is ProviderRefreshResult.Success)
        assertTrue(tokenRefresh.refreshTokens.isEmpty())
        assertEquals(listOf("still-valid-access" to "acct-1"), usageFetcher.requests)
    }

    @Test
    fun `usage 401 silently rotates token and retries once`() = runTest {
        val cipher = RecordingCodexSessionCipher(
            CodexSessionPayload(
                accessToken = "server-rejected-access",
                refreshToken = "refresh-0",
                idToken = null,
                accountId = "acct-1",
                lastRefresh = now,
                accessTokenExpiresAt = now.plusSeconds(30 * 60),
            ),
        )
        val tokenRefresh = RecordingTokenRefresh(
            cipher.currentSession.copy(
                accessToken = "fresh-access",
                refreshToken = "refresh-1",
                lastRefresh = now,
                accessTokenExpiresAt = now.plusSeconds(60 * 60),
            ),
        )
        val usageFetcher = SequenceUsageFetcher(
            ArrayDeque(
                listOf(
                    CodexUsageClient.Result.Failure(
                        app.quotatrail.domain.refresh.QuotaError.AuthRequired(
                            httpStatus = 401,
                            diagnosticsDigest = "codex_usage_auth_required_401",
                        ),
                    ),
                    CodexUsageClient.Result.Success(successfulUsageDto()),
                ),
            ),
        )
        val provider = provider(
            store = RecordingSecureSessionStore(envelope("local-1")),
            cipher = cipher,
            tokenRefresh = tokenRefresh,
            usageFetcher = usageFetcher,
        )

        val result = provider.refresh(account(), RefreshTrigger.Manual)

        assertTrue(result is ProviderRefreshResult.Success)
        assertEquals(listOf("refresh-0"), tokenRefresh.refreshTokens)
        assertEquals(
            listOf("server-rejected-access" to "acct-1", "fresh-access" to "acct-1"),
            usageFetcher.requests,
        )
        assertEquals("refresh-1", cipher.currentSession.refreshToken)
    }

    @Test
    fun `concurrent requests rotate a single-use refresh token only once`() = runTest {
        val mutableClock = MutableClock(now)
        val store = RecordingSecureSessionStore(envelope("local-1"))
        val cipher = RecordingCodexSessionCipher(
            CodexSessionPayload(
                accessToken = "expired-access",
                refreshToken = "refresh-0",
                idToken = null,
                accountId = "acct-1",
                lastRefresh = now.minusSeconds(60 * 60),
                accessTokenExpiresAt = now,
            ),
        )
        val tokenRefresh = DelayedRotatingTokenRefresh(mutableClock)
        val provider = provider(
            store = store,
            cipher = cipher,
            tokenRefresh = tokenRefresh,
            usageFetcher = RecordingUsageFetcher(successfulUsageDto()),
            providerClock = mutableClock,
        )

        val results = listOf(
            async { provider.refresh(account(), RefreshTrigger.AppOpen) },
            async { provider.refresh(account(), RefreshTrigger.Widget) },
        ).awaitAll()

        assertTrue(results.all { it is ProviderRefreshResult.Success })
        assertEquals(listOf("refresh-0"), tokenRefresh.refreshTokens)
        assertEquals("refresh-1", cipher.currentSession.refreshToken)
    }

    @Test
    fun `rotated credentials survive four access-token lifetimes without interactive login`() = runTest {
        val mutableClock = MutableClock(now)
        val store = RecordingSecureSessionStore(envelope("local-1"))
        val cipher = RecordingCodexSessionCipher(
            CodexSessionPayload(
                accessToken = "access-0",
                refreshToken = "refresh-0",
                idToken = null,
                accountId = "acct-1",
                lastRefresh = now.minusSeconds(60 * 60),
                accessTokenExpiresAt = now,
            ),
        )
        val tokenRefresh = DelayedRotatingTokenRefresh(mutableClock, delayMillis = 0)
        val provider = provider(
            store = store,
            cipher = cipher,
            tokenRefresh = tokenRefresh,
            usageFetcher = RecordingUsageFetcher(successfulUsageDto()),
            providerClock = mutableClock,
        )

        repeat(4) {
            val result = provider.refresh(account(), RefreshTrigger.Periodic)
            assertTrue(result is ProviderRefreshResult.Success)
            mutableClock.advanceSeconds(60 * 60)
        }

        assertEquals(listOf("refresh-0", "refresh-1", "refresh-2", "refresh-3"), tokenRefresh.refreshTokens)
        assertEquals("refresh-4", cipher.currentSession.refreshToken)
    }

    private fun provider(
        store: SecureSessionStore,
        cipher: CodexSessionCipher,
        tokenRefresh: CodexTokenRefresh,
        usageFetcher: CodexUsageFetcher,
        providerClock: Clock = clock,
    ): CodexRefreshProvider =
        CodexRefreshProvider(
            sessionStore = store,
            sessionCipher = cipher,
            tokenRefresh = tokenRefresh,
            usageFetcher = usageFetcher,
            clock = providerClock,
        )

    private class RecordingSecureSessionStore(
        initialEnvelope: ProviderSessionEnvelope?,
    ) : SecureSessionStore {
        private var envelope = initialEnvelope
        val savedEnvelopes = mutableListOf<ProviderSessionEnvelope>()

        override suspend fun save(envelope: ProviderSessionEnvelope) {
            savedEnvelopes += envelope
            this.envelope = envelope
        }

        override suspend fun load(providerId: String, localAccountId: String): ProviderSessionEnvelope? = envelope

        override suspend fun delete(providerId: String, localAccountId: String) = Unit
    }

    private class RecordingCodexSessionCipher(
        decryptedSession: CodexSessionPayload,
    ) : CodexSessionCipher {
        var currentSession: CodexSessionPayload = decryptedSession
            private set
        val encryptedRefreshTokens = mutableListOf<String>()

        override fun decrypt(envelope: ProviderSessionEnvelope): Result<CodexSessionPayload> =
            Result.success(currentSession)

        override fun encrypt(
            session: CodexSessionPayload,
            envelope: ProviderSessionEnvelope,
            updatedAt: Instant,
        ): ProviderSessionEnvelope {
            encryptedRefreshTokens += session.refreshToken
            currentSession = session
            return envelope.copy(
                payloadCiphertext = session.refreshToken.toByteArray(),
                updatedAt = updatedAt.toString(),
            )
        }
    }

    private class ThrowingSaveSecureSessionStore(
        private val envelope: ProviderSessionEnvelope,
    ) : SecureSessionStore {
        override suspend fun save(envelope: ProviderSessionEnvelope) {
            throw IllegalStateException("disk unavailable")
        }

        override suspend fun load(providerId: String, localAccountId: String): ProviderSessionEnvelope = envelope

        override suspend fun delete(providerId: String, localAccountId: String) = Unit
    }

    private class RecordingTokenRefresh(
        private val refreshedSession: CodexSessionPayload,
    ) : CodexTokenRefresh {
        val refreshTokens = mutableListOf<String>()

        override suspend fun refresh(session: CodexSessionPayload): CodexTokenRefresher.Result {
            refreshTokens += session.refreshToken
            return CodexTokenRefresher.Result.Success(refreshedSession)
        }
    }

    private class RecordingUsageFetcher(
        private val dto: CodexUsageResponseDto,
    ) : CodexUsageFetcher {
        val requests = mutableListOf<Pair<String, String?>>()

        override suspend fun fetchUsage(accessToken: String, accountId: String?): CodexUsageClient.Result {
            requests += accessToken to accountId
            return CodexUsageClient.Result.Success(dto)
        }
    }

    private class SequenceUsageFetcher(
        private val results: ArrayDeque<CodexUsageClient.Result>,
    ) : CodexUsageFetcher {
        val requests = mutableListOf<Pair<String, String?>>()

        override suspend fun fetchUsage(accessToken: String, accountId: String?): CodexUsageClient.Result {
            requests += accessToken to accountId
            return results.removeFirst()
        }
    }

    private class DelayedRotatingTokenRefresh(
        private val clock: Clock,
        private val delayMillis: Long = 100,
    ) : CodexTokenRefresh {
        val refreshTokens = mutableListOf<String>()
        private var generation = 0

        override suspend fun refresh(session: CodexSessionPayload): CodexTokenRefresher.Result {
            refreshTokens += session.refreshToken
            if (delayMillis > 0) delay(delayMillis)
            generation += 1
            return CodexTokenRefresher.Result.Success(
                session.copy(
                    accessToken = "access-$generation",
                    refreshToken = "refresh-$generation",
                    lastRefresh = clock.instant(),
                    accessTokenExpiresAt = clock.instant().plusSeconds(60 * 60),
                ),
            )
        }
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current

        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }

    private fun account(): ProviderAccount =
        ProviderAccount.createNew(
            localAccountId = LocalAccountId("local-1"),
            providerId = ProviderId("codex"),
            providerAccountId = ProviderAccountId("acct-1"),
            displayName = "Codex",
            now = now,
        )

    private fun envelope(localAccountId: String): ProviderSessionEnvelope =
        ProviderSessionEnvelope(
            providerId = "codex",
            localAccountId = localAccountId,
            providerAccountId = "acct-1",
            schemaVersion = 1,
            payloadCiphertext = byteArrayOf(1, 2, 3),
            payloadNonce = byteArrayOf(4, 5, 6),
            createdAt = now.toString(),
            updatedAt = now.toString(),
        )

    private fun successfulUsageDto(): CodexUsageResponseDto =
        CodexUsageResponseDto(
            planType = "plus",
            rateLimit = CodexRateLimitDto(
                primaryWindow = CodexWindowDto(
                    usedPercent = 62,
                    resetAt = 1_779_426_000,
                    limitWindowSeconds = 18_000,
                ),
                secondaryWindow = CodexWindowDto(
                    usedPercent = 41,
                    resetAt = 1_779_480_000,
                    limitWindowSeconds = 604_800,
                ),
            ),
        )
}
