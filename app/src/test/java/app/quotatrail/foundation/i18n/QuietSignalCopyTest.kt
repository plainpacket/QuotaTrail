package app.quotatrail.foundation.i18n

import app.quotatrail.R
import app.quotatrail.domain.settings.LanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuietSignalCopyTest {
    private val context by lazy {
        AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )
    }

    @Test
    fun `navigation and screen introductions use concise English copy`() {
        assertEquals("Usage", context.getString(R.string.tab_home))
        assertEquals("Accounts", context.getString(R.string.tab_account))
        assertEquals("Settings", context.getString(R.string.tab_settings))
        assertEquals("Connections, alerts, and local account controls.", context.getString(R.string.account_global_current_description))
        assertEquals("Choose how QuotaTrail updates and notifies you.", context.getString(R.string.settings_description))
    }

    @Test
    fun `quota copy describes remaining amount and renewal consistently`() {
        assertEquals("38% remaining", context.getString(R.string.home_quota_percent_format, 38))
        assertEquals("Renews Saturday at 9:00 PM", context.getString(R.string.home_reset_at_format, "Saturday at 9:00 PM"))
        assertEquals("Renewal unavailable", context.getString(R.string.home_reset_unavailable))
        assertEquals("Comfortable", context.getString(R.string.home_quota_status_normal))
        assertEquals("Running low", context.getString(R.string.home_quota_status_caution))
    }
}
