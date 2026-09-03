package app.quotatrail.providers.deepseek

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.deepseek.mapper.DeepSeekBalanceMapper
import app.quotatrail.providers.deepseek.network.DeepSeekBalanceClient
import app.quotatrail.providers.deepseek.session.DeepSeekSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant

class DeepSeekRefreshProvider(
    private val client: DeepSeekBalanceClient,
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
                    diagnosticsDigest = "deepseek_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<DeepSeekSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "deepseek_session_decode_failed",
                ),
            )
        }

        return when (val result = client.fetchBalance(session.apiKey)) {
            is DeepSeekBalanceClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is DeepSeekBalanceClient.Result.Success ->
                ProviderRefreshResult.Success(
                    DeepSeekBalanceMapper.map(
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
        val DEEPSEEK_PROVIDER_ID = ProviderId("deepseek")
    }
}
