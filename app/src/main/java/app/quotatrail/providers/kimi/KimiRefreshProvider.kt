package app.quotatrail.providers.kimi

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.kimi.mapper.KimiQuotaMapper
import app.quotatrail.providers.kimi.network.KimiQuotaClient
import app.quotatrail.providers.kimi.session.KimiSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock

class KimiRefreshProvider(
    private val client: KimiQuotaClient,
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
                    diagnosticsDigest = "kimi_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<KimiSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "kimi_session_decode_failed",
                ),
            )
        }

        // Pre-detect JWT expiry
        session.jwtExpiryEpochSeconds?.let { expiry ->
            val nowEpoch = clock.instant().epochSecond
            if (expiry <= nowEpoch) {
                return ProviderRefreshResult.Failure(
                    QuotaError.AuthRequired(
                        httpStatus = null,
                        diagnosticsDigest = "kimi_jwt_expired",
                    ),
                )
            }
        }

        return when (val result = client.fetchQuota(session.cookieValue)) {
            is KimiQuotaClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is KimiQuotaClient.Result.Success ->
                ProviderRefreshResult.Success(
                    KimiQuotaMapper.map(
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
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.CookieAuth
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    companion object {
        val KIMI_PROVIDER_ID = ProviderId("kimi")
    }
}
