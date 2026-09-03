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
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class QuotaWindowCopyTest {
    @Test
    fun `english quota window labels use limit consistently`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        assertEquals("5-hour limit", context.getString(R.string.home_quota_window_five_hour))
        assertEquals("7-day limit", context.getString(R.string.home_quota_window_weekly))
        assertEquals("5-hour limit", context.getString(R.string.account_quota_five_hour_label))
        assertEquals("7-day limit", context.getString(R.string.account_quota_weekly_label))
        assertEquals("5-hour limit", context.getString(R.string.settings_primary_window_five_hour))
        assertEquals("7-day limit", context.getString(R.string.settings_primary_window_weekly))
    }

    @Test
    fun `simplified chinese preference falls back to English quota labels`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        assertEquals("5-hour limit", context.getString(R.string.home_quota_window_five_hour))
        assertEquals("7-day limit", context.getString(R.string.home_quota_window_weekly))
        assertEquals("5-hour limit", context.getString(R.string.account_quota_five_hour_label))
        assertEquals("7-day limit", context.getString(R.string.account_quota_weekly_label))
        assertEquals("5-hour limit", context.getString(R.string.settings_primary_window_five_hour))
        assertEquals("7-day limit", context.getString(R.string.settings_primary_window_weekly))
    }
}
