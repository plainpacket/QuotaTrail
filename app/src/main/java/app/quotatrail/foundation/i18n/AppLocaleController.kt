package app.quotatrail.foundation.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import app.quotatrail.domain.settings.LanguagePreference
import java.util.Locale

object AppLocaleController {
    fun ensureEnglishLocale(context: Context) {
        clearPlatformLocaleOverride(context)
        updateProcessResources(context, englishLocaleList())
    }

    fun localizedContext(context: Context, @Suppress("UNUSED_PARAMETER") preference: LanguagePreference): Context {
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocales(englishLocaleList())
            },
        )
    }

    internal fun supportedSystemLocaleList(
        @Suppress("UNUSED_PARAMETER") systemLocales: LocaleList = LocaleList.getDefault(),
    ): LocaleList = englishLocaleList()

    @Suppress("DEPRECATION")
    private fun updateProcessResources(context: Context, localeList: LocaleList) {
        val primaryLocale = localeList[0] ?: return
        Locale.setDefault(primaryLocale)
        context.resources.updateConfiguration(
            Configuration(context.resources.configuration).apply {
                setLocales(localeList)
            },
            context.resources.displayMetrics,
        )
    }

    private fun clearPlatformLocaleOverride(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.getEmptyLocaleList()
    }

    private fun englishLocaleList(): LocaleList = LocaleList(Locale.ENGLISH)
}
