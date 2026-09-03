package app.quotatrail.surfaces.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import app.quotatrail.domain.account.DeletedAccountStateCleanup
import app.quotatrail.domain.account.DeletedAccountStateCleaner
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId

class WidgetDeletedAccountStateCleaner(
    context: Context,
    private val widget: QuotaTrailWidget = QuotaTrailWidget(),
) : DeletedAccountStateCleaner {
    private val appContext = context.applicationContext

    override suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup {
        val clearedStates = mutableListOf<WidgetStateSnapshot>()
        val manager = GlanceAppWidgetManager(appContext)
        manager.getGlanceIds(QuotaTrailWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(appContext, glanceId) { preferences ->
                val previousState = preferences.toWidgetQuotaState()
                val changed = preferences.clearWidgetQuotaStateIfAccountMatches(
                    providerId = providerId.value,
                    localAccountId = localAccountId.value,
                )
                if (changed) {
                    clearedStates += WidgetStateSnapshot(glanceId, previousState)
                }
            }
        }
        if (clearedStates.isNotEmpty()) {
            widget.updateAll(appContext)
        }
        return WidgetDeletedAccountStateCleanup(
            appContext = appContext,
            snapshots = clearedStates,
            widget = widget,
        )
    }
}

private data class WidgetStateSnapshot(
    val glanceId: GlanceId,
    val state: WidgetQuotaState,
)

private class WidgetDeletedAccountStateCleanup(
    private val appContext: Context,
    private val snapshots: List<WidgetStateSnapshot>,
    private val widget: QuotaTrailWidget,
) : DeletedAccountStateCleanup {
    override suspend fun restore() {
        snapshots.forEach { snapshot ->
            updateAppWidgetState(appContext, snapshot.glanceId) { preferences ->
                preferences.writeWidgetQuotaState(snapshot.state)
            }
        }
        if (snapshots.isNotEmpty()) {
            widget.updateAll(appContext)
        }
    }
}
