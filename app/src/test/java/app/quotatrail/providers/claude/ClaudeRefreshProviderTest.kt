package app.quotatrail.providers.claude

import app.quotatrail.storage.secure.EncryptedPayload
import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.claude.dto.ClaudeUsageResponseDto
import app.quotatrail.providers.claude.network.ClaudeUsageClient
import app.quotatrail.providers.claude.session.ClaudeSessionPayload
import app.quotatrail.providers.common.auth.OAuthTokenClient
import app.quotatrail.sync.ProviderRefreshResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeRefreshProviderTest {
    private val initialNow = Instant.parse("2026-08-23T00:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `refreshes thirty minutes before expiry and persists rotated refresh token`() = runTest {
        val clock = MutableClock(initialNow)
        val store = storeWith(
            session(
                accessToken = "access-0",
                refreshToken = "refresh-0",
                expiresAt = initialNow.epochSecond + 20 * 60,
            ),
        )
        val refreshInputs = mutableListOf<String>()
        val usageInputs = mutableListOf<String>()
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = { token ->
                refreshInputs += token
                tokenSuccess("access-1", "refresh-1", expiresIn = 28_800)
            },
            usageFetcher = { token ->
                usageInputs += token
                usageSuccess()
            },
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Success)
        assertEquals(listOf("refresh-0"), refreshInputs)
        assertEquals(listOf("access-1"), usageInputs)
        val saved = store.currentSession()
        assertEquals("access-1", saved.accessToken)
        assertEquals("refresh-1", saved.refreshToken)
        assertEquals(initialNow.epochSecond + 28_800, saved.tokenExpiresAtEpochSeconds)
    }

    @Test
    fun `usage 401 triggers one silent refresh and one retry before reauth`() = runTest {
        val clock = MutableClock(initialNow)
        val store = storeWith(
            session(
                accessToken = "access-stale",
                refreshToken = "refresh-0",
                expiresAt = initialNow.epochSecond + 4 * 60 * 60,
            ),
        )
        val usageInputs = mutableListOf<String>()
        var refreshCalls = 0
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = {
                refreshCalls += 1
                tokenSuccess("access-fresh", "refresh-1", expiresIn = 28_800)
            },
            usageFetcher = { token ->
                usageInputs += token
                if (token == "access-stale") usageAuthFailure() else usageSuccess()
            },
        )

        val result = provider.refresh(account(), RefreshTrigger.AppOpen)

        assertTrue(result is ProviderRefreshResult.Success)
        assertEquals(1, refreshCalls)
        assertEquals(listOf("access-stale", "access-fresh"), usageInputs)
        assertEquals("refresh-1", store.currentSession().refreshToken)
    }

    @Test
    fun `usage auth failure after successful token rotation stays retryable`() = runTest {
        val clock = MutableClock(initialNow)
        val store = storeWith(
            session(
                accessToken = "access-expiring",
                refreshToken = "refresh-0",
                expiresAt = initialNow.epochSecond,
            ),
        )
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = {
                tokenSuccess("access-fresh", "refresh-1", expiresIn = 28_800)
            },
            usageFetcher = { usageAuthFailure() },
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Failure)
        val error = (result as ProviderRefreshResult.Failure).error
        assertTrue(error is QuotaError.Network)
        assertTrue(error.retryable)
        assertEquals("refresh-1", store.currentSession().refreshToken)
    }

    @Test
    fun `refresh endpoint 403 stays retryable instead of expiring the account`() = runTest {
        val clock = MutableClock(initialNow)
        val original = session(
            accessToken = "access-expiring",
            refreshToken = "refresh-0",
            expiresAt = initialNow.epochSecond,
        )
        val store = storeWith(original)
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = {
                OAuthTokenClient.Result.Failure(
                    QuotaError.AuthRequired(
                        httpStatus = 403,
                        diagnosticsDigest = "claude_oauth_refresh_auth_403",
                    ),
                )
            },
            usageFetcher = { usageSuccess() },
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Failure)
        val error = (result as ProviderRefreshResult.Failure).error
        assertTrue(error is QuotaError.Network)
        assertTrue(error.retryable)
        assertEquals(original, store.currentSession())
    }

    @Test
    fun `refresh response without optional metadata preserves refresh token and derives safe expiry`() = runTest {
        val clock = MutableClock(initialNow)
        val store = storeWith(
            session(
                accessToken = "access-0",
                refreshToken = "refresh-0",
                expiresAt = initialNow.epochSecond,
            ),
        )
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = {
                tokenSuccess(accessToken = "access-1", refreshToken = null, expiresIn = null)
            },
            usageFetcher = { usageSuccess() },
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Success)
        val saved = store.currentSession()
        assertEquals("refresh-0", saved.refreshToken)
        assertEquals(initialNow.epochSecond + 8 * 60 * 60, saved.tokenExpiresAtEpochSeconds)
    }

    @Test
    fun `concurrent refresh requests rotate a single-use refresh token only once`() = runTest {
        val clock = MutableClock(initialNow)
        val store = storeWith(
            session(
                accessToken = "access-0",
                refreshToken = "refresh-0",
                expiresAt = initialNow.epochSecond,
            ),
        )
        val refreshInputs = mutableListOf<String>()
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = { token ->
                refreshInputs += token
                delay(100)
                tokenSuccess("access-1", "refresh-1", expiresIn = 28_800)
            },
            usageFetcher = { usageSuccess() },
        )

        val results = listOf(
            async { provider.refresh(account(), RefreshTrigger.AppOpen) },
            async { provider.refresh(account(), RefreshTrigger.Widget) },
        ).awaitAll()

        assertTrue(results.all { it is ProviderRefreshResult.Success })
        assertEquals(listOf("refresh-0"), refreshInputs)
        assertEquals("refresh-1", store.currentSession().refreshToken)
    }

    @Test
    fun `rotated credentials survive four access-token lifetimes without interactive login`() = runTest {
        val clock = MutableClock(initialNow)
        val store = storeWith(
            session(
                accessToken = "access-0",
                refreshToken = "refresh-0",
                expiresAt = initialNow.epochSecond,
            ),
        )
        val refreshInputs = mutableListOf<String>()
        var generation = 0
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = { token ->
                refreshInputs += token
                generation += 1
                tokenSuccess(
                    accessToken = "access-$generation",
                    refreshToken = "refresh-$generation",
                    expiresIn = 28_800,
                )
            },
            usageFetcher = { usageSuccess() },
        )

        repeat(4) {
            val result = provider.refresh(account(), RefreshTrigger.Periodic)
            assertTrue(result is ProviderRefreshResult.Success)
            clock.advanceSeconds(8 * 60 * 60)
        }

        assertEquals(
            listOf("refresh-0", "refresh-1", "refresh-2", "refresh-3"),
            refreshInputs,
        )
        assertEquals("refresh-4", store.currentSession().refreshToken)
    }

    @Test
    fun `transient refresh failure stays retryable and does not replace stored credentials`() = runTest {
        val clock = MutableClock(initialNow)
        val original = session(
            accessToken = "access-0",
            refreshToken = "refresh-0",
            expiresAt = initialNow.epochSecond,
        )
        val store = storeWith(original)
        val provider = provider(
            clock = clock,
            store = store,
            tokenRefresher = {
                OAuthTokenClient.Result.Failure(
                    QuotaError.Network(diagnosticsDigest = "claude_oauth_refresh_network"),
                )
            },
            usageFetcher = { usageSuccess() },
        )

        val result = provider.refresh(account(), RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Failure)
        val error = (result as ProviderRefreshResult.Failure).error
        assertTrue(error is QuotaError.Network)
        assertTrue(error.retryable)
        assertEquals(original, store.currentSession())
    }

    private fun provider(
        clock: Clock,
        store: InMemorySessionStore,
        tokenRefresher: suspend (String) -> OAuthTokenClient.Result,
        usageFetcher: suspend (String) -> ClaudeUsageClient.Result,
    ): ClaudeRefreshProvider = ClaudeRefreshProvider(
        usageFetcher = usageFetcher,
        tokenRefresher = tokenRefresher,
        sessionStore = store,
        payloadCipher = PlaintextTestCipher,
        clock = clock,
    )

    private fun account(): ProviderAccount = ProviderAccount.createNew(
        localAccountId = LocalAccountId("claude-local-1"),
        providerId = ProviderId("claude"),
        providerAccountId = null,
        displayName = "Claude",
        now = initialNow,
    )

    private fun session(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
    ): ClaudeSessionPayload = ClaudeSessionPayload(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenExpiresAtEpochSeconds = expiresAt,
    )

    private fun storeWith(session: ClaudeSessionPayload): InMemorySessionStore {
        val plaintext = json.encodeToString(ClaudeSessionPayload.serializer(), session).encodeToByteArray()
        return InMemorySessionStore(
            ProviderSessionEnvelope(
                providerId = "claude",
                localAccountId = "claude-local-1",
                providerAccountId = null,
                schemaVersion = 1,
                payloadCiphertext = plaintext,
                payloadNonce = byteArrayOf(1),
                createdAt = initialNow.toString(),
                updatedAt = initialNow.toString(),
            ),
        )
    }

    private fun tokenSuccess(
        accessToken: String,
        refreshToken: String?,
        expiresIn: Long?,
    ): OAuthTokenClient.Result = OAuthTokenClient.Result.Success(
        OAuthTokenClient.OAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = expiresIn,
            idToken = null,
        ),
    )

    private fun usageSuccess(): ClaudeUsageClient.Result = ClaudeUsageClient.Result.Success(
        ClaudeUsageResponseDto(
            five_hour = ClaudeUsageResponseDto.Window(
                utilization = 25.0,
                resets_at = "2026-08-23T05:00:00Z",
            ),
        ),
    )

    private fun usageAuthFailure(): ClaudeUsageClient.Result = ClaudeUsageClient.Result.Failure(
        QuotaError.AuthRequired(httpStatus = 401, diagnosticsDigest = "claude_auth_required_401"),
    )

    private inner class InMemorySessionStore(
        private var envelope: ProviderSessionEnvelope,
    ) : SecureSessionStore {
        override suspend fun save(envelope: ProviderSessionEnvelope) {
            this.envelope = envelope
        }

        override suspend fun load(providerId: String, localAccountId: String): ProviderSessionEnvelope = envelope

        override suspend fun delete(providerId: String, localAccountId: String) = Unit

        fun currentSession(): ClaudeSessionPayload = json.decodeFromString(
            PlaintextTestCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
        )
    }

    private object PlaintextTestCipher : PayloadCipher {
        override fun encrypt(plaintext: ByteArray): EncryptedPayload =
            EncryptedPayload(ciphertext = plaintext, nonce = byteArrayOf(1))

        override fun decrypt(ciphertext: ByteArray, nonce: ByteArray): ByteArray = ciphertext
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
}
