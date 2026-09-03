package app.quotatrail.application

import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.sync.UsageSyncCoordinator
import app.quotatrail.presentation.home.HomeRefreshUseCase

internal class HomeUsageSyncCoordinatorUseCase(
    private val currentAccountStore: CurrentQuotaRefreshAccountStore,
    private val refreshCoordinator: UsageSyncCoordinator,
    private val currentQuotaStateLoader: CurrentUsageRepository,
) : HomeRefreshUseCase {
    // Manual pull-to-refresh only. Opening Home no longer triggers a network refresh (it just reloads
    // the persisted snapshot), so there is no separate app-open path here.
    override suspend fun refreshCurrentState(): CurrentQuotaState {
        val account = currentAccountStore.currentAccount()
        if (account != null) {
            refreshCoordinator.refresh(account = account, trigger = RefreshTrigger.Manual)
        }
        return currentQuotaStateLoader.loadCurrentState()
    }
}
