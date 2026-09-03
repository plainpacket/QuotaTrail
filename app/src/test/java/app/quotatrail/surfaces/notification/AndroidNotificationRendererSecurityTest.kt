package app.quotatrail.surfaces.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import androidx.core.content.ContextCompat
import app.quotatrail.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidNotificationRendererSecurityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val renderer = AndroidNotificationRenderer(context)

    @Test
    fun `status notification exposes quota but not account identity on lock screen`() {
        val request = request(
            channelId = NotificationChannels.STATUS_CHANNEL_ID,
            title = NotificationText(
                R.string.notification_status_title_identity_format,
                listOf("Claude", "Private account"),
            ),
            body = NotificationText(
                R.string.notification_status_body_with_quota_format,
                listOf(
                    NotificationText(R.string.notification_status_title_percent_format, listOf(61)),
                    NotificationText(R.string.notification_status_body_fresh),
                ),
            ),
        )

        val notification = renderer.render(request)
        val publicVersion = notification.publicVersion

        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(publicVersion)
        assertEquals(context.getString(R.string.notification_status_title), publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("61% left · Up to date", publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("Private account"))
    }

    @Test
    fun `non-status notifications are secret on the lock screen`() {
        val notification = renderer.render(
            request(
                channelId = NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID,
                title = NotificationText(R.string.notification_account_error_title),
                body = NotificationText(R.string.notification_status_body_auth_required),
            ),
        )

        assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
    }

    @Test
    fun `aggregate status notification exposes both safe provider summaries on lock screen`() {
        val safePublicTitle = NotificationText(
            R.string.notification_status_two_accounts_title_format,
            listOf(
                NotificationText(
                    R.string.notification_status_provider_quota_format,
                    listOf("Claude", NotificationText(R.string.notification_status_title_percent_format, listOf(0))),
                ),
                NotificationText(
                    R.string.notification_status_provider_quota_format,
                    listOf("Codex", NotificationText(R.string.notification_status_title_percent_format, listOf(38))),
                ),
            ),
        )
        val notification = renderer.render(
            request(
                channelId = NotificationChannels.STATUS_CHANNEL_ID,
                title = safePublicTitle,
                publicTitle = safePublicTitle,
                body = NotificationText(
                    R.string.notification_status_two_accounts_body_format,
                    listOf("Claude · 0% left", "Codex · 38% left"),
                ),
            ),
        )

        val publicTitle = notification.publicVersion.extras
            .getCharSequence(Notification.EXTRA_TITLE)
            .toString()
        assertEquals("Claude 0% left · Codex 38% left", publicTitle)
        assertFalse(publicTitle.contains("Private account"))
    }

    @Test
    fun `refresh all notification action targets private refresh receiver`() {
        val notification = renderer.render(
            request(
                channelId = NotificationChannels.STATUS_CHANNEL_ID,
                title = NotificationText(R.string.notification_status_title),
                body = NotificationText(R.string.notification_status_body_fresh),
                actions = listOf(
                    NotificationAction(
                        title = NotificationText(R.string.notification_status_action_refresh_all),
                        intent = NotificationActionIntentMetadata(
                            requestCode = 12,
                            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            action = NotificationActionType.RefreshAllQuota,
                        ),
                    ),
                ),
            ),
        )

        val action = notification.actions.single()
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals("Refresh all", action.title.toString())
        assertEquals(StatusNotificationRefreshReceiver::class.java.name, intent.component?.className)
        assertEquals(StatusNotificationRefreshReceiver.ACTION_REFRESH_ALL_QUOTA, intent.action)
    }

    @Test
    fun `status refresh receiver is not exported`() {
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, StatusNotificationRefreshReceiver::class.java),
            0,
        )

        assertFalse(receiverInfo.exported)
    }

    @Test
    fun `status notification uses a quiet colored inbox layout`() {
        val notification = renderer.render(
            request(
                channelId = NotificationChannels.STATUS_CHANNEL_ID,
                title = NotificationText(R.string.notification_status_title),
                body = NotificationText(R.string.notification_status_body_fresh),
                expandedLines = listOf(
                    NotificationText(
                        R.string.notification_status_window_percent_format,
                        listOf("5h", 75, "05/23, 5:00 PM"),
                    ),
                    NotificationText(
                        R.string.notification_status_window_percent_format,
                        listOf("7-day", 60, "05/29, 12:00 AM"),
                    ),
                ),
            ),
        )

        val lines = notification.extras
            .getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map(CharSequence::toString)
        assertEquals(
            listOf(
                "5h 75% · Renews 05/23, 5:00 PM",
                "7-day 60% · Renews 05/29, 12:00 AM",
            ),
            lines,
        )
        assertEquals(ContextCompat.getColor(context, R.color.quotatrail_accent), notification.color)
    }

    private fun request(
        channelId: String,
        title: NotificationText,
        publicTitle: NotificationText? = null,
        body: NotificationText,
        actions: List<NotificationAction> = emptyList(),
        expandedLines: List<NotificationText> = emptyList(),
    ) = NotificationRequest(
        notificationId = 99,
        channelId = channelId,
        title = title,
        publicTitle = publicTitle,
        body = body,
        pendingIntent = NotificationPendingIntentMetadata(
            requestCode = 99,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
        ongoing = channelId == NotificationChannels.STATUS_CHANNEL_ID,
        actions = actions,
        expandedLines = expandedLines,
    )
}
