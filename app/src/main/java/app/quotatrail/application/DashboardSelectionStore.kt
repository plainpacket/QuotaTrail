package app.quotatrail.application

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.storage.preferences.CurrentAccountStore
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.account.CurrentAccountStateRepublisher
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.presentation.home.HomeAccountSelectionUseCase

/** Persists the visible Home page without turning a horizontal swipe into a network refresh. */
internal class DashboardSelectionStore(
    private val providerAccountDao: ProviderAccountDao,
    private val currentAccountStore: CurrentAccountStore,
    private val currentAccountStateRepublisher: CurrentAccountStateRepublisher,
) : HomeAccountSelectionUseCase {
    override suspend fun selectAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean {
        val account = providerAccountDao.getById(localAccountId.value)
            ?.toDomain()
            ?.takeIf { it.providerId == providerId }
            ?: return false

        currentAccountStore.updateCurrentAccountSelection(
            CurrentAccountSelection(
                providerId = account.providerId,
                localAccountId = account.localAccountId,
            ),
        )
        currentAccountStateRepublisher.republishCurrentAccountState(account)
        return true
    }
}
