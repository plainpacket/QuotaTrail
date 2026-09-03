package app.quotatrail.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.TrailSpacing
import java.text.NumberFormat
import java.util.Locale

/** Compact account metadata with QuotaTrail's flat, route-band treatment. */
@Composable
internal fun HomeAccountTrailBand(uiState: HomeUiState) {
    val account = uiState.account ?: return
    val plan = account.planType
    val hasCredits = account.credits !is HomeCreditsUi.Unavailable
    if (plan == null && !hasCredits) return

    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = QuotaTrailShapes.instrument,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.lg),
        ) {
            plan?.let {
                AccountFact(
                    label = stringResource(R.string.home_account_plan_label),
                    value = it,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasCredits) {
                AccountFact(
                    label = stringResource(R.string.home_account_credits_label),
                    value = account.credits.displayText(locale),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccountFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeCreditsUi.displayText(locale: Locale): String =
    when (this) {
        is HomeCreditsUi.Balance -> stringResource(
            R.string.home_account_credits_balance_format,
            formatCreditBalance(amount, locale),
        )
        HomeCreditsUi.Unlimited -> stringResource(R.string.home_account_credits_unlimited)
        HomeCreditsUi.Unavailable -> stringResource(R.string.home_account_credits_unavailable)
    }

private fun formatCreditBalance(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(amount)
