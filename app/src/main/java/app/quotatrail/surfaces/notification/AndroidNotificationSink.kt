package app.quotatrail.surfaces.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.quotatrail.QuotaTrailActivity
import app.quotatrail.R
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.presentation.navigation.QuotaTrailLaunchDestination
import app.quotatrail.presentation.navigation.QuotaTrailDestination

class AndroidNotificationSink(
    context: Context,
    private val renderer: AndroidNotificationRenderer = AndroidNotificationRenderer(context),
) : NotificationSink {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun post(request: NotificationRequest) {
        NotificationChannels.createAll(appContext)
        NotificationManagerCompat.from(appContext).notify(
            request.notificationId,
            renderer.render(request),
        )
    }

    override fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(appContext).cancel(notificationId)
    }
}

class AndroidNotificationRequestOptionsReader(
    context: Context,
    private val notificationPreferenceReader: NotificationPreferenceReader,
) : NotificationRequestOptionsReader {
    private val appContext = context.applicationContext

    override suspend fun currentOptions(): NotificationRequestOptions {
        val preferences = notificationPreferenceReader.notificationPreferences()
        return NotificationRequestOptions(
            notificationPermissionAvailable = hasNotificationPermission(),
            statusNotificationEnabled = preferences.statusNotificationEnabled,
            quotaAlertsEnabled = preferences.quotaAlertsEnabled,
            accountErrorsEnabled = preferences.accountErrorsEnabled,
        )
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}

class NotificationPreferenceAlertThresholdsReader(
    private val notificationPreferenceReader: NotificationPreferenceReader,
) : AlertThresholdsReader {
    override suspend fun currentThresholds(): AlertThresholds {
        val preferences = notificationPreferenceReader.notificationPreferences()
        return AlertThresholds(
            caution = preferences.cautionThreshold,
            warning = preferences.warningThreshold,
            limit = preferences.limitThreshold,
            balanceCaution = preferences.balanceCautionThreshold,
            balanceWarning = preferences.balanceWarningThreshold,
        )
    }
}

class AndroidNotificationRenderer(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun render(request: NotificationRequest): Notification {
        val title = request.title.resolve(appContext)
        val body = request.body.resolve(appContext)

        val builder = NotificationCompat.Builder(appContext, request.channelId)
            .setSmallIcon(R.drawable.ic_notification_status)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPendingIntent(request.pendingIntent))
            .setOngoing(request.ongoing)
            .setOnlyAlertOnce(request.ongoing)
            .setAutoCancel(!request.ongoing)
            .setShowWhen(!request.ongoing)
            .setColor(ContextCompat.getColor(appContext, R.color.quotatrail_accent))
            .setPriority(request.priority())

        applyExpandedStyle(builder, title, body, request.expandedLines)

        configureLockscreenPrivacy(builder, request, body)

        request.timeoutAfterMillis?.let(builder::setTimeoutAfter)
        request.actions.forEach { action ->
            builder.addAction(
                when (action.intent.action) {
                    NotificationActionType.RefreshAllQuota -> R.drawable.ic_action_refresh
                    NotificationActionType.CopyDeviceCode -> R.drawable.ic_notification_status
                },
                action.title.resolve(appContext),
                actionPendingIntent(action.intent),
            )
        }

        return builder
            .build()
    }

    private fun configureLockscreenPrivacy(
        builder: NotificationCompat.Builder,
        request: NotificationRequest,
        resolvedBody: String,
    ) {
        if (request.channelId != NotificationChannels.STATUS_CHANNEL_ID) {
            // Login codes, account errors, alerts, and update details never belong on the lock screen.
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
            return
        }

        // A single-account private title may contain an alias, so it gets the generic public title.
        // Aggregate requests provide an explicitly safe provider/quota title with no aliases.
        val publicTitle = request.publicTitle?.resolve(appContext)
            ?: appContext.getString(R.string.notification_status_title)
        val publicVersion = NotificationCompat.Builder(appContext, request.channelId)
            .setSmallIcon(R.drawable.ic_notification_status)
            .setContentTitle(publicTitle)
            .setContentText(resolvedBody)
            .setContentIntent(contentPendingIntent(request.pendingIntent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(ContextCompat.getColor(appContext, R.color.quotatrail_accent))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        applyExpandedStyle(publicVersion, publicTitle, resolvedBody, request.expandedLines)

        builder
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion.build())
    }

    private fun applyExpandedStyle(
        builder: NotificationCompat.Builder,
        title: String,
        body: String,
        lines: List<NotificationText>,
    ) {
        if (lines.isEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            return
        }
        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { inboxStyle.addLine(it.resolve(appContext)) }
        builder.setStyle(inboxStyle)
    }

    private fun contentPendingIntent(metadata: NotificationPendingIntentMetadata): PendingIntent {
        val intent = when (metadata.destination) {
            NotificationDestination.Home,
            NotificationDestination.AddAccount,
            NotificationDestination.AppUpdate,
            -> {
                val launchDestination = when (metadata.destination) {
                    NotificationDestination.Home -> QuotaTrailLaunchDestination.Home
                    NotificationDestination.AddAccount -> QuotaTrailLaunchDestination.AddAccount
                    NotificationDestination.AppUpdate -> QuotaTrailLaunchDestination.SettingsUpdate
                    NotificationDestination.ExternalUrl -> QuotaTrailLaunchDestination.Home
                }
                Intent(appContext, QuotaTrailActivity::class.java).apply {
                    putExtra(QuotaTrailDestination.EXTRA_LAUNCH_DESTINATION, launchDestination.value)
                }
            }
            NotificationDestination.ExternalUrl -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse(metadata.externalUrl.orEmpty()),
            ).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        }
        return PendingIntent.getActivity(
            appContext,
            metadata.requestCode,
            intent,
            metadata.flags,
        )
    }

    private fun actionPendingIntent(metadata: NotificationActionIntentMetadata): PendingIntent {
        val intent = when (metadata.action) {
            NotificationActionType.CopyDeviceCode -> Intent(
                appContext,
                DeviceCodeLoginCopyCodeReceiver::class.java,
            ).apply {
                action = DeviceCodeLoginCopyCodeReceiver.ACTION_COPY_DEVICE_CODE
                putExtra(DeviceCodeLoginCopyCodeReceiver.EXTRA_ATTEMPT_ID, metadata.attemptId)
            }
            NotificationActionType.RefreshAllQuota -> Intent(
                appContext,
                StatusNotificationRefreshReceiver::class.java,
            ).apply {
                action = StatusNotificationRefreshReceiver.ACTION_REFRESH_ALL_QUOTA
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            metadata.requestCode,
            intent,
            metadata.flags,
        )
    }
}

// Format args may themselves be NotificationText (e.g. status body = "<quota> · <status>"), so
// resolve them recursively before handing plain strings to getString.
private fun NotificationText.resolve(context: Context): String =
    context.getString(
        resourceId,
        *formatArgs.map { arg -> if (arg is NotificationText) arg.resolve(context) else arg }.toTypedArray(),
    )

private fun NotificationRequest.priority(): Int =
    if (channelId == NotificationChannels.STATUS_CHANNEL_ID) {
        NotificationCompat.PRIORITY_LOW
    } else {
        NotificationCompat.PRIORITY_DEFAULT
    }
