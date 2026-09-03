package app.quotatrail.presentation.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.quotatrail.R
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.domain.theme.ThemeMode
import app.quotatrail.providers.ProviderRegistry
import app.quotatrail.presentation.components.QuotaTrailBackdrop
import app.quotatrail.presentation.theme.QuotaTrailFontScheme
import app.quotatrail.presentation.theme.QuotaTrailTheme
import java.time.Instant

/**
 * Debug-only preview of the Home account-summary card / hero over the app backdrop so the
 * theme-aware instrument surface, brand icon, avatar and refresh icon can be visually accepted in both
 * light and dark. It follows the system uiMode (ThemeMode.SYSTEM), so launching with the device in
 * dark mode renders the dark palette. Launch per spec:
 *   adb shell am start -n app.quotatrail.debug/app.quotatrail.presentation.home.HomePreviewActivity
 * then `adb exec-out screencap -p`.
 *
 * Fake data only: no real account, no network, no credentials.
 */
class HomePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuotaTrailTheme(
                themeMode = ThemeMode.SYSTEM,
                fontScheme = QuotaTrailFontScheme.GeistHybrid,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuotaTrailBackdrop(modifier = Modifier.fillMaxSize())
                    HomeScreen(uiState = previewHomeUiState())
                }
            }
        }
    }
}

@Composable
private fun previewHomeUiState(): HomeUiState {
    val now = Instant.parse("2026-06-02T09:00:00Z")
    val codexIcon = ProviderRegistry.iconFor(ProviderId("codex"))
    return HomeUiState(
        contentStatus = HomeContentStatus.Fresh,
        titleResId = R.string.app_name,
        statusTitleResId = R.string.home_state_fresh_title,
        statusDescriptionResId = R.string.home_state_fresh_description,
        errorMessageResId = null,
        account = HomeAccountUi(
            displayName = "个人开发号",
            avatarInitial = "K",
            avatarColorKey = "preview-account",
            planType = "Pro 5x",
            credits = HomeCreditsUi.Balance(amount = 8.5),
            providerIconResId = codexIcon,
        ),
        quotaCards = listOf(
            previewQuotaCard(
                windowId = "five_hour",
                titleResId = R.string.home_quota_window_five_hour,
                usedPercent = 13,
                resetAt = now.plusSeconds(5 * 3600),
                isPrimary = true,
            ),
            previewQuotaCard(
                windowId = "weekly",
                titleResId = R.string.home_quota_window_weekly,
                usedPercent = 58,
                resetAt = now.plusSeconds(3 * 24 * 3600),
                isPrimary = false,
            ),
        ),
        trend = HomeTrendUi(),
        loading = null,
        refresh = HomeRefreshUi(
            titleResId = R.string.home_state_fresh_title,
            descriptionResId = R.string.home_state_fresh_description,
            buttonTextResId = R.string.home_refresh,
            lastSuccessfulRefreshAt = now,
            lastAttemptFinishedAt = now,
        ),
        primaryAction = null,
        secondaryAction = null,
        isRefreshing = false,
        manualRefreshSuccessCount = 0,
    )
}

private fun previewQuotaCard(
    windowId: String,
    titleResId: Int,
    usedPercent: Int,
    resetAt: Instant,
    isPrimary: Boolean,
): HomeQuotaCardUi =
    HomeQuotaCardUi(
        windowId = windowId,
        titleResId = titleResId,
        displayKind = QuotaWindowDisplayKind.Percent,
        usedPercent = usedPercent,
        balanceAmount = null,
        balanceCurrency = null,
        usedCount = null,
        limitCount = null,
        subLabel = null,
        isPrimary = isPrimary,
        resetAt = resetAt,
        status = HomeQuotaStatus.Normal,
        tone = HomeStatusTone.Success,
        statusLabelResId = R.string.home_quota_status_normal,
    )
