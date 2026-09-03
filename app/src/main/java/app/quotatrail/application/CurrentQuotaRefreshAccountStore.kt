package app.quotatrail.application

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.model.AccountStatus
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.providers.ProviderRegistry

internal class CurrentQuotaRefreshAccountStore(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
) {
    private val enabledProviderIds = ProviderRegistry.all.mapTo(mutableSetOf()) { it.providerId }

    /**
     * The currently-selected account when it is eligible for a user-initiated refresh (Active or
     * NeedsReauth). Returning a NeedsReauth account lets manual pull-to-refresh on Home retry it; a
     * successful retry then clears the flag. Disabled/Deleted return null. Only the manual Home
     * refresh path uses this; background refresh enumerates [activeAccounts] instead.
     */
    suspend fun currentAccount(): ProviderAccount? {
        val selection = currentAccountReader.currentAccountSelection() ?: return null
        val account = providerAccountDao.getById(selection.localAccountId.value)?.toDomain() ?: return null
        return account.takeIf {
            it.providerId == selection.providerId && it.status.isManuallyRefreshable()
        }
    }

    /**
     * Every Active account for an enabled provider. QuotaTrail currently exposes Claude and Codex;
     * legacy accounts for disabled providers are ignored.
     */
    suspend fun activeAccounts(): List<ProviderAccount> =
        providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.providerId in enabledProviderIds && it.status == AccountStatus.Active }

    /**
     * Accounts a user-initiated refresh may attempt: Active plus NeedsReauth. Including NeedsReauth
     * lets a manual pull-to-refresh retry a flagged account; a successful retry then clears the flag
     * (see [app.quotatrail.sync.UsageSyncCoordinator]). Disabled/Deleted stay excluded.
     * Background (Periodic) refresh keeps using [activeAccounts] so it never re-hits failed accounts.
     */
    suspend fun manuallyRefreshableAccounts(): List<ProviderAccount> =
        providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.providerId in enabledProviderIds && it.status.isManuallyRefreshable() }
}

private fun AccountStatus.isManuallyRefreshable(): Boolean =
    this == AccountStatus.Active || this == AccountStatus.NeedsReauth
