package app.quotatrail.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.quotatrail.R
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.providers.ProviderRegistry
import app.quotatrail.presentation.quota.quotaWindowLabelRes
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.quota.Credits
import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.currency.CurrencyPreferences
import app.quotatrail.domain.currency.ExchangeRates
import app.quotatrail.domain.currency.withConvertedBalance
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.presentation.providerPlanDisplayName
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface HomeCurrentQuotaStateLoader {
    suspend fun loadCurrentState(): CurrentQuotaState
}

fun interface HomeRefreshUseCase {
    suspend fun refreshCurrentState(): CurrentQuotaState
}

data class HomeTrendQuery(
    val windowId: String,
    val displayKind: QuotaWindowDisplayKind,
    val useModelBucketSum: Boolean,
    val metric: HomeTrendMetric = HomeTrendMetric.Consumption,
)

enum class HomeTrendMetric {
    Consumption,
    RemainingPercent,
}

fun interface HomeTrendHistoryLoader {
    suspend fun loadTrend(accountId: LocalAccountId?, query: HomeTrendQuery): List<HomeTrendPointUi>
}

private object NoopHomeCurrentQuotaStateLoader : HomeCurrentQuotaStateLoader {
    override suspend fun loadCurrentState(): CurrentQuotaState =
        app.quotatrail.domain.quota.CurrentQuotaStateFactory().create(
            account = null,
            latestSnapshot = null,
            latestAttempt = null,
            now = Instant.EPOCH,
        )
}

private object NoopHomeRefreshUseCase : HomeRefreshUseCase {
    override suspend fun refreshCurrentState(): CurrentQuotaState =
        NoopHomeCurrentQuotaStateLoader.loadCurrentState()
}

private object NoopHomeTrendHistoryLoader : HomeTrendHistoryLoader {
    override suspend fun loadTrend(accountId: LocalAccountId?, query: HomeTrendQuery): List<HomeTrendPointUi> = emptyList()
}

private object DefaultHomeNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

private object DefaultHomeCurrencyPreferenceReader : CurrencyPreferenceReader {
    override suspend fun currencyPreferences(): CurrencyPreferences = CurrencyPreferences()
}

private object DefaultHomeExchangeRateReader : ExchangeRateReader {
    override suspend fun currentRates(): ExchangeRates? = null
}

class HomeViewModel(
    private val currentQuotaStateLoader: HomeCurrentQuotaStateLoader = NoopHomeCurrentQuotaStateLoader,
    private val accountQuotaStatesLoader: HomeAccountQuotaStatesLoader? = null,
    private val accountSelectionUseCase: HomeAccountSelectionUseCase = NoopHomeAccountSelectionUseCase,
    private val refreshUseCase: HomeRefreshUseCase = NoopHomeRefreshUseCase,
    private val trendHistoryLoader: HomeTrendHistoryLoader = NoopHomeTrendHistoryLoader,
    private val notificationPreferenceReader: NotificationPreferenceReader = DefaultHomeNotificationPreferenceReader,
    private val currencyPreferenceReader: CurrencyPreferenceReader = DefaultHomeCurrencyPreferenceReader,
    private val exchangeRateReader: ExchangeRateReader = DefaultHomeExchangeRateReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState.loading())
    private val _pagerUiState = MutableStateFlow(HomePagerUiState.Empty)
    private var accountStates: List<CurrentQuotaState> = emptyList()
    private var currentTrendAccountId: LocalAccountId? = null
    private var currentTrendQuery: HomeTrendQuery? = null
    private var currentComparisonTrendQuery: HomeTrendQuery? = null
    private var notificationPreferences = NotificationPreferences()
    private var currencyPreferences: CurrencyPreferences = CurrencyPreferences()
    private var exchangeRates: ExchangeRates? = null
    private val trendPointsByAccount = mutableMapOf<LocalAccountId?, List<HomeTrendPointUi>>()
    private val comparisonTrendPointsByAccount = mutableMapOf<LocalAccountId?, List<HomeTrendPointUi>>()
    private var currentStateLoadJob: Job? = null
    private var pageSelectionJob: Job? = null
    private var trendLoadJob: Job? = null
    private var manualRefreshSuccessCount = 0

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val pagerUiState: StateFlow<HomePagerUiState> = _pagerUiState.asStateFlow()

    fun updateCurrentQuotaState(state: CurrentQuotaState, isRefreshing: Boolean = false) {
        val accountId = state.account?.localAccountId
        val pageIndex = accountStates.indexOfFirst { it.account?.localAccountId == accountId }
        if (accountId != null && pageIndex >= 0) {
            accountStates = accountStates.toMutableList().also { it[pageIndex] = state }
            clearTrendCacheFor(accountId)
            val content = mapToUiState(state = state, isRefreshing = isRefreshing)
            _pagerUiState.value = _pagerUiState.value.copy(
                pages = _pagerUiState.value.pages.mapIndexed { index, page ->
                    if (index == pageIndex) page.copy(content = content) else page
                },
            )
            if (_pagerUiState.value.selectedPageIndex == pageIndex) {
                activatePage(pageIndex = pageIndex)
            }
            return
        }
        currentTrendAccountId = state.account?.localAccountId
        currentTrendQuery = state.trendWindow()?.toTrendQuery()
        currentComparisonTrendQuery = state.comparisonTrendWindow()?.toTrendQuery()
        clearTrendCacheFor(currentTrendAccountId)
        _uiState.value = mapToUiState(state = state, isRefreshing = isRefreshing)
        if (state.account != null && currentTrendQuery != null) {
            loadTrend()
        }
    }

    /**
     * Loads the latest persisted snapshot for display. Deliberately does NOT hit the network: opening
     * Home (and every ON_RESUME / tab re-entry) must not trigger an API refresh, which previously
     * caused frequent calls when switching pages. Freshness comes from manual pull-to-refresh and the
     * periodic background worker; this only reflects whatever those have already written.
     */
    fun loadCurrentState() {
        if (currentStateLoadJob?.isActive == true) {
            return
        }
        currentStateLoadJob = viewModelScope.launch {
            loadNotificationPreferences()
            val persistedPages = loadPersistedAccountStatesOrNull()
            if (persistedPages != null) {
                publishAccountPages(persistedPages)
                return@launch
            }
            val persistedState = loadPersistedCurrentStateOrNull()
            if (persistedState != null) {
                updateCurrentQuotaState(state = persistedState, isRefreshing = false)
            } else {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun refreshNow() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        updateSelectedPagerContent(_uiState.value)
        viewModelScope.launch {
            pageSelectionJob?.join()
            loadNotificationPreferences()
            val refreshedState = refreshUseCase.refreshCurrentState()
            if (refreshedState.status.isRefreshSweepSuccess()) {
                manualRefreshSuccessCount += 1
            }
            updateCurrentQuotaState(refreshedState)
        }
    }

    fun selectAccountPage(pageIndex: Int) {
        val page = _pagerUiState.value.pages.getOrNull(pageIndex) ?: return
        if (_pagerUiState.value.selectedPageIndex == pageIndex) return

        activatePage(pageIndex = pageIndex)
        pageSelectionJob?.cancel()
        pageSelectionJob = viewModelScope.launch {
            accountSelectionUseCase.selectAccount(
                providerId = page.providerId,
                localAccountId = page.localAccountId,
            )
        }
    }

    private suspend fun loadNotificationPreferences() {
        runCatching {
            notificationPreferenceReader.notificationPreferences()
        }.onSuccess { preferences ->
            notificationPreferences = preferences
        }
        currencyPreferences = currencyPreferenceReader.currencyPreferences()
        exchangeRates = exchangeRateReader.currentRates()
    }

    /** Test-only: populate preference fields via injected readers without triggering a full state load. */
    internal suspend fun loadPreferencesForTest() {
        notificationPreferences = notificationPreferenceReader.notificationPreferences()
        currencyPreferences = currencyPreferenceReader.currencyPreferences()
        exchangeRates = exchangeRateReader.currentRates()
    }

    private suspend fun loadPersistedCurrentStateOrNull(): CurrentQuotaState? =
        try {
            currentQuotaStateLoader.loadCurrentState()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private suspend fun loadPersistedAccountStatesOrNull(): HomeAccountQuotaStates? {
        val loader = accountQuotaStatesLoader ?: return null
        return try {
            loader.loadAccountStates()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private fun publishAccountPages(result: HomeAccountQuotaStates) {
        accountStates = result.states.filter { it.account != null }
        if (accountStates.isEmpty()) {
            _pagerUiState.value = HomePagerUiState.Empty
            _uiState.value = HomeUiState.unauthenticated()
            return
        }

        accountStates.forEach { clearTrendCacheFor(it.account?.localAccountId) }
        val pages = accountStates.map { state ->
            val account = requireNotNull(state.account)
            HomeAccountPageUi(
                providerId = account.providerId,
                localAccountId = account.localAccountId,
                providerName = ProviderRegistry.displayNameFor(account.providerId),
                content = mapToUiState(state),
            )
        }
        val selectedPageIndex = pages.indexOfFirst { it.localAccountId == result.selectedAccountId }
            .takeIf { it >= 0 }
            ?: 0
        _pagerUiState.value = HomePagerUiState(
            pages = pages,
            selectedPageIndex = selectedPageIndex,
        )
        activatePage(pageIndex = selectedPageIndex)
    }

    private fun activatePage(pageIndex: Int) {
        val page = _pagerUiState.value.pages.getOrNull(pageIndex) ?: return
        val state = accountStates.getOrNull(pageIndex) ?: return
        _pagerUiState.value = _pagerUiState.value.copy(selectedPageIndex = pageIndex)
        _uiState.value = page.content
        currentTrendAccountId = page.localAccountId
        currentTrendQuery = state.trendWindow()?.toTrendQuery()
        currentComparisonTrendQuery = state.comparisonTrendWindow()?.toTrendQuery()
        if (currentTrendQuery != null) {
            loadTrend()
        }
    }

    private fun updateSelectedPagerContent(content: HomeUiState) {
        val selectedIndex = _pagerUiState.value.selectedPageIndex
        if (_pagerUiState.value.pages.getOrNull(selectedIndex) == null) return
        _pagerUiState.value = _pagerUiState.value.copy(
            pages = _pagerUiState.value.pages.mapIndexed { index, page ->
                if (index == selectedIndex) page.copy(content = content) else page
            },
        )
    }

    fun mapToUiState(
        state: CurrentQuotaState,
        isRefreshing: Boolean = false,
    ): HomeUiState {
        if (state.status == CurrentQuotaStatus.Unauthenticated) {
            return HomeUiState.unauthenticated(isRefreshing = isRefreshing)
        }

        val contentStatus = state.status.toHomeStatus()
        val statusTitleResId = state.statusTitleResId()
        val statusDescriptionResId = state.statusDescriptionResId()
        val allWindows = state.allWindows()
        val quotaCards = allWindows
            .filterNot {
                it.availability == QuotaWindowAvailability.Missing ||
                    it.availability == QuotaWindowAvailability.Unsupported
            }
            .map { it.withConvertedBalance(currencyPreferences.targetCurrency, exchangeRates) }
            .map { window -> window.toQuotaCard() }
        val effectiveRefreshing = isRefreshing || state.status == CurrentQuotaStatus.Loading

        return HomeUiState(
            contentStatus = contentStatus,
            statusTitleResId = statusTitleResId,
            statusDescriptionResId = statusDescriptionResId,
            errorMessageResId = state.error?.safeMessageKey?.toErrorMessageResId(),
            account = state.account?.let {
                HomeAccountUi(
                    displayName = it.displayName,
                    avatarInitial = it.avatarInitial,
                    avatarColorKey = it.avatarColorKey,
                    planType = providerPlanDisplayName(it.providerId, state.snapshot?.planType),
                    credits = state.snapshot?.credits.toHomeCredits(),
                    providerIconResId = ProviderRegistry.iconFor(it.providerId),
                )
            },
            quotaCards = quotaCards,
            trend = HomeTrendUi(
                titleResId = if (state.trendWindow()?.isOverallSevenDayWindow() == true) {
                    R.string.home_trend_weekly_title
                } else {
                    R.string.home_trend_placeholder_title
                },
                descriptionResId = if (state.trendWindow()?.isOverallSevenDayWindow() == true) {
                    R.string.home_trend_weekly_description
                } else {
                    R.string.home_trend_placeholder_description
                },
                metricLabelResId = state.trendWindow()?.trendMetricLabelResId()
                    ?: R.string.home_trend_metric_usage,
                displaysRemainingPercent = state.trendWindow()?.isOverallSevenDayWindow() == true,
                points = trendPointsByAccount[state.account?.localAccountId].orEmpty(),
                comparisonPoints = comparisonTrendPointsByAccount[state.account?.localAccountId].orEmpty(),
            ),
            loading = if (
                state.status == CurrentQuotaStatus.Loading &&
                quotaCards.isEmpty()
            ) {
                HomeLoadingUi(
                    titleResId = R.string.home_loading_card_title,
                    descriptionResId = R.string.home_loading_card_description,
                )
            } else {
                null
            },
            refresh = HomeRefreshUi(
                titleResId = if (effectiveRefreshing) R.string.home_state_loading_title else statusTitleResId,
                descriptionResId = if (effectiveRefreshing) {
                    R.string.home_state_loading_description
                } else {
                    statusDescriptionResId
                },
                buttonTextResId = if (effectiveRefreshing) R.string.home_refreshing else R.string.home_refresh,
                lastSuccessfulRefreshAt = state.snapshot?.fetchedAt ?: state.account?.lastSuccessfulRefreshAt,
                lastAttemptFinishedAt = state.latestAttempt?.finishedAt,
            ),
            primaryAction = state.primaryAction(),
            secondaryAction = state.secondaryAction(),
            isRefreshing = effectiveRefreshing,
            manualRefreshSuccessCount = manualRefreshSuccessCount,
        )
    }

    private fun CurrentQuotaStatus.toHomeStatus(): HomeContentStatus =
        when (this) {
            CurrentQuotaStatus.Unauthenticated -> HomeContentStatus.Unauthenticated
            CurrentQuotaStatus.Loading -> HomeContentStatus.Loading
            CurrentQuotaStatus.Fresh -> HomeContentStatus.Fresh
            CurrentQuotaStatus.PossiblyStale -> HomeContentStatus.PossiblyStale
            CurrentQuotaStatus.Expired -> HomeContentStatus.Expired
            CurrentQuotaStatus.AuthRequired -> HomeContentStatus.AuthRequired
            CurrentQuotaStatus.ErrorWithLastKnownGood -> HomeContentStatus.ErrorWithLastKnownGood
            CurrentQuotaStatus.NoData -> HomeContentStatus.NoData
        }

    private fun CurrentQuotaStatus.isRefreshSweepSuccess(): Boolean =
        this == CurrentQuotaStatus.Fresh || this == CurrentQuotaStatus.PossiblyStale

    private fun CurrentQuotaState.statusTitleResId(): Int =
        when (status) {
            CurrentQuotaStatus.Unauthenticated -> R.string.home_state_unauthenticated_title
            CurrentQuotaStatus.Loading -> R.string.home_state_loading_title
            CurrentQuotaStatus.Fresh -> R.string.home_state_fresh_title
            CurrentQuotaStatus.PossiblyStale -> R.string.home_state_possibly_stale_title
            CurrentQuotaStatus.Expired -> R.string.home_state_expired_title
            CurrentQuotaStatus.AuthRequired -> R.string.home_state_auth_required_title
            CurrentQuotaStatus.ErrorWithLastKnownGood -> R.string.home_state_error_lkg_title
            CurrentQuotaStatus.NoData -> R.string.home_state_no_data_title
        }

    private fun CurrentQuotaState.statusDescriptionResId(): Int =
        when (status) {
            CurrentQuotaStatus.Unauthenticated -> R.string.home_state_unauthenticated_description
            CurrentQuotaStatus.Loading -> R.string.home_state_loading_description
            CurrentQuotaStatus.Fresh -> R.string.home_state_fresh_description
            CurrentQuotaStatus.PossiblyStale -> R.string.home_state_possibly_stale_description
            CurrentQuotaStatus.Expired -> R.string.home_state_expired_description
            CurrentQuotaStatus.AuthRequired -> R.string.home_state_auth_required_description
            CurrentQuotaStatus.ErrorWithLastKnownGood -> R.string.home_state_error_lkg_description
            CurrentQuotaStatus.NoData -> R.string.home_state_no_data_description
        }

    private fun CurrentQuotaState.primaryAction(): HomeActionUi? =
        when (status) {
            CurrentQuotaStatus.Unauthenticated,
            CurrentQuotaStatus.AuthRequired -> HomeActionUi(
                kind = HomeActionKind.LoginToCodex,
                labelResId = R.string.home_login_to_codex,
            )
            else -> null
        }

    private fun CurrentQuotaState.secondaryAction(): HomeActionUi? = null

    private fun CurrentQuotaState.allWindows(): List<QuotaWindow> =
        (listOfNotNull(primaryWindow) + secondaryWindows).distinctBy { it.windowId.value }

    private fun loadTrend() {
        val accountId = currentTrendAccountId
        val query = currentTrendQuery ?: return
        val comparisonQuery = currentComparisonTrendQuery
        trendLoadJob?.cancel()
        trendLoadJob = viewModelScope.launch {
            val points = try {
                trendHistoryLoader.loadTrend(accountId = accountId, query = query)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                emptyList()
            }
            val comparisonPoints = if (comparisonQuery == null) {
                emptyList()
            } else {
                try {
                    trendHistoryLoader.loadTrend(accountId = accountId, query = comparisonQuery)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    emptyList()
                }
            }
            trendPointsByAccount[accountId] = points
            comparisonTrendPointsByAccount[accountId] = comparisonPoints
            if (currentTrendAccountId == accountId) {
                val updatedContent = _uiState.value.copy(
                    trend = _uiState.value.trend.copy(
                        points = points,
                        comparisonPoints = comparisonPoints,
                    ),
                )
                _uiState.value = updatedContent
                updateSelectedPagerContent(updatedContent)
            }
        }
    }

    private fun clearTrendCacheFor(accountId: LocalAccountId?) {
        trendPointsByAccount.remove(accountId)
        comparisonTrendPointsByAccount.remove(accountId)
    }

    // Prefer the provider's overall seven-day limit for the requested 72-hour remaining chart.
    // Providers without that window retain the existing consumption trend as a graceful fallback.
    private fun CurrentQuotaState.trendWindow(): QuotaWindow? =
        allWindows().firstOrNull { it.isOverallSevenDayWindow() }
            ?: primaryWindow
            ?: secondaryWindows.firstOrNull { it.isPrimaryCandidate }
            ?: secondaryWindows.firstOrNull()

    private fun CurrentQuotaState.comparisonTrendWindow(): QuotaWindow? {
        val primaryTrendWindow = trendWindow()?.takeIf { it.isOverallSevenDayWindow() } ?: return null
        return allWindows().firstOrNull { it.windowId.value in OVERALL_FIVE_HOUR_WINDOW_IDS }
            ?: allWindows().firstOrNull {
                it.windowId != primaryTrendWindow.windowId && it.limitWindowSeconds == FIVE_HOUR_SECONDS
            }
    }

    private fun QuotaWindow.toTrendQuery(): HomeTrendQuery =
        HomeTrendQuery(
            windowId = windowId.value,
            displayKind = displayKind,
            useModelBucketSum = usesModelBucketSum,
            metric = if (isOverallSevenDayWindow() || isOverallFiveHourWindow()) {
                HomeTrendMetric.RemainingPercent
            } else {
                HomeTrendMetric.Consumption
            },
        )

    private fun QuotaWindow.trendMetricLabelResId(): Int =
        if (isOverallSevenDayWindow()) {
            R.string.home_trend_metric_remaining
        } else {
            displayKind.metricLabelResId()
        }

    private fun QuotaWindow.isOverallSevenDayWindow(): Boolean =
        windowId.value in OVERALL_SEVEN_DAY_WINDOW_IDS ||
            (limitWindowSeconds == SEVEN_DAY_SECONDS && windowId.value !in MODEL_SPECIFIC_SEVEN_DAY_WINDOW_IDS)

    private fun QuotaWindow.isOverallFiveHourWindow(): Boolean =
        windowId.value in OVERALL_FIVE_HOUR_WINDOW_IDS || limitWindowSeconds == FIVE_HOUR_SECONDS

    private fun QuotaWindowDisplayKind?.metricLabelResId(): Int =
        when (this) {
            QuotaWindowDisplayKind.Balance -> R.string.home_trend_metric_spend
            QuotaWindowDisplayKind.UsageCount -> R.string.home_trend_metric_calls
            else -> R.string.home_trend_metric_usage
        }

    private fun QuotaWindow.toQuotaCard(): HomeQuotaCardUi {
        val quotaStatus = toQuotaStatus()
        return HomeQuotaCardUi(
            windowId = windowId.value,
            titleResId = quotaWindowLabelRes(windowId.value),
            displayKind = displayKind,
            usedPercent = usedPercent,
            balanceAmount = balanceAmount,
            balanceCurrency = balanceCurrency,
            usedCount = usedCount,
            limitCount = limitCount,
            subLabel = subLabel,
            isPrimary = isPrimaryCandidate,
            resetAt = resetAt,
            status = quotaStatus,
            tone = quotaStatus.toTone(),
            statusLabelResId = quotaStatus.labelResId(),
            originalBalanceAmount = originalBalanceAmount,
            originalBalanceCurrency = originalBalanceCurrency,
            grantedBalance = grantedBalance,
            toppedUpBalance = toppedUpBalance,
        )
    }

    private fun QuotaWindow.toQuotaStatus(): HomeQuotaStatus {
        if (availability == QuotaWindowAvailability.Depleted) return HomeQuotaStatus.Exhausted
        if (availability != QuotaWindowAvailability.Available) return HomeQuotaStatus.Unavailable
        if (displayKind == QuotaWindowDisplayKind.Balance) {
            val amount = balanceAmount?.toDoubleOrNull() ?: return HomeQuotaStatus.Unavailable
            return when {
                amount <= 0.0 -> HomeQuotaStatus.Exhausted
                amount <= notificationPreferences.balanceWarningThreshold -> HomeQuotaStatus.Warning
                amount <= notificationPreferences.balanceCautionThreshold -> HomeQuotaStatus.Caution
                else -> HomeQuotaStatus.Normal
            }
        }
        val percent = displayPercent ?: return HomeQuotaStatus.Unavailable
        return when {
            percent <= notificationPreferences.limitThreshold -> HomeQuotaStatus.Exhausted
            percent <= notificationPreferences.warningThreshold -> HomeQuotaStatus.Warning
            percent <= notificationPreferences.cautionThreshold -> HomeQuotaStatus.Caution
            else -> HomeQuotaStatus.Normal
        }
    }

    internal fun testQuotaStatusFor(window: QuotaWindow): HomeQuotaStatus =
        window.withConvertedBalance(currencyPreferences.targetCurrency, exchangeRates).toQuotaStatus()

    private fun Credits?.toHomeCredits(): HomeCreditsUi =
        when {
            this == null || !hasCredits -> HomeCreditsUi.Unavailable
            unlimited -> HomeCreditsUi.Unlimited
            balance != null -> HomeCreditsUi.Balance(balance)
            else -> HomeCreditsUi.Unavailable
        }

    private fun HomeQuotaStatus.toTone(): HomeStatusTone =
        when (this) {
            HomeQuotaStatus.Normal -> HomeStatusTone.Success
            HomeQuotaStatus.Caution -> HomeStatusTone.Warning
            HomeQuotaStatus.Warning -> HomeStatusTone.Danger
            HomeQuotaStatus.Exhausted -> HomeStatusTone.Danger
            HomeQuotaStatus.Unavailable -> HomeStatusTone.Neutral
        }

    private fun HomeQuotaStatus.labelResId(): Int =
        when (this) {
            HomeQuotaStatus.Normal -> R.string.home_quota_status_normal
            HomeQuotaStatus.Caution -> R.string.home_quota_status_caution
            HomeQuotaStatus.Warning -> R.string.home_quota_status_warning
            HomeQuotaStatus.Exhausted -> R.string.home_quota_status_exhausted
            HomeQuotaStatus.Unavailable -> R.string.home_quota_status_unavailable
        }

    private fun String.toErrorMessageResId(): Int =
        when (this) {
            "error_auth_required" -> R.string.error_auth_required
            "error_network" -> R.string.error_network
            else -> R.string.error_unknown
        }

    companion object {
        private const val FIVE_HOUR_SECONDS = 5 * 60 * 60
        private const val SEVEN_DAY_SECONDS = 7 * 24 * 60 * 60
        private val OVERALL_FIVE_HOUR_WINDOW_IDS = setOf(
            "five_hour",
            "claude_5h_window",
            "zai_5h_window",
        )
        private val OVERALL_SEVEN_DAY_WINDOW_IDS = setOf(
            "weekly",
            "claude_7d_window",
            "zai_weekly_window",
            "kimi_weekly_window",
            "minimax_weekly",
        )
        private val MODEL_SPECIFIC_SEVEN_DAY_WINDOW_IDS = setOf(
            "claude_7d_opus_window",
            "claude_7d_sonnet_window",
        )

        fun factory(
            currentQuotaStateLoader: HomeCurrentQuotaStateLoader,
            accountQuotaStatesLoader: HomeAccountQuotaStatesLoader? = null,
            accountSelectionUseCase: HomeAccountSelectionUseCase = NoopHomeAccountSelectionUseCase,
            refreshUseCase: HomeRefreshUseCase,
            trendHistoryLoader: HomeTrendHistoryLoader = NoopHomeTrendHistoryLoader,
            notificationPreferenceReader: NotificationPreferenceReader = DefaultHomeNotificationPreferenceReader,
            currencyPreferenceReader: CurrencyPreferenceReader = DefaultHomeCurrencyPreferenceReader,
            exchangeRateReader: ExchangeRateReader = DefaultHomeExchangeRateReader,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(
                        currentQuotaStateLoader = currentQuotaStateLoader,
                        accountQuotaStatesLoader = accountQuotaStatesLoader,
                        accountSelectionUseCase = accountSelectionUseCase,
                        refreshUseCase = refreshUseCase,
                        trendHistoryLoader = trendHistoryLoader,
                        notificationPreferenceReader = notificationPreferenceReader,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                    ) as T
            }

    }
}
