package app.quotatrail.domain.theme

import kotlinx.coroutines.flow.Flow

interface AppearancePreferenceStore {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
