package app.quotatrail.providers.zai

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.zai.mapper.ZaiQuotaMapper
import app.quotatrail.providers.zai.network.ZaiQuotaClient
import app.quotatrail.providers.zai.session.ZaiSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock

class ZaiRefreshProvider(
    private val client: ZaiQuotaClient,
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
                    diagnosticsDigest = "zai_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<ZaiSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "zai_session_decode_failed",
                ),
            )
        }

        val baseUrl = session.apiBaseUrl ?: ZaiQuotaClient.DEFAULT_BASE_URL
        return when (val result = client.fetchQuota(session.apiKey, baseUrl)) {
            is ZaiQuotaClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is ZaiQuotaClient.Result.Success ->
                ProviderRefreshResult.Success(
                    ZaiQuotaMapper.map(
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
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.ApiKeyImport
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    companion object {
        val ZAI_PROVIDER_ID = ProviderId("zai")
    }
}
