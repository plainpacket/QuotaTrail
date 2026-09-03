package app.quotatrail

import android.app.Application
import androidx.work.Configuration
import app.quotatrail.BuildConfig
import app.quotatrail.application.ApplicationGraph
import app.quotatrail.foundation.i18n.AppLocaleController
import app.quotatrail.domain.account.AccountDeleteUseCase
import app.quotatrail.domain.account.AccountListUseCase
import app.quotatrail.domain.account.AccountRenameUseCase
import app.quotatrail.domain.account.AccountSwitchUseCase
import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.domain.auth.ApiKeyLoginUseCase
import app.quotatrail.domain.auth.SessionLoginUseCase
import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.currency.CurrencyPreferenceStore
import app.quotatrail.domain.auth.DeviceCodeLoginController
import app.quotatrail.domain.auth.DeviceCodeLoginNotifier
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.settings.DEFAULT_REFRESH_INTERVAL_MINUTES
import app.quotatrail.domain.settings.NotificationPreferenceStore
import app.quotatrail.domain.settings.PrimaryQuotaWindowPreferenceStore
import app.quotatrail.domain.settings.QuotaHistoryClearUseCase
import app.quotatrail.domain.settings.RetentionPreferenceStore
import app.quotatrail.domain.theme.AppearancePreferenceStore
import app.quotatrail.domain.theme.ThemeMode
import app.quotatrail.domain.update.AppUpdateCheckUseCase
import app.quotatrail.domain.update.AppUpdateDownloadUseCase
import app.quotatrail.domain.update.AppUpdateNotifier
import app.quotatrail.domain.update.UpdatePreferenceStore
import app.quotatrail.sync.ExchangeRateRefresher
import app.quotatrail.sync.QuotaRefreshDependenciesProvider
import app.quotatrail.sync.UsageSyncCoordinator
import app.quotatrail.presentation.home.HomeCurrentQuotaStateLoader
import app.quotatrail.presentation.home.HomeAccountQuotaStatesLoader
import app.quotatrail.presentation.home.HomeAccountSelectionUseCase
import app.quotatrail.presentation.home.HomeRefreshUseCase
import app.quotatrail.presentation.home.HomeTrendHistoryLoader
import app.quotatrail.sync.SyncWorkScheduler
import app.quotatrail.application.NotificationWindowChoicesLoader
import app.quotatrail.presentation.account.AccountQuotaAlertEvaluationRequester
import app.quotatrail.presentation.settings.SettingsBackgroundRefreshStatusReader
import app.quotatrail.presentation.settings.SettingsDiagnosticsReader
import app.quotatrail.delivery.UpdateCheckDependenciesProvider
import app.quotatrail.surfaces.widget.WidgetQuotaStateLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuotaTrailApplication :
    Application(),
    Configuration.Provider,
    QuotaRefreshDependenciesProvider,
    UpdateCheckDependenciesProvider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appContainer: ApplicationGraph by lazy {
        ApplicationGraph.create(this)
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder().build()

    override val refreshCoordinator: UsageSyncCoordinator
        get() = appContainer.refreshCoordinator

    override val exchangeRateRefresher: ExchangeRateRefresher
        get() = appContainer.exchangeRateRefresher

    val accountListUseCase: AccountListUseCase
        get() = appContainer.accountListUseCase

    val accountDeleteUseCase: AccountDeleteUseCase
        get() = appContainer.accountDeleteUseCase

    val accountSwitchUseCase: AccountSwitchUseCase
        get() = appContainer.accountSwitchUseCase

    val accountRenameUseCase: AccountRenameUseCase
        get() = appContainer.accountRenameUseCase

    val accountRefreshAllUseCase: app.quotatrail.presentation.account.AccountRefreshAllUseCase
        get() = appContainer.accountRefreshAllUseCase

    val accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester
        get() = appContainer.accountQuotaAlertEvaluationRequester

    val homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader
        get() = appContainer.homeCurrentQuotaStateLoader

    val homeAccountQuotaStatesLoader: HomeAccountQuotaStatesLoader
        get() = appContainer.homeAccountQuotaStatesLoader

    val homeAccountSelectionUseCase: HomeAccountSelectionUseCase
        get() = appContainer.homeAccountSelectionUseCase

    val homeRefreshUseCase: HomeRefreshUseCase
        get() = appContainer.homeRefreshUseCase

    val deviceCodeLoginController: DeviceCodeLoginController
        get() = appContainer.deviceCodeLoginController

    val deviceCodeLoginNotifier: DeviceCodeLoginNotifier
        get() = appContainer.deviceCodeLoginNotifier

    val retentionPreferenceStore: RetentionPreferenceStore
        get() = appContainer.retentionPreferences

    val notificationPreferenceStore: NotificationPreferenceStore
        get() = appContainer.notificationPreferences

    val primaryQuotaWindowPreferenceStore: PrimaryQuotaWindowPreferenceStore
        get() = appContainer.primaryQuotaWindowPreferences

    val quotaHistoryClearUseCase: QuotaHistoryClearUseCase
        get() = appContainer.quotaHistoryClearUseCase

    val homeTrendHistoryLoader: HomeTrendHistoryLoader
        get() = appContainer.homeTrendHistoryLoader

    val backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader
        get() = appContainer.backgroundRefreshStatusReader

    val settingsDiagnosticsReader: SettingsDiagnosticsReader
        get() = appContainer.settingsDiagnosticsReader

    val appUpdateChecker: AppUpdateCheckUseCase
        get() = appContainer.appUpdateChecker

    val appUpdateDownloader: AppUpdateDownloadUseCase
        get() = appContainer.appUpdateDownloader

    override val appUpdateCheck: AppUpdateCheckUseCase
        get() = appContainer.appUpdateChecker

    override val updatePreferenceStore: UpdatePreferenceStore
        get() = appContainer.updatePreferenceStore

    override val appUpdateNotifier: AppUpdateNotifier
        get() = appContainer.appUpdateNotifier

    override val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    val widgetQuotaStateLoader: WidgetQuotaStateLoader
        get() = appContainer.widgetQuotaStateLoader

    val apiKeyLoginUseCase: SessionLoginUseCase
        get() = appContainer.apiKeyLoginUseCase

    val currencyPreferenceReader: CurrencyPreferenceReader
        get() = appContainer.currencyPreferences

    val currencyPreferenceStore: CurrencyPreferenceStore
        get() = appContainer.currencyPreferences

    val exchangeRateReader: ExchangeRateReader
        get() = appContainer.exchangeRateRepository

    val notificationWindowChoicesLoader: NotificationWindowChoicesLoader
        get() = appContainer.notificationWindowChoicesLoader

    val appearancePreferenceStore: AppearancePreferenceStore
        get() = appContainer.appearancePreferences

    val initialThemeMode: ThemeMode
        get() = appContainer.initialThemeMode

    override fun onCreate() {
        super.onCreate()
        AppLocaleController.ensureEnglishLocale(this)
        appContainer.startupMaintenance.start(applicationScope)
        val scheduler = SyncWorkScheduler.from(this)
        registerRefreshWork(scheduler)
        // Re-apply the user's configured cadence (15/30 min, or Manual = cancel) once prefs are read.
        applicationScope.launch {
            val minutes = runCatching {
                appContainer.notificationPreferences.notificationPreferences().backgroundRefreshIntervalMinutes
            }.getOrDefault(DEFAULT_REFRESH_INTERVAL_MINUTES)
            scheduler.applyIntervalMinutes(minutes)
        }

    }

    override suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount> =
        appContainer.activeQuotaRefreshAccounts()

    override suspend fun manuallyRefreshableQuotaRefreshAccounts(): List<ProviderAccount> =
        appContainer.manuallyRefreshableQuotaRefreshAccounts()

    companion object {
        fun registerRefreshWork(scheduler: SyncWorkScheduler) {
            scheduler.schedulePeriodicRefresh()
        }
    }
}
