package app.quotatrail.application

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.quotatrail.BuildConfig
import app.quotatrail.R
import app.quotatrail.foundation.network.ProviderHttpClient
import app.quotatrail.storage.local.db.QuotaTrailDatabase
import app.quotatrail.storage.preferences.CurrentAccountPreferences
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.preferences.NotificationPreferencesDataStore
import app.quotatrail.storage.preferences.PrimaryQuotaWindowPreferences
import app.quotatrail.storage.preferences.RetentionPreferences
import app.quotatrail.storage.preferences.UpdatePreferencesDataStore
import app.quotatrail.storage.repository.AccountDeletionRepository
import app.quotatrail.storage.repository.AccountListRepository
import app.quotatrail.storage.repository.AccountMutationRepository
import app.quotatrail.storage.repository.QuotaHistoryClearRepository
import app.quotatrail.storage.repository.RetentionCleanupRepository
import app.quotatrail.storage.repository.RoomNotificationAlertStateStore
import app.quotatrail.storage.repository.RoomCodexSessionImportPersistence
import app.quotatrail.storage.repository.RoomQuotaSnapshotStore
import app.quotatrail.storage.repository.RoomRefreshAccountStatusStore
import app.quotatrail.storage.repository.RoomRefreshAttemptStore
import app.quotatrail.storage.secure.AesGcmPayloadCipher
import app.quotatrail.storage.secure.AndroidKeystoreSecretKeyProvider
import app.quotatrail.storage.secure.FileSecureSessionStore
import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.domain.account.AccountDeleteUseCase
import app.quotatrail.domain.account.AccountListUseCase
import app.quotatrail.domain.account.AccountRenameUseCase
import app.quotatrail.domain.account.AccountSwitchUseCase
import app.quotatrail.domain.account.AccountSwitchRefreshRequester
import app.quotatrail.domain.auth.ApiKeyLoginUseCase
import app.quotatrail.domain.auth.SessionLoginUseCase
import app.quotatrail.domain.auth.DeviceCodeLoginController
import app.quotatrail.domain.auth.DeviceCodeLoginDiagnosticsReader
import app.quotatrail.domain.auth.DeviceCodeLoginNotifier
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.RefreshAttemptId
import app.quotatrail.domain.quota.CurrentQuotaStateFactory
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.domain.settings.NotificationPreferenceStore
import app.quotatrail.domain.settings.PrimaryQuotaWindowPreferenceStore
import app.quotatrail.domain.settings.QuotaHistoryClearUseCase
import app.quotatrail.domain.update.AppUpdateCheckUseCase
import app.quotatrail.domain.update.AppUpdateDownloadUseCase
import app.quotatrail.domain.update.AppUpdateNotifier
import app.quotatrail.domain.update.NoopAppUpdateCheckUseCase
import app.quotatrail.domain.update.NoopAppUpdateDownloadUseCase
import app.quotatrail.domain.update.UpdatePreferenceStore
import app.quotatrail.surfaces.notification.AndroidAppUpdateNotifier
import app.quotatrail.surfaces.notification.AndroidNotificationRequestOptionsReader
import app.quotatrail.surfaces.notification.AndroidDeviceCodeLoginNotifier
import app.quotatrail.surfaces.notification.AndroidNotificationSink
import app.quotatrail.surfaces.notification.AccountErrorEventReader
import app.quotatrail.surfaces.notification.AccountErrorPolicy
import app.quotatrail.surfaces.notification.UsageStatusPublisher
import app.quotatrail.surfaces.notification.NotificationPreferenceAlertThresholdsReader
import app.quotatrail.surfaces.notification.NotificationPreferenceQuotaAlertWindowReader
import app.quotatrail.providers.SessionImportRouter
import app.quotatrail.providers.common.auth.OAuthTokenClient
import app.quotatrail.providers.codex.CodexRefreshProvider
import app.quotatrail.providers.codex.CodexSessionCipher
import app.quotatrail.providers.codex.CodexTokenRefresh
import app.quotatrail.providers.codex.CodexUsageFetcher
import app.quotatrail.providers.codex.auth.CodexSessionImporter
import app.quotatrail.providers.codex.auth.CodexDeviceCodeClient
import app.quotatrail.providers.codex.auth.CodexDeviceCodeLoginController
import app.quotatrail.providers.codex.auth.CodexDeviceCodeLoginUseCase
import app.quotatrail.providers.codex.auth.CodexOAuthTokenExchanger
import app.quotatrail.providers.codex.auth.CodexTokenRefresher
import app.quotatrail.providers.codex.auth.DeviceCodeChallenge
import app.quotatrail.providers.codex.auth.DeviceCodeLoginAttemptId
import app.quotatrail.providers.codex.mapper.CodexUsageMapper
import app.quotatrail.providers.codex.network.CodexUsageClient
import app.quotatrail.providers.codex.session.AndroidKeystoreCodexSessionCipher
import app.quotatrail.providers.codex.session.CodexSessionPayload
import app.quotatrail.providers.deepseek.DeepSeekRefreshProvider
import app.quotatrail.providers.deepseek.auth.DeepSeekSessionImporter
import app.quotatrail.providers.deepseek.network.DeepSeekBalanceClient
import app.quotatrail.providers.zai.ZaiRefreshProvider
import app.quotatrail.providers.zai.auth.ZaiSessionImporter
import app.quotatrail.providers.zai.network.ZaiQuotaClient
import app.quotatrail.providers.minimax.MiniMaxRefreshProvider
import app.quotatrail.providers.minimax.auth.MiniMaxSessionImporter
import app.quotatrail.providers.minimax.network.MiniMaxUsageClient
import app.quotatrail.providers.cursor.CursorRefreshProvider
import app.quotatrail.providers.cursor.auth.CursorSessionImporter
import app.quotatrail.providers.cursor.network.CursorUsageClient
import app.quotatrail.providers.kimi.KimiRefreshProvider
import app.quotatrail.providers.kimi.auth.KimiSessionImporter
import app.quotatrail.providers.kimi.network.KimiQuotaClient
import app.quotatrail.providers.zaibalance.ZaiBalanceRefreshProvider
import app.quotatrail.providers.zaibalance.auth.ZaiBalanceSessionImporter
import app.quotatrail.providers.zaibalance.network.ZaiBalanceClient
import app.quotatrail.providers.claude.ClaudeRefreshProvider
import app.quotatrail.providers.claude.auth.ClaudeSessionImporter
import app.quotatrail.providers.claude.network.ClaudeUsageClient
import app.quotatrail.providers.antigravity.AntigravityRefreshProvider
import app.quotatrail.providers.antigravity.auth.AntigravitySessionImporter
import app.quotatrail.providers.antigravity.network.AntigravityQuotaClient
import app.quotatrail.storage.currency.ExchangeRateRepository
import app.quotatrail.storage.preferences.CurrencyPreferencesDataStore
import app.quotatrail.sync.AttemptIdProvider
import app.quotatrail.sync.CompositeCurrentQuotaStatePublisher
import app.quotatrail.sync.CompositeRefreshProvider
import app.quotatrail.sync.CurrentQuotaStatePublisher
import app.quotatrail.sync.ExchangeRateRefresher
import app.quotatrail.sync.MultiAccountRefreshRunner
import app.quotatrail.sync.UsageSyncCoordinator
import app.quotatrail.presentation.account.AccountRefreshAllUseCase
import app.quotatrail.sync.RefreshProvider
import app.quotatrail.presentation.account.AccountQuotaAlertEvaluationRequester
import app.quotatrail.presentation.home.HomeCurrentQuotaStateLoader
import app.quotatrail.presentation.home.HomeAccountQuotaStatesLoader
import app.quotatrail.presentation.home.HomeAccountSelectionUseCase
import app.quotatrail.presentation.home.HomeRefreshUseCase
import app.quotatrail.presentation.settings.SettingsBackgroundRefreshStatusReader
import app.quotatrail.presentation.settings.SettingsDiagnosticsReader
import app.quotatrail.surfaces.widget.WidgetDeletedAccountStateCleaner
import app.quotatrail.surfaces.widget.WidgetQuotaStateUpdater
import app.quotatrail.surfaces.widget.WidgetQuotaStateLoader
import app.quotatrail.presentation.home.HomeTrendHistoryLoader
import java.time.Clock
import java.time.Instant
import java.util.UUID
import app.quotatrail.storage.preferences.AppearancePreferences
import app.quotatrail.domain.theme.AppearancePreferenceStore
import app.quotatrail.domain.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ApplicationGraph private constructor(
    val refreshCoordinator: UsageSyncCoordinator,
    val accountListUseCase: AccountListUseCase,
    val accountDeleteUseCase: AccountDeleteUseCase,
    val accountSwitchUseCase: AccountSwitchUseCase,
    val accountRenameUseCase: AccountRenameUseCase,
    val accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester,
    val homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    val homeAccountQuotaStatesLoader: HomeAccountQuotaStatesLoader,
    val homeAccountSelectionUseCase: HomeAccountSelectionUseCase,
    val homeRefreshUseCase: HomeRefreshUseCase,
    val deviceCodeLoginController: DeviceCodeLoginController,
    val deviceCodeLoginNotifier: DeviceCodeLoginNotifier,
    val retentionPreferences: RetentionPreferences,
    val notificationPreferences: NotificationPreferenceStore,
    val primaryQuotaWindowPreferences: PrimaryQuotaWindowPreferenceStore,
    val quotaHistoryClearUseCase: QuotaHistoryClearUseCase,
    val homeTrendHistoryLoader: HomeTrendHistoryLoader,
    val backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader,
    val settingsDiagnosticsReader: SettingsDiagnosticsReader,
    val appUpdateChecker: AppUpdateCheckUseCase,
    val appUpdateDownloader: AppUpdateDownloadUseCase,
    val updatePreferenceStore: UpdatePreferenceStore,
    val appUpdateNotifier: AppUpdateNotifier,
    val widgetQuotaStateLoader: WidgetQuotaStateLoader,
    val startupMaintenance: StartupMaintenance,
    val apiKeyLoginUseCase: SessionLoginUseCase,
    val appearancePreferences: AppearancePreferenceStore,
    val initialThemeMode: ThemeMode,
    val currencyPreferences: CurrencyPreferencesDataStore,
    val exchangeRateRepository: ExchangeRateRepository,
    private val currentAccountStore: CurrentQuotaRefreshAccountStore,
    val exchangeRateRefresher: ExchangeRateRefresher,
    val notificationWindowChoicesLoader: NotificationWindowChoicesLoader,
) {
    suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount> =
        currentAccountStore.activeAccounts()

    suspend fun manuallyRefreshableQuotaRefreshAccounts(): List<ProviderAccount> =
        currentAccountStore.manuallyRefreshableAccounts()

    /**
     * Pull-to-refresh on the Account screen: refresh every manually-refreshable account in parallel,
     * including NeedsReauth ones, so a transient failure can be retried and self-heal on success.
     */
    val accountRefreshAllUseCase: AccountRefreshAllUseCase
        get() = AccountRefreshAllUseCase {
            MultiAccountRefreshRunner(
                refreshCoordinator = refreshCoordinator,
                exchangeRateRefresher = exchangeRateRefresher,
            ).refresh(
                accounts = currentAccountStore.manuallyRefreshableAccounts(),
                trigger = RefreshTrigger.Manual,
            )
        }

    companion object {
        fun create(context: Context): ApplicationGraph {
            val appContext = context.applicationContext
            val database = Room.databaseBuilder(
                appContext,
                QuotaTrailDatabase::class.java,
                QuotaTrailDatabase.DATABASE_NAME,
            ).build()
            val currentAccountPreferences = CurrentAccountPreferences.create(
                file = appContext.preferencesDataStoreFile(PREFERENCES_FILE_NAME),
            )
            val appearancePreferences = AppearancePreferences.create(
                file = appContext.preferencesDataStoreFile(APPEARANCE_PREFERENCES_FILE_NAME),
            )
            // Preheat the first value synchronously so the first frame uses the correct theme.
            val initialThemeMode = runBlocking {
                appearancePreferences.themeMode.first()
            }
            val retentionPreferences = RetentionPreferences(currentAccountPreferences.dataStore)
            val notificationPreferences = NotificationPreferencesDataStore(currentAccountPreferences.dataStore)
            val primaryQuotaWindowPreferences = PrimaryQuotaWindowPreferences(currentAccountPreferences.dataStore)
            val updatePreferences = UpdatePreferencesDataStore.create(
                file = appContext.preferencesDataStoreFile(UPDATE_PREFERENCES_FILE_NAME),
            )
            val appUpdateNotifier = AndroidAppUpdateNotifier(
                notificationSink = AndroidNotificationSink(appContext),
            )
            val httpClient = ProviderHttpClient(
                allowedHosts = app.quotatrail.security.PersonalSecurityPolicy.API_HOST_ALLOWLIST,
            )
            val usageClient = CodexUsageClient(httpClient)
            val sessionStore = FileSecureSessionStore(
                directory = appContext.filesDir.resolve(SESSION_DIRECTORY_NAME),
            )
            val sessionCipher = AndroidKeystoreCodexSessionCipher()
            val payloadCipher: PayloadCipher = AesGcmPayloadCipher(AndroidKeystoreSecretKeyProvider())
            val widgetDeletedAccountStateCleaner = WidgetDeletedAccountStateCleaner(appContext)
            val clock = Clock.systemUTC()
            val currencyPreferences = CurrencyPreferencesDataStore(currentAccountPreferences.dataStore)
            // Claude/Codex expose no monetary balance, so the private build never calls an FX API.
            val exchangeRateRepository = ExchangeRateRepository(
                cache = currencyPreferences,
                fetch = { null },
            )
            val defaultAccountDisplayName = appContext.getString(R.string.account_default_display_name)
            val currentQuotaStateFactory = CurrentQuotaStateFactory()
            val widgetQuotaStateRepository = WidgetProjectionRepository(
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStateFactory = currentQuotaStateFactory,
                notificationPreferenceReader = notificationPreferences,
                clock = clock,
            )
            val currentQuotaStateRepository = CurrentUsageRepository(
                currentAccountReader = currentAccountPreferences,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStateFactory = currentQuotaStateFactory,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                notificationPreferenceReader = notificationPreferences,
                clock = clock,
                currencyPreferenceReader = currencyPreferences,
                exchangeRateReader = exchangeRateRepository,
            )
            val accountErrorPolicy = AccountErrorPolicy()

            val codexRefreshProvider = CodexRefreshProvider(
                sessionStore = sessionStore,
                sessionCipher = sessionCipher,
                tokenRefresh = CodexTokenRefresh(CodexTokenRefresher(httpClient)::refresh),
                usageFetcher = CodexUsageFetcher(usageClient::fetchUsage),
                clock = clock,
            )
            val deepseekBalanceClient = DeepSeekBalanceClient(httpClient)
            val deepseekRefreshProvider = DeepSeekRefreshProvider(
                client = deepseekBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiQuotaClient = ZaiQuotaClient(httpClient)
            val zaiRefreshProvider = ZaiRefreshProvider(
                client = zaiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val minimaxUsageClient = MiniMaxUsageClient(httpClient)
            val minimaxRefreshProvider = MiniMaxRefreshProvider(
                client = minimaxUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val cursorUsageClient = CursorUsageClient(httpClient)
            val cursorRefreshProvider = CursorRefreshProvider(
                client = cursorUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val kimiQuotaClient = KimiQuotaClient(httpClient)
            val kimiRefreshProvider = KimiRefreshProvider(
                client = kimiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiBalanceClient = ZaiBalanceClient(httpClient)
            val zaiBalanceRefreshProvider = ZaiBalanceRefreshProvider(
                client = zaiBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val claudeUsageClient = ClaudeUsageClient(httpClient)
            val claudeTokenClient = OAuthTokenClient(
                httpClient = httpClient,
                tokenEndpoint = app.quotatrail.providers.claude.auth.ClaudeOAuthConfig.TOKEN_ENDPOINT,
                clientId = app.quotatrail.providers.claude.auth.ClaudeOAuthConfig.CLIENT_ID,
                diagnosticsPrefix = "claude_oauth",
                useJsonBody = true,
                userAgent = app.quotatrail.providers.claude.auth.ClaudeOAuthConfig.USER_AGENT,
            )
            val claudeRefreshProvider = ClaudeRefreshProvider(
                usageFetcher = claudeUsageClient::fetchUsage,
                tokenRefresher = claudeTokenClient::refresh,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val antigravityQuotaClient = AntigravityQuotaClient(httpClient)
            val antigravityTokenClient = OAuthTokenClient(
                httpClient = httpClient,
                tokenEndpoint = "https://oauth2.googleapis.com/token",
                clientId = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
                clientSecret = BuildConfig.ANTIGRAVITY_OAUTH_CLIENT_SECRET,
                diagnosticsPrefix = "antigravity_oauth",
            )
            val antigravityRefreshProvider = AntigravityRefreshProvider(
                client = antigravityQuotaClient,
                tokenClient = antigravityTokenClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val compositeRefreshProvider = CompositeRefreshProvider(
                providers = mapOf(
                    ProviderId("codex") to codexRefreshProvider,
                    ProviderId("deepseek") to deepseekRefreshProvider,
                    ProviderId("zai") to zaiRefreshProvider,
                    ProviderId("minimax") to minimaxRefreshProvider,
                    ProviderId("cursor") to cursorRefreshProvider,
                    ProviderId("kimi") to kimiRefreshProvider,
                    ProviderId("zai_balance") to zaiBalanceRefreshProvider,
                    ProviderId("claude") to claudeRefreshProvider,
                    ProviderId("antigravity") to antigravityRefreshProvider,
                ),
            )
            val deepseekSessionImporter = DeepSeekSessionImporter(
                balanceClient = deepseekBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val minimaxSessionImporter = MiniMaxSessionImporter(
                usageClient = minimaxUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiSessionImporter = ZaiSessionImporter(
                client = zaiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val cursorSessionImporter = CursorSessionImporter(
                usageClient = cursorUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val kimiSessionImporter = KimiSessionImporter(
                client = kimiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiBalanceSessionImporter = ZaiBalanceSessionImporter(
                client = zaiBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val claudeSessionImporter = ClaudeSessionImporter(
                tokenClient = claudeTokenClient,
                client = claudeUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val antigravitySessionImporter = AntigravitySessionImporter(
                tokenClient = antigravityTokenClient,
                client = antigravityQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )

            return fromDatabase(
                appContext = appContext,
                database = database,
                currentAccountReader = currentAccountPreferences,
                currentAccountPreferences = currentAccountPreferences,
                retentionPreferences = retentionPreferences,
                notificationPreferences = notificationPreferences,
                primaryQuotaWindowPreferences = primaryQuotaWindowPreferences,
                sessionStore = sessionStore,
                sessionCipher = sessionCipher,
                refreshProvider = compositeRefreshProvider,
                usageClient = usageClient,
                deepseekSessionImporter = deepseekSessionImporter,
                minimaxSessionImporter = minimaxSessionImporter,
                zaiSessionImporter = zaiSessionImporter,
                cursorSessionImporter = cursorSessionImporter,
                kimiSessionImporter = kimiSessionImporter,
                zaiBalanceSessionImporter = zaiBalanceSessionImporter,
                claudeSessionImporter = claudeSessionImporter,
                antigravitySessionImporter = antigravitySessionImporter,
                deviceCodeLoginNotifier = AndroidDeviceCodeLoginNotifier(
                    notificationSink = AndroidNotificationSink(appContext),
                ),
                currentQuotaStatePublisher = CompositeCurrentQuotaStatePublisher(
                    listOf(
                        WidgetQuotaStateUpdater(
                            context = appContext,
                            notificationPreferenceReader = notificationPreferences,
                            widgetQuotaStateLoader = widgetQuotaStateRepository,
                        ),
                        UsageStatusPublisher(
                            notificationSink = AndroidNotificationSink(appContext),
                            alertStateStore = RoomNotificationAlertStateStore(database.alertStateDao()),
                            optionsReader = AndroidNotificationRequestOptionsReader(
                                context = appContext,
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            alertThresholdsReader = NotificationPreferenceAlertThresholdsReader(
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            alertWindowPreferenceReader = NotificationPreferenceQuotaAlertWindowReader(
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            statusNotificationStatesLoader = currentQuotaStateRepository,
                            accountErrorEventReader = AccountErrorEventReader { state ->
                                val account = state.account
                                val consecutiveFailureCount = if (account == null) {
                                    0
                                } else {
                                    database.refreshAttemptDao().countConsecutiveFailuresSinceLatestSuccess(
                                        providerId = account.providerId.value,
                                        localAccountId = account.localAccountId.value,
                                    )
                                }
                                accountErrorPolicy.evaluate(
                                    state = state,
                                    consecutiveFailureCount = consecutiveFailureCount,
                                )
                            },
                            clock = clock,
                            currencyPreferenceReader = currencyPreferences,
                            exchangeRateReader = exchangeRateRepository,
                        ),
                    ),
                ),
                currentQuotaStateRepository = currentQuotaStateRepository,
                accountListRepository = AccountListRepository(
                    providerAccountDao = database.providerAccountDao(),
                    quotaSnapshotDao = database.quotaSnapshotDao(),
                    currentAccountReader = currentAccountPreferences,
                ),
                accountDeletionRepository = AccountDeletionRepository(
                    database = database,
                    secureSessionStore = sessionStore,
                    currentAccountStore = currentAccountPreferences,
                    deletedAccountStateCleaner = widgetDeletedAccountStateCleaner,
                ),
                retentionCleanupRepository = RetentionCleanupRepository(database),
                quotaHistoryClearRepository = QuotaHistoryClearRepository(
                    database = database,
                    currentAccountReader = currentAccountPreferences,
                ),
                currentQuotaStateFactory = currentQuotaStateFactory,
                widgetQuotaStateRepository = widgetQuotaStateRepository,
                defaultAccountDisplayName = defaultAccountDisplayName,
                httpClient = httpClient,
                clock = clock,
                appearancePreferences = appearancePreferences,
                initialThemeMode = initialThemeMode,
                currencyPreferences = currencyPreferences,
                exchangeRateRepository = exchangeRateRepository,
                exchangeRateRefresher = ExchangeRateRefresher { exchangeRateRepository.refreshIfStale(clock.instant()) },
                updatePreferences = updatePreferences,
                appUpdateNotifier = appUpdateNotifier,
            )
        }

        private fun fromDatabase(
            appContext: Context,
            database: QuotaTrailDatabase,
            currentAccountReader: CurrentAccountReader,
            currentAccountPreferences: CurrentAccountPreferences,
            retentionPreferences: RetentionPreferences,
            notificationPreferences: NotificationPreferencesDataStore,
            primaryQuotaWindowPreferences: PrimaryQuotaWindowPreferences,
            sessionStore: FileSecureSessionStore,
            sessionCipher: CodexSessionCipher,
            refreshProvider: RefreshProvider,
            usageClient: CodexUsageClient,
            deepseekSessionImporter: DeepSeekSessionImporter,
            minimaxSessionImporter: MiniMaxSessionImporter,
            zaiSessionImporter: ZaiSessionImporter,
            cursorSessionImporter: CursorSessionImporter,
            kimiSessionImporter: KimiSessionImporter,
            zaiBalanceSessionImporter: ZaiBalanceSessionImporter,
            claudeSessionImporter: ClaudeSessionImporter,
            antigravitySessionImporter: AntigravitySessionImporter,
            deviceCodeLoginNotifier: DeviceCodeLoginNotifier,
            currentQuotaStatePublisher: CurrentQuotaStatePublisher,
            currentQuotaStateRepository: CurrentUsageRepository,
            accountListRepository: AccountListRepository,
            accountDeletionRepository: AccountDeletionRepository,
            retentionCleanupRepository: RetentionCleanupRepository,
            quotaHistoryClearRepository: QuotaHistoryClearRepository,
            currentQuotaStateFactory: CurrentQuotaStateFactory,
            widgetQuotaStateRepository: WidgetProjectionRepository,
            defaultAccountDisplayName: String,
            httpClient: ProviderHttpClient,
            clock: Clock,
            appearancePreferences: AppearancePreferenceStore,
            initialThemeMode: ThemeMode,
            currencyPreferences: CurrencyPreferencesDataStore,
            exchangeRateRepository: ExchangeRateRepository,
            exchangeRateRefresher: ExchangeRateRefresher,
            updatePreferences: UpdatePreferenceStore,
            appUpdateNotifier: AppUpdateNotifier,
        ): ApplicationGraph {
            val sessionImporter = CodexSessionImporter(
                usageClient = CodexSessionImporter.UsageClient(usageClient::fetchUsage),
                mapper = CodexUsageMapper(),
                importPersistence = RoomCodexSessionImportPersistence(
                    database = database,
                    sessionStore = sessionStore,
                    currentAccountStore = currentAccountPreferences,
                ),
                sessionEnvelopeFactory = CodexSessionEnvelopeFactory(sessionCipher),
                localAccountIdProvider = CodexSessionImporter.LocalAccountIdProvider {
                    LocalAccountId("codex-${UUID.randomUUID()}")
                },
                defaultDisplayName = defaultAccountDisplayName,
                clock = clock,
            )
            val sessionImportRouter = SessionImportRouter(
                importers = mapOf(
                    ProviderId("codex") to sessionImporter,
                    ProviderId("deepseek") to deepseekSessionImporter,
                    ProviderId("zai") to zaiSessionImporter,
                    ProviderId("minimax") to minimaxSessionImporter,
                    ProviderId("cursor") to cursorSessionImporter,
                    ProviderId("kimi") to kimiSessionImporter,
                    ProviderId("zai_balance") to zaiBalanceSessionImporter,
                    ProviderId("claude") to claudeSessionImporter,
                    ProviderId("antigravity") to antigravitySessionImporter,
                ),
            )
            val sessionLoginUseCase = SessionLoginUseCase(
                importRouter = sessionImportRouter,
                database = database,
                accountDao = database.providerAccountDao(),
                snapshotDao = database.quotaSnapshotDao(),
                currentAccountStore = currentAccountPreferences,
                clock = clock,
            )
            val codexDeviceCodeClient = CodexDeviceCodeClient(httpClient)
            val codexDeviceCodeLoginUseCase = CodexDeviceCodeLoginUseCase(
                deviceCodeClient = object : CodexDeviceCodeLoginUseCase.DeviceCodeClient {
                    override suspend fun requestDeviceCode() =
                        codexDeviceCodeClient.requestDeviceCode()

                    override suspend fun pollAuthorization(challenge: DeviceCodeChallenge) =
                        codexDeviceCodeClient.pollAuthorization(challenge)
                },
                tokenExchanger = CodexDeviceCodeLoginUseCase.TokenExchanger(
                    CodexOAuthTokenExchanger(httpClient)::exchange,
                ),
                sessionImporter = object : CodexDeviceCodeLoginUseCase.SessionImporter {
                    override suspend fun prepareDeviceCodeSession(
                        session: CodexSessionPayload,
                    ): CodexSessionImporter.PrepareResult =
                        sessionImporter.prepareDeviceCodeSession(session)

                    override suspend fun commitPreparedDeviceCodeSession(
                        preparedImport: CodexSessionImporter.PreparedImport,
                    ): CodexSessionImporter.Result =
                        sessionImporter.commitPreparedDeviceCodeSession(preparedImport)
                },
                attemptIdProvider = {
                    DeviceCodeLoginAttemptId("device-${UUID.randomUUID()}")
                },
                clock = clock,
            )
            val currentQuotaRefreshAccountStore = CurrentQuotaRefreshAccountStore(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
            )
            val refreshCoordinator = UsageSyncCoordinator(
                accountExists = { account ->
                    database.providerAccountDao().getById(account.localAccountId.value)
                        ?.let { it.providerId == account.providerId.value } == true
                },
                provider = refreshProvider,
                snapshotStore = RoomQuotaSnapshotStore(database.quotaSnapshotDao()),
                attemptStore = RoomRefreshAttemptStore(database.refreshAttemptDao()),
                accountStatusStore = RoomRefreshAccountStatusStore(database.providerAccountDao()),
                attemptIdProvider = AttemptIdProvider {
                    RefreshAttemptId("periodic-${UUID.randomUUID()}")
                },
                currentQuotaStatePublisher = currentQuotaStatePublisher,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                clock = clock,
            )
            val currentAccountStateRepublisher = CurrentAccountQuotaStateRepublisher(
                currentAccountReader = currentAccountPreferences,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStatePublisher = currentQuotaStatePublisher,
                currentQuotaStateFactory = currentQuotaStateFactory,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                clock = clock,
            )
            val homeAccountSelectionRepository = DashboardSelectionStore(
                providerAccountDao = database.providerAccountDao(),
                currentAccountStore = currentAccountPreferences,
                currentAccountStateRepublisher = currentAccountStateRepublisher,
            )
            val accountMutationRepository = AccountMutationRepository(
                providerAccountDao = database.providerAccountDao(),
                currentAccountStore = currentAccountPreferences,
                accountSwitchRefreshRequester = AccountSwitchRefreshRequester { account ->
                    try {
                        refreshCoordinator.refresh(
                            account = account,
                            trigger = RefreshTrigger.AccountSwitch,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // Account selection is already durable; a later refresh/app open can recover state.
                    }
                },
                currentAccountStateRepublisher = currentAccountStateRepublisher,
                clock = clock,
            )
            val homeRefreshUseCase = HomeUsageSyncCoordinatorUseCase(
                currentAccountStore = currentQuotaRefreshAccountStore,
                refreshCoordinator = refreshCoordinator,
                currentQuotaStateLoader = currentQuotaStateRepository,
            )
            val homeTrendHistoryRepository = UsageTrendRepository(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                clock = clock,
            )
            val backgroundRefreshStatusRepository = SettingsBackgroundRefreshStatusRepository(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
            )
            val deviceCodeLoginController = CodexDeviceCodeLoginController(
                useCase = codexDeviceCodeLoginUseCase,
                onSaved = { saved ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateFactory.create(
                            account = saved.account,
                            latestSnapshot = saved.snapshot,
                            latestAttempt = null,
                            now = clock.instant(),
                            primaryWindowId = primaryQuotaWindowPreferences.primaryQuotaWindowId(),
                        ),
                    )
                },
            )
            val deviceCodeLoginDiagnosticsReader = deviceCodeLoginController as DeviceCodeLoginDiagnosticsReader
            val settingsDiagnosticsReader = SettingsDiagnosticsRepository(
                context = appContext,
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                sessionStore = sessionStore,
                deviceCodeLoginDiagnosticsReader = deviceCodeLoginDiagnosticsReader,
                notificationPreferenceReader = notificationPreferences,
                retentionPreferenceReader = retentionPreferences,
            )

            return ApplicationGraph(
                refreshCoordinator = refreshCoordinator,
                accountListUseCase = accountListRepository,
                accountDeleteUseCase = CoordinatedAccountDeletion(
                    coordinator = refreshCoordinator,
                    delegate = AccountDeleteUseCase(accountDeletionRepository::deleteAccount),
                    onDeleted = {
                        currentQuotaStatePublisher.publish(currentQuotaStateRepository.loadCurrentState())
                    },
                ),
                accountSwitchUseCase = accountMutationRepository,
                accountRenameUseCase = accountMutationRepository,
                accountQuotaAlertEvaluationRequester = AccountQuotaAlertEvaluationRequester { providerId, localAccountId, windowId ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateRepository.loadAccountState(
                            providerId = providerId,
                            localAccountId = localAccountId,
                            primaryWindowId = windowId,
                        ),
                    )
                },
                homeCurrentQuotaStateLoader = currentQuotaStateRepository,
                homeAccountQuotaStatesLoader = currentQuotaStateRepository,
                homeAccountSelectionUseCase = homeAccountSelectionRepository,
                homeRefreshUseCase = homeRefreshUseCase,
                deviceCodeLoginController = deviceCodeLoginController,
                deviceCodeLoginNotifier = deviceCodeLoginNotifier,
                retentionPreferences = retentionPreferences,
                notificationPreferences = RepublishingNotificationPreferenceStore(
                    delegate = notificationPreferences,
                    currentQuotaStateLoader = currentQuotaStateRepository,
                    currentQuotaStatePublisher = currentQuotaStatePublisher,
                ),
                primaryQuotaWindowPreferences = primaryQuotaWindowPreferences,
                quotaHistoryClearUseCase = RepublishingQuotaHistoryClearUseCase(
                    delegate = quotaHistoryClearRepository,
                    currentQuotaStateLoader = currentQuotaStateRepository,
                    currentQuotaStatePublisher = currentQuotaStatePublisher,
                ),
                homeTrendHistoryLoader = homeTrendHistoryRepository,
                backgroundRefreshStatusReader = backgroundRefreshStatusRepository,
                settingsDiagnosticsReader = settingsDiagnosticsReader,
                appUpdateChecker = NoopAppUpdateCheckUseCase,
                appUpdateDownloader = NoopAppUpdateDownloadUseCase,
                updatePreferenceStore = updatePreferences,
                appUpdateNotifier = appUpdateNotifier,
                widgetQuotaStateLoader = widgetQuotaStateRepository,
                startupMaintenance = StartupMaintenance(
                    retentionPreferenceReader = retentionPreferences,
                    retentionCleanup = retentionCleanupRepository,
                    reporter = AndroidStartupMaintenanceReporter,
                    clock = clock,
                ),
                apiKeyLoginUseCase = sessionLoginUseCase,
                appearancePreferences = appearancePreferences,
                initialThemeMode = initialThemeMode,
                currencyPreferences = currencyPreferences,
                exchangeRateRepository = exchangeRateRepository,
                currentAccountStore = currentQuotaRefreshAccountStore,
                exchangeRateRefresher = exchangeRateRefresher,
                notificationWindowChoicesLoader = DefaultNotificationWindowChoicesLoader(
                    currentAccountReader = currentAccountReader,
                    quotaSnapshotDao = database.quotaSnapshotDao(),
                ),
            )
        }

        private const val PREFERENCES_FILE_NAME = "quotatrail.preferences_pb"
        private const val APPEARANCE_PREFERENCES_FILE_NAME = "appearance.preferences_pb"
        private const val UPDATE_PREFERENCES_FILE_NAME = "update.preferences_pb"
        private const val SESSION_DIRECTORY_NAME = "sessions"
    }
}

private class CodexSessionEnvelopeFactory(
    private val sessionCipher: CodexSessionCipher,
) : CodexSessionImporter.SessionEnvelopeFactory {
    override fun create(
        payload: CodexSessionPayload,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        now: Instant,
    ): ProviderSessionEnvelope {
        val envelope = ProviderSessionEnvelope(
            providerId = CODEX_PROVIDER_ID.value,
            localAccountId = localAccountId.value,
            providerAccountId = providerAccountId?.value,
            schemaVersion = CODEX_SESSION_SCHEMA_VERSION,
            payloadCiphertext = byteArrayOf(),
            payloadNonce = byteArrayOf(),
            createdAt = now.toString(),
            updatedAt = now.toString(),
        )
        return sessionCipher.encrypt(
            session = payload,
            envelope = envelope,
            updatedAt = now,
        )
    }

    private companion object {
        const val CODEX_SESSION_SCHEMA_VERSION = 1
    }
}

private val CODEX_PROVIDER_ID = ProviderId("codex")
