package app.quotatrail.surfaces.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.providers.ProviderRegistry
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetGlanceCompositionTest {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun `all widget sizes compose and measure with last updated timestamp`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val state = WidgetQuotaState(
            status = WidgetQuotaStatus.Fresh,
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "acc-1",
            accountName = "Private account alias",
            tone = WidgetQuotaTone.Success,
            clickTarget = WidgetClickTarget.Home,
            fields = listOf(
                WidgetField(
                    windowId = "five_hour",
                    isBalance = false,
                    percent = 87,
                    balanceAmount = null,
                    balanceCurrency = null,
                    resetAt = Instant.parse("2026-08-24T05:00:00Z"),
                    tone = WidgetQuotaTone.Success,
                ),
            ),
            lastUpdatedAt = Instant.parse("2026-08-24T00:15:00Z"),
            providerIconRes = ProviderRegistry.iconFor(ProviderId("codex")),
        )
        val specs = listOf(
            Triple(WidgetLayoutVariant.ThreeByOne, 252, 110),
            Triple(WidgetLayoutVariant.FourByOne, 340, 110),
            Triple(WidgetLayoutVariant.ThreeByTwo, 252, 210),
            Triple(WidgetLayoutVariant.FourByTwo, 340, 210),
        )

        specs.forEach { (variant, widthDp, heightDp) ->
            val size = DpSize(widthDp.dp, heightDp.dp)
            val remoteViews = GlanceRemoteViews().compose(context, size) {
                QuotaTrailWidgetContent(context, state, size, variant)
            }.remoteViews
            val parent = FrameLayout(context)
            val view = remoteViews.apply(context, parent)
            val widthPx = (widthDp * density).toInt()
            val heightPx = (heightDp * density).toInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, widthPx, heightPx)

            assertEquals(widthPx, view.measuredWidth)
            assertEquals(heightPx, view.measuredHeight)
            val refreshControl = view.findByContentDescription("Refresh widget")
            assertNotNull("$variant should expose a refresh control", refreshControl)
            assertTrue(
                "$variant refresh control should be clickable",
                refreshControl!!.isClickable || (refreshControl.parent as? View)?.isClickable == true,
            )
        }
    }

    private fun View.findByContentDescription(description: String): View? {
        if (contentDescription?.toString() == description) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findByContentDescription(description)?.let { return it }
        }
        return null
    }
}
