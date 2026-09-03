package app.quotatrail.storage.repository

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.domain.account.AccountListResult
import app.quotatrail.domain.account.AccountListUseCase
import app.quotatrail.domain.model.ProviderId

class AccountListRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val currentAccountReader: CurrentAccountReader,
) : AccountListUseCase {
    override suspend fun loadAccounts(): AccountListResult {
        val rawAccounts = providerAccountDao.listAll().map { it.toDomain() }
        val currentSelection = currentAccountReader.currentAccountSelection()
            ?.takeIf { selection -> rawAccounts.any { it.localAccountId == selection.localAccountId } }

        val latestQuotaSnapshots = rawAccounts.mapNotNull { account ->
            quotaSnapshotDao.getLatestForAccount(
                providerId = account.providerId.value,
                localAccountId = account.localAccountId.value,
            )?.toDomain()?.let { account.localAccountId to it }
        }.toMap()

        // The refresh pipeline persists snapshots, not the account's last-refresh timestamp, so derive
        // it from the latest snapshot's `fetchedAt` when the account row hasn't recorded one. Mirrors
        // the COALESCE fallback in ProviderAccountDao.listByProvider so newly-added providers don't
        // show "No successful refresh yet" despite having fresh quota.
        val accounts = rawAccounts.map { account ->
            if (account.lastSuccessfulRefreshAt != null) {
                account
            } else {
                latestQuotaSnapshots[account.localAccountId]
                    ?.let { account.copy(lastSuccessfulRefreshAt = it.fetchedAt) }
                    ?: account
            }
        }

        return AccountListResult(
            accounts = accounts,
            currentAccountId = currentSelection?.localAccountId,
            latestQuotaSnapshots = latestQuotaSnapshots,
        )
    }
}
