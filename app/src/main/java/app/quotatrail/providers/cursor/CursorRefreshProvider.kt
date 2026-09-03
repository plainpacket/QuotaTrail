package app.quotatrail.providers.cursor

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.providers.cursor.mapper.CursorUsageMapper
import app.quotatrail.providers.cursor.network.CursorUsageClient
import app.quotatrail.providers.cursor.session.CursorSessionPayload
import app.quotatrail.sync.ProviderRefreshResult
import app.quotatrail.sync.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock

class CursorRefreshProvider(
    private val client: CursorUsageClient,
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
                    diagnosticsDigest = "cursor_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<CursorSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "cursor_session_decode_failed",
                ),
            )
        }

        return when (val result = client.fetchUsage(session.cookieValue)) {
            is CursorUsageClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is CursorUsageClient.Result.Success ->
                ProviderRefreshResult.Success(
                    CursorUsageMapper.map(
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
        val CURSOR_PROVIDER_ID = ProviderId("cursor")
    }
}
