package app.quotatrail.surfaces.notification

import app.quotatrail.domain.update.AppUpdateInfo
import app.quotatrail.domain.update.AppUpdateNotifier

class AndroidAppUpdateNotifier(
    private val notificationSink: NotificationSink,
    private val orchestrator: AppUpdateNotificationCoordinator = AppUpdateNotificationCoordinator(),
) : AppUpdateNotifier {
    override fun notifyUpdateAvailable(update: AppUpdateInfo) {
        notificationSink.post(orchestrator.updateAvailable(update))
    }
}
