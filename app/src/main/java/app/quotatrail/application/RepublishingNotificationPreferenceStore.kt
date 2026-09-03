package app.quotatrail.application

import app.quotatrail.domain.settings.NotificationPreferenceStore
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.sync.CurrentQuotaStatePublisher
import app.quotatrail.presentation.home.HomeCurrentQuotaStateLoader
import kotlinx.coroutines.CancellationException

internal class RepublishingNotificationPreferenceStore(
    private val delegate: NotificationPreferenceStore,
    private val currentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    private val currentQuotaStatePublisher: CurrentQuotaStatePublisher,
) : NotificationPreferenceStore {
    override suspend fun notificationPreferences(): NotificationPreferences =
        delegate.notificationPreferences()

    override suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
        delegate.updateNotificationPreferences(preferences)
        try {
            currentQuotaStatePublisher.publish(currentQuotaStateLoader.loadCurrentState())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Preference writes are durable; presentation refresh can recover on the next state update.
        }
    }
}
