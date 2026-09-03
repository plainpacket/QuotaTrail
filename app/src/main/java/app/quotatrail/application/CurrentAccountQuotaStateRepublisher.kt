package app.quotatrail.application

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.local.dao.RefreshAttemptDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.account.CurrentAccountStateRepublisher
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.quota.CurrentQuotaStateFactory
import app.quotatrail.domain.settings.DefaultPrimaryQuotaWindowPreferenceReader
import app.quotatrail.domain.settings.PrimaryQuotaWindowPreferenceReader
import app.quotatrail.sync.CurrentQuotaStatePublisher
import java.time.Clock

internal class CurrentAccountQuotaStateRepublisher(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStatePublisher: CurrentQuotaStatePublisher,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val primaryQuotaWindowPreferenceReader: PrimaryQuotaWindowPreferenceReader =
        DefaultPrimaryQuotaWindowPreferenceReader,
    private val clock: Clock,
) : CurrentAccountStateRepublisher {
    override suspend fun republishCurrentAccountState(account: ProviderAccount) {
        val currentSelection = currentAccountReader.currentAccountSelection() ?: return
        if (currentSelection.providerId != account.providerId ||
            currentSelection.localAccountId != account.localAccountId
        ) {
            return
        }

        val persistedAccount = providerAccountDao.getById(account.localAccountId.value)
            ?.toDomain()
            ?.takeIf { it.providerId == account.providerId }
            ?: return
        val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = persistedAccount.providerId.value,
            localAccountId = persistedAccount.localAccountId.value,
        )?.toDomain()
        val latestAttempt = refreshAttemptDao.getLatestForAccount(
            providerId = persistedAccount.providerId.value,
            localAccountId = persistedAccount.localAccountId.value,
        )?.toDomain()

        currentQuotaStatePublisher.publish(
            currentQuotaStateFactory.create(
                account = persistedAccount,
                latestSnapshot = latestSnapshot,
                latestAttempt = latestAttempt,
                now = clock.instant(),
                primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId(),
            ),
        )
    }
}
