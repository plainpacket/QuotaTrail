package app.quotatrail.providers.minimax

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.minimax.mapper.MiniMaxUsageMapper
import app.quotatrail.providers.minimax.network.MiniMaxUsageClient
import app.quotatrail.providers.minimax.session.MiniMaxSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock

class MiniMaxRefreshProvider(
    private val client: MiniMaxUsageClient,
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
                    diagnosticsDigest = "minimax_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<MiniMaxSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "minimax_session_decode_failed",
                ),
            )
        }

        val baseUrl = session.apiBaseUrl ?: MiniMaxUsageClient.DEFAULT_BASE_URL
        return when (val result = client.fetchUsage(session.apiKey, baseUrl)) {
            is MiniMaxUsageClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is MiniMaxUsageClient.Result.Success ->
                ProviderRefreshResult.Success(
                    MiniMaxUsageMapper.map(
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
        val MINIMAX_PROVIDER_ID = ProviderId("minimax")
    }
}
