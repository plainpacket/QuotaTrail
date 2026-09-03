package app.quotatrail.surfaces.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.sync.CurrentQuotaStatePublisher

class WidgetQuotaStateUpdater(
    context: Context,
    private val factory: WidgetQuotaStateFactory = WidgetQuotaStateFactory(),
    private val widget: QuotaTrailWidget = QuotaTrailWidget(),
    private val notificationPreferenceReader: NotificationPreferenceReader =
        DefaultWidgetNotificationPreferenceReader,
    private val widgetQuotaStateLoader: WidgetQuotaStateLoader? = null,
) : CurrentQuotaStatePublisher {
    private val appContext = context.applicationContext

    override suspend fun publish(state: CurrentQuotaState) {
        val notificationPreferences = notificationPreferenceReader.notificationPreferences()
        val manager = GlanceAppWidgetManager(appContext)
        manager.getGlanceIds(QuotaTrailWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(appContext, glanceId) { preferences ->
                val configuration = preferences.toWidgetQuotaConfiguration()
                val configuredWidgetState = widgetQuotaStateLoader?.loadWidgetQuotaState(configuration)
                    ?: factory.createFromSlots(
                        projections = configuration.slots
                            .filter { slot ->
                                slot.providerId == state.account?.providerId?.value &&
                                    slot.localAccountId == state.account.localAccountId.value
                            }
                            .map { slot -> WidgetSlotProjection(slot, state) },
                        notificationPreferences = notificationPreferences,
                        hasAccounts = state.account != null,
                    )
                preferences.writeWidgetQuotaState(configuredWidgetState)
            }
        }
        widget.updateAll(appContext)
    }
}

private object DefaultWidgetNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}
