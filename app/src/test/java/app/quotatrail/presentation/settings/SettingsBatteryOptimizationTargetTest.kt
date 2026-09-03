package app.quotatrail.presentation.settings

import android.provider.Settings
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class SettingsBatteryOptimizationTargetTest {
    @Test
    fun `request exemption opens the system ignore-battery-optimization dialog for this package`() {
        val intent = SettingsBatteryOptimizationTarget.requestExemptionIntent("app.quotatrail")

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals(Uri.parse("package:app.quotatrail"), intent.data)
    }
}
