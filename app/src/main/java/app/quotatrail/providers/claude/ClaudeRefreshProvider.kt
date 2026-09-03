package app.quotatrail.providers.claude

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.claude.mapper.ClaudeUsageMapper
import app.quotatrail.providers.claude.network.ClaudeUsageClient
import app.quotatrail.providers.claude.session.ClaudeSessionPayload
import app.quotatrail.providers.common.auth.OAuthTokenClient
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Clock

class ClaudeRefreshProvider(
    private val usageFetcher: suspend (String) -> ClaudeUsageClient.Result,
    private val tokenRefresher: suspend (String) -> OAuthTokenClient.Result,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshTokenMutex = Mutex()

    override suspend fun refresh(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult = refreshTokenMutex.withLock {
        refreshLocked(account = account, trigger = trigger)
    }

    private suspend fun refreshLocked(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult {
        // Load inside the mutex. Anthropic rotates refresh tokens, so a waiter must observe the
        // token saved by the preceding refresh instead of reusing a now-invalid single-use token.
        val envelope = sessionStore.load(account.providerId.value, account.localAccountId.value)
            ?: return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_session_missing"),
            )
        var session = try {
            json.decodeFromString<ClaudeSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_session_decode_failed"),
            )
        }

        // Refresh early enough to absorb scheduler delay and device/server clock skew.
        val nowEpoch = clock.instant().epochSecond
        val refreshDue = session.tokenExpiresAtEpochSeconds
            ?.let { it <= nowEpoch + PROACTIVE_REFRESH_MARGIN_SECONDS }
            ?: false
        var refreshedThisAttempt = false
        if (refreshDue) {
            when (val refreshed = refreshSession(session, envelope)) {
                is SessionRefreshResult.Failure -> return ProviderRefreshResult.Failure(refreshed.error)
                is SessionRefreshResult.Success -> {
                    session = refreshed.session
                    refreshedThisAttempt = true
                }
            }
        }

        var usageResult = usageFetcher(session.accessToken)
        if (usageResult.isAuthRequired() && !refreshedThisAttempt) {
            // The server is authoritative: tokens may be revoked slightly before the local expiry.
            // Try one silent rotation and retry once before asking the user to sign in again.
            when (val refreshed = refreshSession(session, envelope)) {
                is SessionRefreshResult.Failure -> return ProviderRefreshResult.Failure(refreshed.error)
                is SessionRefreshResult.Success -> {
                    session = refreshed.session
                    refreshedThisAttempt = true
                    usageResult = usageFetcher(session.accessToken)
                }
            }
        }

        // A successful token rotation proves the refresh credential is still valid. If the usage
        // edge immediately answers 401/403 anyway, keep the account connected and retry later;
        // only a refresh-token rejection is authoritative enough to require interactive login.
        if (usageResult.isAuthRequired() && refreshedThisAttempt) {
            return ProviderRefreshResult.Failure(
                QuotaError.Network(diagnosticsDigest = "claude_usage_auth_after_refresh"),
            )
        }

        return usageResult.toProviderResult(account = account, trigger = trigger)
    }

    private suspend fun refreshSession(
        session: ClaudeSessionPayload,
        previous: ProviderSessionEnvelope,
    ): SessionRefreshResult {
        val refreshToken = session.refreshToken
            ?: return SessionRefreshResult.Failure(
                QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_refresh_token_missing"),
            )

        return when (val refreshed = tokenRefresher(refreshToken)) {
            is OAuthTokenClient.Result.Failure -> SessionRefreshResult.Failure(
                // Anthropic's edge can return 403 for bot/WAF policy before evaluating the grant.
                // It is not proof that the rotating refresh token is invalid, so preserve it and
                // retry instead of forcing the user through OAuth again.
                if (refreshed.error is QuotaError.AuthRequired && refreshed.error.httpStatus == 403) {
                    QuotaError.Network(diagnosticsDigest = "claude_oauth_refresh_http_403")
                } else {
                    refreshed.error
                },
            )
            is OAuthTokenClient.Result.Success -> {
                val now = clock.instant()
                val expiresIn = refreshed.tokens.expiresInSeconds
                    ?.takeIf { it > 0 }
                    ?: DEFAULT_ACCESS_TOKEN_TTL_SECONDS
                val updated = session.copy(
                    accessToken = refreshed.tokens.accessToken,
                    // Claude rotates refresh tokens. Preserve the previous token only when a valid
                    // refresh response omits the optional replacement field.
                    refreshToken = refreshed.tokens.refreshToken ?: session.refreshToken,
                    // Some valid refresh responses omit expires_in. Never retain an already-expired
                    // timestamp, which would rotate the single-use refresh token on every poll.
                    tokenExpiresAtEpochSeconds = now.epochSecond + expiresIn,
                )
                try {
                    persist(updated, previous, now)
                    SessionRefreshResult.Success(updated)
                } catch (_: Exception) {
                    SessionRefreshResult.Failure(
                        QuotaError.Network(diagnosticsDigest = "claude_refresh_session_save_failed"),
                    )
                }
            }
        }
    }

    private suspend fun persist(
        session: ClaudeSessionPayload,
        previous: ProviderSessionEnvelope,
        now: Instant,
    ) {
        val jsonBytes = json.encodeToString(ClaudeSessionPayload.serializer(), session).encodeToByteArray()
        val encrypted = payloadCipher.encrypt(jsonBytes)
        sessionStore.save(
            previous.copy(
                payloadCiphertext = encrypted.ciphertext,
                payloadNonce = encrypted.nonce,
                updatedAt = now.toString(),
            ),
        )
    }

    private fun ClaudeUsageClient.Result.isAuthRequired(): Boolean =
        this is ClaudeUsageClient.Result.Failure && error is QuotaError.AuthRequired

    private fun ClaudeUsageClient.Result.toProviderResult(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult = when (this) {
        is ClaudeUsageClient.Result.Failure -> ProviderRefreshResult.Failure(error)
        is ClaudeUsageClient.Result.Success -> ProviderRefreshResult.Success(
            ClaudeUsageMapper.map(
                dto = dto,
                localAccountId = account.localAccountId,
                providerAccountId = account.providerAccountId,
                fetchedAt = clock.instant(),
                source = trigger.toSnapshotSource(),
            ),
        )
    }

    private fun RefreshTrigger.toSnapshotSource(): QuotaSnapshotSource = when (this) {
        RefreshTrigger.AppOpen -> QuotaSnapshotSource.AppOpenRefresh
        RefreshTrigger.Manual -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Widget -> QuotaSnapshotSource.WidgetRefresh
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.OAuthPkceLogin
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    companion object {
        val CLAUDE_PROVIDER_ID = ProviderId("claude")
        private const val PROACTIVE_REFRESH_MARGIN_SECONDS = 30L * 60L
        private const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 8L * 60L * 60L
    }

    private sealed interface SessionRefreshResult {
        data class Success(val session: ClaudeSessionPayload) : SessionRefreshResult
        data class Failure(val error: QuotaError) : SessionRefreshResult
    }
}
