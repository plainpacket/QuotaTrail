package app.quotatrail.application

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.quotatrail.surfaces.widget.QuotaTrailWidget

fun interface WidgetCountReader {
    suspend fun configuredWidgetCount(): Int?
}

internal class GlanceWidgetCountReader(
    context: Context,
) : WidgetCountReader {
    private val appContext = context.applicationContext

    override suspend fun configuredWidgetCount(): Int? =
        runCatching {
            GlanceAppWidgetManager(appContext).getGlanceIds(QuotaTrailWidget::class.java).size
        }.getOrNull()
}
