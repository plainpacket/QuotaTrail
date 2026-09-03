package app.quotatrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quotatrail.domain.theme.resolveDarkAppearance
import app.quotatrail.presentation.navigation.QuotaTrailRouter
import app.quotatrail.presentation.navigation.QuotaTrailDestination
import app.quotatrail.presentation.theme.QuotaTrailFontScheme
import app.quotatrail.presentation.theme.QuotaTrailTheme

class QuotaTrailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val codexMeterApp = application as QuotaTrailApplication
        val launchDestinationValue = intent?.getStringExtra(QuotaTrailDestination.EXTRA_LAUNCH_DESTINATION)
        val startDestination = QuotaTrailDestination.startRouteForLaunchDestination(launchDestinationValue)
        setContent {
            val themeMode by codexMeterApp.appearancePreferenceStore.themeMode
                .collectAsStateWithLifecycle(initialValue = codexMeterApp.initialThemeMode)
            val darkAppearance = resolveDarkAppearance(themeMode, isSystemInDarkTheme())

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkAppearance
                    controller.isAppearanceLightNavigationBars = !darkAppearance
                }
            }

            QuotaTrailTheme(themeMode = themeMode, fontScheme = QuotaTrailFontScheme.GeistHybrid) {
                QuotaTrailRouter(
                    startDestination = startDestination,
                    launchDestinationValue = launchDestinationValue,
                    updatePreferenceStore = codexMeterApp.updatePreferenceStore,
                    deviceCodeLoginController = codexMeterApp.deviceCodeLoginController,
                    deviceCodeLoginNotifier = codexMeterApp.deviceCodeLoginNotifier,
                    accountListUseCase = codexMeterApp.accountListUseCase,
                    accountDeleteUseCase = codexMeterApp.accountDeleteUseCase,
                    accountSwitchUseCase = codexMeterApp.accountSwitchUseCase,
                    accountRenameUseCase = codexMeterApp.accountRenameUseCase,
                    accountQuotaAlertEvaluationRequester = codexMeterApp.accountQuotaAlertEvaluationRequester,
                    accountRefreshAllUseCase = codexMeterApp.accountRefreshAllUseCase,
                    homeCurrentQuotaStateLoader = codexMeterApp.homeCurrentQuotaStateLoader,
                    homeAccountQuotaStatesLoader = codexMeterApp.homeAccountQuotaStatesLoader,
                    homeAccountSelectionUseCase = codexMeterApp.homeAccountSelectionUseCase,
                    homeRefreshUseCase = codexMeterApp.homeRefreshUseCase,
                    homeTrendHistoryLoader = codexMeterApp.homeTrendHistoryLoader,
                    retentionPreferenceStore = codexMeterApp.retentionPreferenceStore,
                    notificationPreferenceStore = codexMeterApp.notificationPreferenceStore,
                    backgroundRefreshStatusReader = codexMeterApp.backgroundRefreshStatusReader,
                    settingsDiagnosticsReader = codexMeterApp.settingsDiagnosticsReader,
                    quotaHistoryClearUseCase = codexMeterApp.quotaHistoryClearUseCase,
                    appUpdateChecker = codexMeterApp.appUpdateChecker,
                    appUpdateDownloader = codexMeterApp.appUpdateDownloader,
                    apiKeyLoginUseCase = codexMeterApp.apiKeyLoginUseCase,
                    currencyPreferenceReader = codexMeterApp.currencyPreferenceReader,
                    exchangeRateReader = codexMeterApp.exchangeRateReader,
                    notificationWindowChoicesLoader = codexMeterApp.notificationWindowChoicesLoader,
                    currencyPreferenceStore = codexMeterApp.currencyPreferenceStore,
                    appearancePreferenceStore = codexMeterApp.appearancePreferenceStore,
                )
            }
        }
    }
}
