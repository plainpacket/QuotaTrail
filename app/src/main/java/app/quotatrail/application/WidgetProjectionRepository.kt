package app.quotatrail.application

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.local.dao.RefreshAttemptDao
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.model.AccountStatus
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.quota.CurrentQuotaStateFactory
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.surfaces.widget.WidgetQuotaConfiguration
import app.quotatrail.surfaces.widget.WidgetQuotaState
import app.quotatrail.surfaces.widget.WidgetQuotaStateFactory
import app.quotatrail.surfaces.widget.WidgetQuotaStateLoader
import app.quotatrail.surfaces.widget.WidgetSlotProjection
import java.time.Clock

internal class WidgetProjectionRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val widgetQuotaStateFactory: WidgetQuotaStateFactory = WidgetQuotaStateFactory(),
    private val notificationPreferenceReader: NotificationPreferenceReader = DefaultWidgetNotificationPreferenceReader,
    private val clock: Clock,
) : WidgetQuotaStateLoader {
    override suspend fun loadWidgetQuotaState(configuration: WidgetQuotaConfiguration): WidgetQuotaState {
        val configuredSlots = configuration.slots.filter { it.isComplete }.take(4)
        if (configuredSlots.isEmpty()) {
            return widgetQuotaStateFactory.unconfigured(hasAccounts = hasSelectableAccount())
        }

        val projections = configuredSlots.mapNotNull { slot ->
            val account = providerAccountDao.getById(slot.localAccountId)
                ?.toDomain()
                ?.takeIf { it.providerId.value == slot.providerId }
                ?: return@mapNotNull null
            val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
                providerId = account.providerId.value,
                localAccountId = account.localAccountId.value,
            )?.toDomain()
            val latestAttempt = refreshAttemptDao.getLatestForAccount(
                providerId = account.providerId.value,
                localAccountId = account.localAccountId.value,
            )?.toDomain()
            WidgetSlotProjection(
                configuration = slot,
                state = currentQuotaStateFactory.create(
                    account = account,
                    latestSnapshot = latestSnapshot,
                    latestAttempt = latestAttempt,
                    now = clock.instant(),
                    primaryWindowId = QuotaWindowId(slot.windowId),
                ),
            )
        }
        return widgetQuotaStateFactory.createFromSlots(
            projections = projections,
            notificationPreferences = notificationPreferenceReader.notificationPreferences(),
            hasAccounts = hasSelectableAccount(),
        )
    }

    private suspend fun hasSelectableAccount(): Boolean =
        providerAccountDao.listAll().any { it.toDomain().status != AccountStatus.Deleted }
}

private object DefaultWidgetNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

