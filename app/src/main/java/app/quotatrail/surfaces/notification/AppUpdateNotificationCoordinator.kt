package app.quotatrail.surfaces.notification

import android.app.PendingIntent
import app.quotatrail.R
import app.quotatrail.domain.update.AppUpdateInfo

class AppUpdateNotificationCoordinator {
    fun updateAvailable(update: AppUpdateInfo): NotificationRequest =
        NotificationRequest(
            notificationId = NotificationCoordinator.APP_UPDATE_NOTIFICATION_ID,
            channelId = NotificationChannels.APP_UPDATES_CHANNEL_ID,
            title = NotificationText(R.string.notification_app_update_title),
            body = NotificationText(
                resourceId = R.string.notification_app_update_body_format,
                formatArgs = listOf(update.versionName),
            ),
            pendingIntent = NotificationPendingIntentMetadata(
                requestCode = APP_UPDATE_REQUEST_CODE,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                destination = NotificationDestination.AppUpdate,
            ),
            ongoing = false,
        )

    private companion object {
        // Distinct from other notification request codes (11/21/31 NotificationCoordinator, 41/42 DeviceCodeLogin) so PendingIntents don't collide.
        const val APP_UPDATE_REQUEST_CODE = 51
    }
}
