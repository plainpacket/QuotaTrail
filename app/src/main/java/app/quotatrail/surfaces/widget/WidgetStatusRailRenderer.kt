package app.quotatrail.surfaces.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt

/** A crisp semantic rail for RemoteViews, matching the in-app Field Instrument language. */
internal object WidgetStatusRailRenderer {
    fun render(
        context: Context,
        tone: WidgetQuotaTone,
        widthDp: Float = DEFAULT_WIDTH_DP,
        heightDp: Float = DEFAULT_HEIGHT_DP,
    ): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val width = max(1, (widthDp * density).roundToInt())
        val height = max(1, (heightDp * density).roundToInt())
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val inset = 1.5f * density
            val railWidth = 4f * density
            val left = (width - railWidth) / 2f
            canvas.drawRoundRect(
                RectF(left, inset, left + railWidth, height - inset),
                2f * density,
                2f * density,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone.statusAccentArgb() },
            )
        }
    }

    private const val DEFAULT_WIDTH_DP = 9f
    private const val DEFAULT_HEIGHT_DP = 44f
}
