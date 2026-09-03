package app.quotatrail.application

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.RefreshAttemptDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.presentation.settings.SettingsBackgroundRefreshStatus
import app.quotatrail.presentation.settings.SettingsBackgroundRefreshStatusReader

internal class SettingsBackgroundRefreshStatusRepository(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val refreshAttemptDao: RefreshAttemptDao,
) : SettingsBackgroundRefreshStatusReader {
    override suspend fun latestBackgroundRefreshStatus(): SettingsBackgroundRefreshStatus {
        val selection = currentAccountReader.currentAccountSelection()
            ?: return SettingsBackgroundRefreshStatus.NoCurrentAccount
        val account = providerAccountDao.getById(selection.localAccountId.value)
            ?.takeIf { it.providerId == selection.providerId.value }
            ?: return SettingsBackgroundRefreshStatus.NoCurrentAccount
        val attempt = refreshAttemptDao.getLatestForAccountByTrigger(
            providerId = account.providerId,
            localAccountId = account.localAccountId,
            trigger = PERIODIC_TRIGGER,
        ) ?: return SettingsBackgroundRefreshStatus.NoAttempts

        return when (attempt.status) {
            "success" -> SettingsBackgroundRefreshStatus.Success
            "failed" -> if (attempt.retryable == true && attempt.userActionRequired != true) {
                SettingsBackgroundRefreshStatus.Retrying
            } else {
                SettingsBackgroundRefreshStatus.Failed
            }
            "skipped" -> SettingsBackgroundRefreshStatus.Skipped
            "cancelled" -> SettingsBackgroundRefreshStatus.Cancelled
            else -> SettingsBackgroundRefreshStatus.Failed
        }
    }

    private companion object {
        const val PERIODIC_TRIGGER = "periodic"
    }
}
