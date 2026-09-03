package app.quotatrail.providers.antigravity

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.antigravity.mapper.AntigravityQuotaMapper
import app.quotatrail.providers.antigravity.network.AntigravityQuotaClient
import app.quotatrail.providers.antigravity.session.AntigravitySessionPayload
import app.quotatrail.providers.common.auth.OAuthTokenClient
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock

class AntigravityRefreshProvider(
    private val client: AntigravityQuotaClient,
    private val tokenClient: OAuthTokenClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun refresh(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult {
        val envelope = sessionStore.load(account.providerId.value, account.localAccountId.value)
            ?: return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "antigravity_session_missing",
                ),
            )
        var session = try {
            json.decodeFromString<AntigravitySessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "antigravity_session_decode_failed",
                ),
            )
        }

        // Refresh the Google access token before it expires rather than forcing a re-login.
        val nowEpoch = clock.instant().epochSecond
        val expired = session.tokenExpiresAtEpochSeconds?.let { it <= nowEpoch + EXPIRY_SKEW_SECONDS } ?: false
        if (expired) {
            val refreshToken = session.refreshToken
                ?: return ProviderRefreshResult.Failure(
                    QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "antigravity_token_expired"),
                )
            when (val refreshed = tokenClient.refresh(refreshToken)) {
                is OAuthTokenClient.Result.Failure ->
                    return ProviderRefreshResult.Failure(refreshed.error)
                is OAuthTokenClient.Result.Success -> {
                    val now = clock.instant()
                    session = session.copy(
                        accessToken = refreshed.tokens.accessToken,
                        refreshToken = refreshed.tokens.refreshToken ?: session.refreshToken,
                        tokenExpiresAtEpochSeconds = refreshed.tokens.expiresInSeconds?.let { now.epochSecond + it },
                    )
                    val jsonBytes = json.encodeToString(AntigravitySessionPayload.serializer(), session)
                        .encodeToByteArray()
                    val encrypted = payloadCipher.encrypt(jsonBytes)
                    sessionStore.save(
                        envelope.copy(
                            payloadCiphertext = encrypted.ciphertext,
                            payloadNonce = encrypted.nonce,
                            updatedAt = now.toString(),
                        ),
                    )
                }
            }
        }

        return when (val result = client.fetchQuota(session.accessToken)) {
            is AntigravityQuotaClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is AntigravityQuotaClient.Result.Success ->
                ProviderRefreshResult.Success(
                    AntigravityQuotaMapper.map(
                        dto = result.dto,
                        localAccountId = account.localAccountId,
                        providerAccountId = account.providerAccountId,
                        fetchedAt = clock.instant(),
                        source = trigger.toSnapshotSource(),
                    ),
                )
        }
    }

    private fun RefreshTrigger.toSnapshotSource(): QuotaSnapshotSource = when (this) {
        RefreshTrigger.AppOpen -> QuotaSnapshotSource.AppOpenRefresh
        RefreshTrigger.Manual -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Widget -> QuotaSnapshotSource.WidgetRefresh
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.OAuthWebView
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    companion object {
        val ANTIGRAVITY_PROVIDER_ID = ProviderId("antigravity")
        private const val EXPIRY_SKEW_SECONDS = 60L
    }
}
