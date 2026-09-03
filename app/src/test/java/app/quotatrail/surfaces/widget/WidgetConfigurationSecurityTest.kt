package app.quotatrail.surfaces.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetConfigurationSecurityTest {
    private val expected = ComponentName(
        "app.quotatrail.safe",
        QuotaTrailWidgetReceiver::class.java.name,
    )

    @Test
    fun `configuration accepts only widget ids owned by this receiver`() {
        val own = AppWidgetProviderInfo().apply { provider = expected }
        val foreign = AppWidgetProviderInfo().apply {
            provider = ComponentName("evil.test", "evil.test.Widget")
        }

        assertTrue(isOwnWidgetProvider(own, expected))
        assertFalse(isOwnWidgetProvider(foreign, expected))
        assertFalse(isOwnWidgetProvider(null, expected))
    }
}
