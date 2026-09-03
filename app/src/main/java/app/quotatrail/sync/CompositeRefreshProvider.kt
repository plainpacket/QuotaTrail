package app.quotatrail.sync

import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.domain.refresh.RefreshTrigger

class CompositeRefreshProvider(
    private val providers: Map<ProviderId, RefreshProvider>,
) : RefreshProvider {
    override suspend fun refresh(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult =
        providers[account.providerId]
            ?.refresh(account, trigger)
            ?: ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "composite_refresh_no_provider_for_${account.providerId.value}",
                ),
            )
}
