package app.quotatrail.providers.codex

import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.codex.auth.CodexAccessTokenExpiry
import app.quotatrail.providers.codex.auth.CodexTokenRefresher
import app.quotatrail.providers.codex.mapper.CodexUsageMapper
import app.quotatrail.providers.codex.network.CodexUsageClient
import app.quotatrail.providers.codex.session.CodexSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CodexRefreshProvider(
    private val sessionStore: SecureSessionStore,
    private val sessionCipher: CodexSessionCipher,
    private val tokenRefresh: CodexTokenRefresh,
    private val usageFetcher: CodexUsageFetcher,
    private val mapper: CodexUsageMapper = CodexUsageMapper(),
    private val clock: Clock = Clock.systemUTC(),
) : RefreshProvider {
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
        // Load after acquiring the lock so waiters see any rotated refresh token saved by the
        // preceding request. OpenAI can invalidate the previous refresh token on rotation.
        val loadedEnvelope = loadEnvelope(account) ?: return authRequired("codex_refresh_session_missing")
        var session = sessionCipher.decrypt(loadedEnvelope).getOrElse {
            return authRequired("codex_refresh_session_decrypt_failed")
        }
        var refreshedThisAttempt = false

        // The previous build rotated a refresh token on every quota poll. Use the current access
        // token until shortly before its expiry, reducing rotation from dozens per day to about one
        // per access-token lifetime.
        if (CodexAccessTokenExpiry.isRefreshDue(session, clock.instant())) {
            when (val refreshed = refreshAndPersist(session, loadedEnvelope)) {
                is SessionRefreshResult.Failure -> return ProviderRefreshResult.Failure(refreshed.error)
                is SessionRefreshResult.Success -> {
                    session = refreshed.session
                    refreshedThisAttempt = true
                }
            }
        }

        var providerAccountId = session.providerAccountIdOr(account.providerAccountId)
        var usage = usageFetcher.fetchUsage(
            accessToken = session.accessToken,
            accountId = providerAccountId?.value,
        )
        if (usage.isAuthRequired() && !refreshedThisAttempt) {
            // The server is authoritative when it rejects a token before its local/JWT expiry.
            // Rotate once and retry once before requiring another interactive browser login.
            when (val refreshed = refreshAndPersist(session, loadedEnvelope)) {
                is SessionRefreshResult.Failure -> return ProviderRefreshResult.Failure(refreshed.error)
                is SessionRefreshResult.Success -> {
                    session = refreshed.session
                    providerAccountId = session.providerAccountIdOr(account.providerAccountId)
                    usage = usageFetcher.fetchUsage(
                        accessToken = session.accessToken,
                        accountId = providerAccountId?.value,
                    )
                }
            }
        }

        return when (usage) {
            is CodexUsageClient.Result.Failure -> ProviderRefreshResult.Failure(usage.error)
            is CodexUsageClient.Result.Success -> ProviderRefreshResult.Success(
                mapper.map(
                    dto = usage.dto,
                    localAccountId = account.localAccountId,
                    providerAccountId = providerAccountId,
                    fetchedAt = clock.instant(),
                    source = trigger.toSnapshotSource(),
                ),
            )
        }
    }

    private suspend fun refreshAndPersist(
        session: CodexSessionPayload,
        envelope: ProviderSessionEnvelope,
    ): SessionRefreshResult =
        when (val refresh = tokenRefresh.refresh(session)) {
            is CodexTokenRefresher.Result.Failure -> SessionRefreshResult.Failure(refresh.error)
            is CodexTokenRefresher.Result.Success -> {
                val refreshed = refresh.session
                val providerAccountId = refreshed.providerAccountIdOr(
                    envelope.providerAccountId?.takeIf { it.isNotBlank() }?.let(::ProviderAccountId),
                )
                val saveError = saveRefreshedSession(
                    session = refreshed,
                    envelope = envelope.copy(providerAccountId = providerAccountId?.value),
                )
                if (saveError == null) {
                    SessionRefreshResult.Success(refreshed)
                } else {
                    SessionRefreshResult.Failure(saveError)
                }
            }
        }

    private fun CodexSessionPayload.providerAccountIdOr(fallback: ProviderAccountId?): ProviderAccountId? =
        accountId
            ?.takeIf { it.isNotBlank() }
            ?.let(::ProviderAccountId)
            ?: fallback

    private fun CodexUsageClient.Result.isAuthRequired(): Boolean =
        this is CodexUsageClient.Result.Failure && error is QuotaError.AuthRequired

    private suspend fun loadEnvelope(account: ProviderAccount): ProviderSessionEnvelope? =
        if (account.providerId == CODEX_PROVIDER_ID) {
            sessionStore.load(
                providerId = account.providerId.value,
                localAccountId = account.localAccountId.value,
            )
        } else {
            null
        }

    private suspend fun saveRefreshedSession(
        session: CodexSessionPayload,
        envelope: ProviderSessionEnvelope,
    ): QuotaError? =
        try {
            sessionStore.save(
                sessionCipher.encrypt(
                    session = session,
                    envelope = envelope,
                    updatedAt = clock.instant(),
                ),
            )
            null
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            QuotaError.Network(diagnosticsDigest = "codex_refresh_session_save_failed")
        }

    private fun authRequired(diagnosticsDigest: String): ProviderRefreshResult.Failure =
        ProviderRefreshResult.Failure(
            QuotaError.AuthRequired(
                httpStatus = null,
                diagnosticsDigest = diagnosticsDigest,
            ),
        )

    private fun RefreshTrigger.toSnapshotSource(): QuotaSnapshotSource =
        when (this) {
            RefreshTrigger.AppOpen -> QuotaSnapshotSource.AppOpenRefresh
            RefreshTrigger.Manual -> QuotaSnapshotSource.ManualRefresh
            RefreshTrigger.Widget -> QuotaSnapshotSource.WidgetRefresh
            RefreshTrigger.ImportValidation -> QuotaSnapshotSource.AuthJsonImport
            RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
            RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
        }

    private companion object {
        val CODEX_PROVIDER_ID = ProviderId("codex")
    }

    private sealed interface SessionRefreshResult {
        data class Success(val session: CodexSessionPayload) : SessionRefreshResult
        data class Failure(val error: QuotaError) : SessionRefreshResult
    }
}

interface CodexSessionCipher {
    fun decrypt(envelope: ProviderSessionEnvelope): Result<CodexSessionPayload>

    fun encrypt(
        session: CodexSessionPayload,
        envelope: ProviderSessionEnvelope,
        updatedAt: Instant,
    ): ProviderSessionEnvelope
}

fun interface CodexTokenRefresh {
    suspend fun refresh(session: CodexSessionPayload): CodexTokenRefresher.Result
}

fun interface CodexUsageFetcher {
    suspend fun fetchUsage(
        accessToken: String,
        accountId: String?,
    ): CodexUsageClient.Result
}
