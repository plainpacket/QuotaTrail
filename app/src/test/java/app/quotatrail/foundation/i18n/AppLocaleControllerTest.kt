package app.quotatrail.foundation.i18n

import android.app.LocaleManager
import android.os.LocaleList
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
class AppLocaleControllerTest {
    @Test
    fun `localized context resolves English even for simplified chinese preference`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        assertEquals("Settings", context.getString(R.string.settings_title))
    }

    @Test
    fun `localized context resolves english resources`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        assertEquals("Settings", context.getString(R.string.settings_title))
    }

    @Test
    fun `localized context for system preference is English`() {
        val overriddenContext = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        val systemContext = AppLocaleController.localizedContext(
            context = overriddenContext,
            preference = LanguagePreference.System,
        )

        assertEquals("en", overriddenContext.resources.configuration.locales[0].language)
        assertEquals("en", systemContext.resources.configuration.locales[0].language)
    }

    @Test
    fun `system language resolution falls back to english when unsupported`() {
        assertEquals(
            "en",
            AppLocaleController.supportedSystemLocaleList(LocaleList.forLanguageTags("ja-JP"))
                .toLanguageTags(),
        )
    }

    @Test
    fun `system language resolution forces English for simplified chinese`() {
        assertEquals(
            "en",
            AppLocaleController.supportedSystemLocaleList(LocaleList.forLanguageTags("zh-CN,en"))
                .toLanguageTags(),
        )
    }

    @Test
    fun `ensure English clears persisted platform locale override`() {
        val context = RuntimeEnvironment.getApplication()
        val localeManager = context.getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = LocaleList.forLanguageTags("en")

        AppLocaleController.ensureEnglishLocale(context)

        assertEquals("", localeManager.applicationLocales.toLanguageTags())
        assertEquals("en", context.resources.configuration.locales[0].language)
    }
}
