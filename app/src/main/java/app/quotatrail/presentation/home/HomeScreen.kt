package app.quotatrail.presentation.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quotatrail.R
import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.presentation.components.QuotaPullToRefreshIndicator
import app.quotatrail.presentation.motion.rememberQuotaTrailAnimatorsEnabled
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.QuotaTrailTypography
import app.quotatrail.presentation.theme.avatarColor
import app.quotatrail.presentation.theme.avatarInitialColor
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    currentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    accountQuotaStatesLoader: HomeAccountQuotaStatesLoader? = null,
    accountSelectionUseCase: HomeAccountSelectionUseCase = NoopHomeAccountSelectionUseCase,
    refreshUseCase: HomeRefreshUseCase,
    trendHistoryLoader: HomeTrendHistoryLoader = HomeTrendHistoryLoader { _, _ -> emptyList() },
    notificationPreferenceReader: NotificationPreferenceReader,
    currencyPreferenceReader: CurrencyPreferenceReader,
    exchangeRateReader: ExchangeRateReader,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            currentQuotaStateLoader = currentQuotaStateLoader,
            accountQuotaStatesLoader = accountQuotaStatesLoader,
            accountSelectionUseCase = accountSelectionUseCase,
            refreshUseCase = refreshUseCase,
            trendHistoryLoader = trendHistoryLoader,
            notificationPreferenceReader = notificationPreferenceReader,
            currencyPreferenceReader = currencyPreferenceReader,
            exchangeRateReader = exchangeRateReader,
        ),
    ),
    onLoginClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerUiState by viewModel.pagerUiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.loadCurrentState()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadCurrentState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HomeScreen(
        uiState = uiState,
        pagerUiState = pagerUiState,
        modifier = modifier,
        onRefreshClick = viewModel::refreshNow,
        onAccountPageSelected = viewModel::selectAccountPage,
        onLoginClick = onLoginClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    pagerUiState: HomePagerUiState = HomePagerUiState.Empty,
    modifier: Modifier = Modifier,
    onRefreshClick: () -> Unit = {},
    onAccountPageSelected: (Int) -> Unit = {},
    onLoginClick: () -> Unit = {},
) {
    // Pull-to-refresh refreshes only the visible account page (the Account screen refreshes all).
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefreshClick,
        modifier = modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            QuotaPullToRefreshIndicator(
                isRefreshing = uiState.isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        val pageKeys = pagerUiState.pages.map { it.providerId.value to it.localAccountId.value }
        key(pageKeys) {
            val pageCount = pagerUiState.pages.size.coerceAtLeast(1)
            val initialPage = pagerUiState.selectedPageIndex.coerceIn(0, pageCount - 1)
            val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

            LaunchedEffect(pagerState, pageKeys) {
                snapshotFlow { pagerState.settledPage }
                    .distinctUntilChanged()
                    .collect(onAccountPageSelected)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { index -> pageKeys.getOrNull(index) ?: "home-empty" },
            ) { pageIndex ->
                val page = pagerUiState.pages.getOrNull(pageIndex)
                val pageUiState = page?.content ?: uiState
                HomeDashboardPage(
                    uiState = pageUiState,
                    providerName = page?.providerName,
                    pages = pagerUiState.pages,
                    pageIndex = pageIndex,
                    onRefreshClick = onRefreshClick,
                    onLoginClick = onLoginClick,
                )
            }
        }
    }
}

@Composable
private fun HomeDashboardPage(
    uiState: HomeUiState,
    providerName: String?,
    pages: List<HomeAccountPageUi>,
    pageIndex: Int,
    onRefreshClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.lg),
    ) {
        HomeHeader(
            uiState = uiState,
            providerName = providerName,
            onRefreshClick = onRefreshClick,
        )
        if (pages.size > 1) {
            HomeAccountPageIndicator(
                pages = pages,
                selectedPageIndex = pageIndex,
            )
        }

        if (uiState.contentStatus == HomeContentStatus.Unauthenticated) {
            HomeActionCard(
                uiState = uiState,
                onLoginClick = onLoginClick,
            )
        } else {
            if (uiState.quotaCards.isNotEmpty()) {
                HomeQuotaCards(
                    quotaCards = uiState.quotaCards,
                )
                HomeAccountTrailBand(uiState = uiState)
                HomeTrendCard(trend = uiState.trend)
            } else {
                uiState.loading?.let { loading ->
                    HomeLoadingCard(loading = loading)
                }
            }
            HomeRefreshCard(
                uiState = uiState,
            )
            if (uiState.primaryAction != null || uiState.secondaryAction != null) {
                HomeActionCard(
                    uiState = uiState,
                    onLoginClick = onLoginClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(TrailSpacing.bottomNavigationClearance))
    }
}

@Composable
private fun HomeAccountPageIndicator(
    pages: List<HomeAccountPageUi>,
    selectedPageIndex: Int,
) {
    val selectedPage = pages.getOrNull(selectedPageIndex) ?: return
    val pageAnnouncement = stringResource(
        R.string.home_account_page_position,
        selectedPage.providerName,
        selectedPageIndex + 1,
        pages.size,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = pageAnnouncement },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.home_page_indicator_format,
                selectedPageIndex + 1,
                pages.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == selectedPageIndex) 9.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == selectedPageIndex) {
                                QuotaTrailTheme.colors.accent
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    uiState: HomeUiState,
    providerName: String?,
    onRefreshClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            uiState.account?.let { HomeProviderMark(it) }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
            ) {
                Text(
                    text = providerName ?: stringResource(uiState.titleResId),
                    style = QuotaTrailTypography.current.display,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = uiState.account?.displayName
                        ?: stringResource(uiState.statusDescriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.contentStatus != HomeContentStatus.Unauthenticated) {
                HomeRefreshIconButton(
                    isRefreshing = uiState.isRefreshing,
                    onRefreshClick = onRefreshClick,
                )
            }
        }
    }
}

@Composable
private fun HomeRefreshIconButton(
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
) {
    val animatorsEnabled = rememberQuotaTrailAnimatorsEnabled()
    val rotation = if (isRefreshing && animatorsEnabled) {
        val transition = rememberInfiniteTransition(label = "home_refresh_icon")
        val animatedRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
            ),
            label = "home_refresh_icon_rotation",
        )
        animatedRotation
    } else {
        0f
    }
    IconButton(
        onClick = onRefreshClick,
        enabled = !isRefreshing,
        modifier = Modifier
            .size(48.dp)
            .clip(QuotaTrailShapes.sm)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_action_refresh),
            contentDescription = stringResource(R.string.home_refresh),
            // Manual refresh is the primary header action; accent stays visible on both light and
            // dark backgrounds (the default content color could fall back to near-invisible in dark).
            tint = QuotaTrailTheme.colors.accent,
            modifier = Modifier.graphicsLayer {
                rotationZ = if (isRefreshing) rotation else 0f
            },
        )
    }
}

@Composable
private fun HomeProviderMark(account: HomeAccountUi) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(QuotaTrailShapes.instrument)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val iconResId = account.providerIconResId
        if (iconResId != null) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = QuotaTrailTheme.colors.primary,
                modifier = Modifier.size(27.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(avatarColor(account.avatarColorKey)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = account.avatarInitial,
                    style = MaterialTheme.typography.labelLarge,
                    color = avatarInitialColor(),
                    maxLines = 1,
                )
            }
        }
    }
}
