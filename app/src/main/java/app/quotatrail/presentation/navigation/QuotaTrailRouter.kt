package app.quotatrail.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.security.MessageDigest
import java.security.SecureRandom
import app.quotatrail.R
import app.quotatrail.domain.auth.DeviceCodeLoginController
import app.quotatrail.domain.auth.DeviceCodeLoginNotifier
import app.quotatrail.domain.auth.NoopDeviceCodeLoginController
import app.quotatrail.domain.auth.NoopDeviceCodeLoginNotifier
import app.quotatrail.domain.auth.ApiKeyLoginUseCase
import app.quotatrail.domain.auth.SessionLoginUseCase
import app.quotatrail.domain.account.AccountDeleteUseCase
import app.quotatrail.domain.account.AccountListUseCase
import app.quotatrail.domain.account.AccountRenameUseCase
import app.quotatrail.domain.account.AccountSwitchUseCase
import app.quotatrail.domain.account.NoopAccountRenameUseCase
import app.quotatrail.domain.account.NoopAccountSwitchUseCase
import app.quotatrail.domain.settings.NotificationPreferenceStore
import app.quotatrail.domain.settings.NoopQuotaHistoryClearUseCase
import app.quotatrail.domain.settings.QuotaHistoryClearUseCase
import app.quotatrail.domain.settings.RetentionPreferenceStore
import app.quotatrail.presentation.account.AccountQuotaAlertEvaluationRequester
import app.quotatrail.presentation.account.AccountRefreshAllUseCase
import app.quotatrail.presentation.account.AccountRoute
import app.quotatrail.presentation.account.NoopAccountQuotaAlertEvaluationRequester
import app.quotatrail.presentation.account.NoopAccountRefreshAllUseCase
import app.quotatrail.presentation.auth.AddAccountEntryMode
import app.quotatrail.presentation.auth.AddAccountRoute
import app.quotatrail.presentation.auth.ApiKeyAuthRegion
import app.quotatrail.presentation.auth.ApiKeyAuthScreen
import app.quotatrail.sync.SyncWorkScheduler
import app.quotatrail.presentation.auth.ProviderSelectionScreen
import app.quotatrail.presentation.auth.ProviderSelectionSheet
import app.quotatrail.presentation.auth.WebViewAuthConfig
import app.quotatrail.presentation.auth.WebViewAuthScreen
import app.quotatrail.presentation.components.QuotaTrailBackdrop
import app.quotatrail.presentation.components.InstrumentSurface
import app.quotatrail.presentation.components.InstrumentSurfaceLevel
import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.currency.CurrencyPreferenceStore
import app.quotatrail.domain.currency.CurrencyPreferences
import app.quotatrail.domain.theme.AppearancePreferenceStore
import app.quotatrail.presentation.settings.NoopAppearancePreferenceStore
import app.quotatrail.presentation.settings.NoopCurrencyPreferenceStore
import app.quotatrail.presentation.home.HomeCurrentQuotaStateLoader
import app.quotatrail.presentation.home.HomeAccountQuotaStatesLoader
import app.quotatrail.presentation.home.HomeAccountSelectionUseCase
import app.quotatrail.presentation.home.NoopHomeAccountSelectionUseCase
import app.quotatrail.presentation.home.HomeRefreshUseCase
import app.quotatrail.presentation.home.HomeTrendHistoryLoader
import app.quotatrail.presentation.home.HomeRoute
import app.quotatrail.presentation.motion.TrailMotion
import app.quotatrail.presentation.motion.QuotaTrailPageCascade
import app.quotatrail.presentation.motion.rememberQuotaTrailAnimatorsEnabled
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.domain.update.AppUpdateCheckUseCase
import app.quotatrail.domain.update.AppUpdateDownloadUseCase
import app.quotatrail.domain.update.NoopAppUpdateCheckUseCase
import app.quotatrail.domain.update.NoopAppUpdateDownloadUseCase
import app.quotatrail.domain.update.NoopUpdatePreferenceStore
import app.quotatrail.domain.update.UpdatePreferenceStore
import app.quotatrail.delivery.UpdateCheckWorkScheduler
import app.quotatrail.application.NotificationWindowChoicesLoader
import app.quotatrail.presentation.settings.InMemoryNotificationPreferenceStore
import app.quotatrail.presentation.settings.InMemoryRetentionPreferenceStore
import app.quotatrail.presentation.settings.SettingsRoute
import app.quotatrail.presentation.settings.SettingsDiagnosticsReader
import app.quotatrail.presentation.settings.DefaultSettingsDiagnosticsReader

@Composable
fun QuotaTrailRouter(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = QuotaTrailDestination.Home.route,
    deviceCodeLoginController: DeviceCodeLoginController = NoopDeviceCodeLoginController,
    deviceCodeLoginNotifier: DeviceCodeLoginNotifier = NoopDeviceCodeLoginNotifier,
    accountListUseCase: AccountListUseCase,
    accountDeleteUseCase: AccountDeleteUseCase,
    accountSwitchUseCase: AccountSwitchUseCase = NoopAccountSwitchUseCase,
    accountRenameUseCase: AccountRenameUseCase = NoopAccountRenameUseCase,
    accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester =
        NoopAccountQuotaAlertEvaluationRequester,
    accountRefreshAllUseCase: AccountRefreshAllUseCase = NoopAccountRefreshAllUseCase,
    homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    homeAccountQuotaStatesLoader: HomeAccountQuotaStatesLoader? = null,
    homeAccountSelectionUseCase: HomeAccountSelectionUseCase = NoopHomeAccountSelectionUseCase,
    homeRefreshUseCase: HomeRefreshUseCase,
    homeTrendHistoryLoader: HomeTrendHistoryLoader = HomeTrendHistoryLoader { _, _ -> emptyList() },
    currencyPreferenceReader: CurrencyPreferenceReader = object : CurrencyPreferenceReader {
        override suspend fun currencyPreferences() = CurrencyPreferences()
    },
    exchangeRateReader: ExchangeRateReader = object : ExchangeRateReader {
        override suspend fun currentRates() = null
    },

    retentionPreferenceStore: RetentionPreferenceStore = InMemoryRetentionPreferenceStore(),
    notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
    backgroundRefreshStatusReader: app.quotatrail.presentation.settings.SettingsBackgroundRefreshStatusReader =
        app.quotatrail.presentation.settings.InMemoryBackgroundRefreshStatusReader(),
    settingsDiagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
    quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
    appUpdateChecker: AppUpdateCheckUseCase = NoopAppUpdateCheckUseCase,
    appUpdateDownloader: AppUpdateDownloadUseCase = NoopAppUpdateDownloadUseCase,
    apiKeyLoginUseCase: SessionLoginUseCase? = null,
    notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
        NotificationWindowChoicesLoader { _, _ -> emptyList() },
    currencyPreferenceStore: CurrencyPreferenceStore = NoopCurrencyPreferenceStore,
    appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
    updatePreferenceStore: UpdatePreferenceStore = NoopUpdatePreferenceStore,
    launchDestinationValue: String? = null,
) {
    val tabs = QuotaTrailDestination.bottomTabs
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: QuotaTrailDestination.Home.route

    // The provider picker is a bottom sheet that unfolds from the tab bar; hoisted here so the sheet
    // and the tab bar are siblings in the root Box (the bar must paint on top of the sheet).
    var providerSheetVisible by remember { mutableStateOf(false) }

    fun navigateToAddAccount(entryMode: AddAccountEntryMode) {
        navController.navigate(QuotaTrailDestination.AddAccount.routeFor(entryMode))
    }

    fun navigateToProviderSelection() {
        providerSheetVisible = true
    }

    // After a successful add, land on the Account tab and clear the add-account flow off the stack.
    fun navigateToAccountAfterSave() {
        navController.navigate(QuotaTrailDestination.Account.route) {
            popUpTo(QuotaTrailDestination.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun navigateBasedOnAuthType(providerId: app.quotatrail.domain.model.ProviderId) {
        val config = app.quotatrail.providers.ProviderRegistry.configFor(providerId)
        val nextMode = when (config.authKind) {
            app.quotatrail.providers.ProviderAuthKind.OAuthWebView ->
                AddAccountEntryMode.LoginToCodex
            app.quotatrail.providers.ProviderAuthKind.ApiKeyImport ->
                AddAccountEntryMode.ApiKeyInput(providerId)
            app.quotatrail.providers.ProviderAuthKind.CookieAuth ->
                AddAccountEntryMode.WebViewCookieAuth(providerId)
            app.quotatrail.providers.ProviderAuthKind.OAuthPkceLogin ->
                AddAccountEntryMode.WebViewOAuthPkce(providerId)
        }
        navigateToAddAccount(nextMode)
    }

    // Re-login goes straight to the account's own provider sign-in (no provider picker), carrying the
    // target account so the new credential rebinds in place instead of creating a duplicate.
    fun navigateToRelogin(
        providerId: app.quotatrail.domain.model.ProviderId,
        localAccountId: app.quotatrail.domain.model.LocalAccountId,
        providerAccountId: String?,
    ) {
        val config = app.quotatrail.providers.ProviderRegistry.configFor(providerId)
        val nextMode = when (config.authKind) {
            app.quotatrail.providers.ProviderAuthKind.OAuthWebView ->
                AddAccountEntryMode.CodexRelogin(providerAccountId)
            app.quotatrail.providers.ProviderAuthKind.ApiKeyImport ->
                AddAccountEntryMode.ApiKeyInput(providerId, reloginAccountId = localAccountId)
            app.quotatrail.providers.ProviderAuthKind.CookieAuth ->
                AddAccountEntryMode.WebViewCookieAuth(providerId, reloginAccountId = localAccountId)
            app.quotatrail.providers.ProviderAuthKind.OAuthPkceLogin ->
                AddAccountEntryMode.WebViewOAuthPkce(providerId, reloginAccountId = localAccountId)
        }
        navigateToAddAccount(nextMode)
    }

    Box(modifier = modifier.fillMaxSize()) {
        QuotaTrailBackdrop(modifier = Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) { innerPadding ->
        @Composable
        fun AddAccountDestination(entryMode: AddAccountEntryMode) {
            when (entryMode) {
                is AddAccountEntryMode.ProviderSelection, AddAccountEntryMode.Choose -> {
                    QuotaTrailDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        ProviderSelectionScreen(
                            onProviderSelected = { providerId ->
                                navigateBasedOnAuthType(providerId)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                is AddAccountEntryMode.LoginToCodex, is AddAccountEntryMode.CodexRelogin -> {
                    QuotaTrailDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        AddAccountRoute(
                            entryMode = entryMode,
                            deviceCodeLoginController = deviceCodeLoginController,
                            deviceCodeLoginNotifier = deviceCodeLoginNotifier,
                            onBackClick = { navController.popBackStack() },
                            onLoginSaved = { navigateToAccountAfterSave() },
                        )
                    }
                }
                is AddAccountEntryMode.ApiKeyInput -> {
                    QuotaTrailDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val useCase = apiKeyLoginUseCase
                        if (useCase != null) {
                            // Region options for providers whose balance/usage API differs by platform.
                            // Endpoint URLs are pending owner verification; persisted per account.
                            val regions = remember(entryMode.providerId) {
                                when (entryMode.providerId.value) {
                                    "zai" -> listOf(
                                        ApiKeyAuthRegion("🇨🇳 China region (bigmodel.cn)", "https://open.bigmodel.cn"),
                                        ApiKeyAuthRegion("🌍 International region (z.ai)", "https://api.z.ai"),
                                    )
                                    "minimax" -> listOf(
                                        ApiKeyAuthRegion("🌍 International region (api.minimax.io)", "https://api.minimax.io"),
                                        ApiKeyAuthRegion("🇨🇳 China region (api.minimaxi.com)", "https://api.minimaxi.com"),
                                    )
                                    else -> emptyList()
                                }
                            }
                            ApiKeyAuthScreen(
                                providerId = entryMode.providerId,
                                regions = regions,
                                onImportApiKey = { apiKey, label, apiBaseUrl ->
                                    val reloginAccountId = entryMode.reloginAccountId
                                    if (reloginAccountId != null) {
                                        useCase.reloginApiKey(
                                            localAccountId = reloginAccountId,
                                            apiKey = apiKey,
                                            apiBaseUrl = apiBaseUrl,
                                        ).map { }
                                    } else {
                                        useCase.importApiKey(
                                            providerId = entryMode.providerId,
                                            providerDisplayName = app.quotatrail.providers.ProviderRegistry
                                                .displayNameFor(entryMode.providerId),
                                            apiKey = apiKey,
                                            label = label,
                                            apiBaseUrl = apiBaseUrl,
                                        ).map { }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                                onSaved = { navigateToAccountAfterSave() },
                            )
                        } else {
                            StubAuthScreen(
                                title = "API Key — ${entryMode.providerId.value}",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                is AddAccountEntryMode.WebViewCookieAuth -> {
                    QuotaTrailDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val useCase = apiKeyLoginUseCase
                        if (useCase != null) {
                            val cookieConfig = remember(entryMode.providerId) {
                                when (entryMode.providerId.value) {
                                    "cursor" -> WebViewAuthConfig.Cookie(
                                        providerId = entryMode.providerId,
                                        loginUrl = "https://cursor.com",
                                        cookieDomain = "cursor.com",
                                        targetCookieNames = listOf("WorkosCursorSessionToken"),
                                    )
                                    // CodexBar uses a single www.kimi.com host (no region split);
                                    // the kimi-auth cookie is the JWT used for the billing API.
                                    // The coding console is where the kimi-auth session + billing API
                                    // live (www.kimi.com/ is just an SEO landing). kimi sets a guest
                                    // kimi-auth before login, so capture only on explicit "Done".
                                    "kimi" -> WebViewAuthConfig.Cookie(
                                        providerId = entryMode.providerId,
                                        loginUrl = "https://www.kimi.com/code/console",
                                        cookieDomain = "www.kimi.com",
                                        targetCookieNames = listOf("kimi-auth"),
                                        autoCapture = false,
                                        tipResId = R.string.auth_tip_kimi,
                                        // kimi's logged-out /code landing collapses to 0-height in a
                                        // WebView, but its login button opens a working modal — so open
                                        // it automatically (guarded so it won't re-trigger once shown).
                                        injectOnLoadJs = """
                                            if(!document.querySelector('input[type=tel]')){
                                              var t=[].slice.call(document.querySelectorAll('a,button,[role=button]'))
                                                .filter(function(e){return /登录|登入|log\s*in|sign\s*in/i.test(e.textContent||'')})[0];
                                              if(t)t.click();
                                            }
                                        """.trimIndent(),
                                    )
                                    else -> null
                                }
                            }
                            if (cookieConfig != null) {
                                WebViewAuthScreen(
                                    config = cookieConfig,
                                    onCredentialExtracted = { cookie, _ ->
                                        val reloginAccountId = entryMode.reloginAccountId
                                        if (reloginAccountId != null) {
                                            useCase.reloginCookie(
                                                localAccountId = reloginAccountId,
                                                cookieValue = cookie,
                                            ).map { }
                                        } else {
                                            useCase.importCookie(
                                                providerId = entryMode.providerId,
                                                providerDisplayName = app.quotatrail.providers.ProviderRegistry
                                                    .displayNameFor(entryMode.providerId),
                                                cookieValue = cookie,
                                            ).map { }
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navigateToAccountAfterSave() },
                                )
                            } else {
                                StubAuthScreen(
                                    title = "Web Login — ${entryMode.providerId.value}",
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        } else {
                            StubAuthScreen(
                                title = "Web Login — ${entryMode.providerId.value}",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                is AddAccountEntryMode.WebViewOAuthPkce -> {
                    QuotaTrailDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val useCase = apiKeyLoginUseCase
                        if (useCase != null) {
                            val pkceParams = remember {
                                val verifier = generateCodeVerifier()
                                val challenge = generateCodeChallenge(verifier)
                                val state = generateRandomState()
                                Triple(verifier, challenge, state)
                            }
                            val (codeVerifier, codeChallenge, state) = pkceParams

                            val pkceConfig = remember(entryMode.providerId) {
                                when (entryMode.providerId.value) {
                                    "claude" -> {
                                        val authUrl = app.quotatrail.providers.claude.auth.ClaudeOAuthConfig
                                            .authorizationUrl(codeChallenge = codeChallenge, state = state)
                                        WebViewAuthConfig.OAuthManualCode(
                                            providerId = entryMode.providerId,
                                            authorizationUrl = authUrl,
                                            redirectUri = app.quotatrail.providers.claude.auth.ClaudeOAuthConfig.REDIRECT_URI,
                                            expectedState = state,
                                        )
                                    }
                                    // The embedded WebView now passes Google's bot/UA checks, so we
                                    // intercept the loopback redirect in-app instead of bouncing to an
                                    // external browser (which never returned the callback). Google's
                                    // loopback clients accept any 127.0.0.1 port; nothing actually binds
                                    // it — the redirect is caught before the WebView loads it.
                                    "antigravity" -> {
                                        val redirectUri = "http://127.0.0.1:8089/callback"
                                        val authUrl = buildOAuthUrl(
                                            "https://accounts.google.com/o/oauth2/v2/auth",
                                            "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
                                            redirectUri,
                                            "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/userinfo.email",
                                            codeChallenge,
                                            state,
                                        ) + "&access_type=offline"
                                        WebViewAuthConfig.OAuthIntercept(
                                            providerId = entryMode.providerId,
                                            authorizationUrl = authUrl,
                                            redirectUriPrefix = redirectUri,
                                            expectedState = state,
                                        )
                                    }
                                    else -> null
                                }
                            }
                            if (pkceConfig != null) {
                                WebViewAuthScreen(
                                    config = pkceConfig,
                                    onCredentialExtracted = { code, redirectUri ->
                                        val reloginAccountId = entryMode.reloginAccountId
                                        if (reloginAccountId != null) {
                                            useCase.reloginOAuthPkce(
                                                localAccountId = reloginAccountId,
                                                code = code,
                                                verifier = codeVerifier,
                                                redirectUri = redirectUri.orEmpty(),
                                            ).map { }
                                        } else {
                                            useCase.importOAuthPkce(
                                                providerId = entryMode.providerId,
                                                providerDisplayName = app.quotatrail.providers.ProviderRegistry
                                                    .displayNameFor(entryMode.providerId),
                                                code = code,
                                                verifier = codeVerifier,
                                                redirectUri = redirectUri.orEmpty(),
                                            ).map { }
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navigateToAccountAfterSave() },
                                )
                            } else {
                                StubAuthScreen(
                                    title = "OAuth PKCE — ${entryMode.providerId.value}",
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        } else {
                            StubAuthScreen(
                                title = "OAuth PKCE — ${entryMode.providerId.value}",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(QuotaTrailDestination.Home.route) {
                QuotaTrailDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    HomeRoute(
                        currentQuotaStateLoader = homeCurrentQuotaStateLoader,
                        accountQuotaStatesLoader = homeAccountQuotaStatesLoader,
                        accountSelectionUseCase = homeAccountSelectionUseCase,
                        refreshUseCase = homeRefreshUseCase,
                        trendHistoryLoader = homeTrendHistoryLoader,
                        notificationPreferenceReader = notificationPreferenceStore,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                        onLoginClick = { navigateToProviderSelection() },
                    )
                }
            }
            composable(QuotaTrailDestination.Account.route) {
                QuotaTrailDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    AccountRoute(
                        accountListUseCase = accountListUseCase,
                        accountDeleteUseCase = accountDeleteUseCase,
                        accountSwitchUseCase = accountSwitchUseCase,
                        accountRenameUseCase = accountRenameUseCase,
                        notificationPreferenceStore = notificationPreferenceStore,
                        quotaAlertEvaluationRequester = accountQuotaAlertEvaluationRequester,
                        refreshAllUseCase = accountRefreshAllUseCase,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                        onLoginToCodexClick = { navigateToProviderSelection() },
                        onAddAccountClick = { navigateToProviderSelection() },
                        onReloginAccount = { providerId, localAccountId, providerAccountId ->
                            navigateToRelogin(providerId, localAccountId, providerAccountId)
                        },
                    )
                }
            }
            composable(QuotaTrailDestination.Settings.route) {
                val settingsContext = LocalContext.current
                QuotaTrailDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    SettingsRoute(
                        accountListUseCase = accountListUseCase,
                        retentionPreferenceStore = retentionPreferenceStore,
                        notificationPreferenceStore = notificationPreferenceStore,
                        backgroundRefreshStatusReader = backgroundRefreshStatusReader,
                        diagnosticsReader = settingsDiagnosticsReader,
                        quotaHistoryClearUseCase = quotaHistoryClearUseCase,
                        appUpdateChecker = appUpdateChecker,
                        appUpdateDownloader = appUpdateDownloader,
                        backgroundRefreshScheduler = { minutes ->
                            SyncWorkScheduler.from(settingsContext).applyIntervalMinutes(minutes)
                        },
                        notificationWindowChoicesLoader = notificationWindowChoicesLoader,
                        currencyPreferenceStore = currencyPreferenceStore,
                        appearancePreferenceStore = appearancePreferenceStore,
                        updatePreferenceStore = updatePreferenceStore,
                        updateCheckScheduler = { enabled ->
                            UpdateCheckWorkScheduler.from(settingsContext).setAutoCheckEnabled(enabled)
                        },
                        openUpdateDialogOnLaunch =
                            launchDestinationValue == QuotaTrailLaunchDestination.SettingsUpdate.value,
                    )
                }
            }
            composable(QuotaTrailDestination.AddAccount.route) {
                AddAccountDestination(entryMode = AddAccountEntryMode.Choose)
            }
            composable(
                route = QuotaTrailDestination.AddAccount.routeWithEntryMode,
                arguments = listOf(
                    navArgument(QuotaTrailDestination.AddAccount.ENTRY_MODE_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                AddAccountDestination(
                    entryMode = AddAccountEntryMode.fromRouteValue(
                        backStackEntry.arguments?.getString(QuotaTrailDestination.AddAccount.ENTRY_MODE_ARG),
                    ),
                )
            }
        }
        }
        // The per-provider login flow is a full-screen modal; hiding the tab bar there keeps its
        // actions and error text from being covered by the floating bar.
        val showBottomBar = tabs.any { it.route == currentRoute }
        if (showBottomBar) {
        QuotaTrailBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            tabs = tabs,
            currentRoute = currentRoute,
            onTabSelected = { tab ->
                // Don't leave the picker hovering over a freshly switched tab.
                providerSheetVisible = false
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
        }
        // Provider picker sheet — rendered LAST so it paints over the tab bar: the panel grows up
        // out of the bar's position and covers it while open. Dismisses before branching into the
        // per-provider full-screen login.
        ProviderSelectionSheet(
            visible = providerSheetVisible,
            onProviderSelected = { providerId ->
                providerSheetVisible = false
                navigateBasedOnAuthType(providerId)
            },
            onDismiss = { providerSheetVisible = false },
        )
    }
}

@Composable
private fun QuotaTrailDestination(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        QuotaTrailBackdrop(modifier = Modifier.fillMaxSize())
        QuotaTrailPageCascade(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun QuotaTrailBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<QuotaTrailDestination>,
    currentRoute: String,
    onTabSelected: (QuotaTrailDestination) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                top = TrailSpacing.sm,
                start = TrailSpacing.lg,
                end = TrailSpacing.lg,
                bottom = TrailSpacing.sm,
            ),
        contentAlignment = Alignment.Center,
    ) {
        InstrumentSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            level = InstrumentSurfaceLevel.Dock,
            contentPadding = PaddingValues(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    QuotaTrailBottomBarItem(
                        tab = tab,
                        selected = selected,
                        onClick = { if (!selected) onTabSelected(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuotaTrailBottomBarItem(
    tab: QuotaTrailDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val animatorsEnabled = rememberQuotaTrailAnimatorsEnabled()
    val tabInteractionSource = remember { MutableInteractionSource() }
    val tabPressIndication = if (TrailMotion.bottomTabPressIndicationEnabled()) {
        LocalIndication.current
    } else {
        null
    }
    val colorAnimationSpec = if (animatorsEnabled) {
        tween<Color>(
            durationMillis = TrailMotion.BottomTabDurationMillis,
            easing = FastOutSlowInEasing,
        )
    } else {
        snap()
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            QuotaTrailTheme.colors.accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorAnimationSpec,
        label = "bottom_tab_content_color",
    )
    val iconScale by animateFloatAsState(
        targetValue = TrailMotion.bottomTabScale(selected),
        animationSpec = if (animatorsEnabled) {
            tween(
                durationMillis = TrailMotion.BottomTabDurationMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "bottom_tab_icon_scale",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .height(60.dp)
            .clip(QuotaTrailShapes.sm)
            .selectable(
                selected = selected,
                interactionSource = tabInteractionSource,
                indication = tabPressIndication,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .clip(QuotaTrailShapes.pill)
                .background(if (selected) contentColor else Color.Transparent),
        )
        Icon(
            painter = painterResource(tab.iconResId),
            contentDescription = stringResource(tab.contentDescriptionResId),
            modifier = Modifier
                .padding(top = 5.dp)
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = contentColor,
        )
        Text(
            text = stringResource(tab.labelResId),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

private fun generateRandomState(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun buildOAuthUrl(
    endpoint: String,
    clientId: String,
    redirectUri: String,
    scope: String,
    codeChallenge: String,
    state: String,
): String =
    "$endpoint?" +
        "client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
        "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
        "&response_type=code" +
        "&scope=${java.net.URLEncoder.encode(scope, "UTF-8")}" +
        "&state=$state" +
        "&code_challenge=$codeChallenge" +
        "&code_challenge_method=S256"
