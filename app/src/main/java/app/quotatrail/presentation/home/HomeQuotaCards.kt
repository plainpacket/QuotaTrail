package app.quotatrail.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quotatrail.R
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.presentation.components.InstrumentSurface
import app.quotatrail.presentation.components.InstrumentSurfaceLevel
import app.quotatrail.presentation.motion.AnimatedQuotaPercent
import app.quotatrail.presentation.motion.MotionQuotaRail
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.QuotaTrailTypography

@Composable
internal fun HomeQuotaCards(quotaCards: List<HomeQuotaCardUi>) {
    if (quotaCards.isEmpty()) return
    InstrumentSurface(
        modifier = Modifier.fillMaxWidth(),
        level = InstrumentSurfaceLevel.Focal,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            quotaCards.forEachIndexed { index, card ->
                QuotaRow(card = card, featured = index == 0)
                if (index != quotaCards.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = TrailSpacing.lg),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaRow(card: HomeQuotaCardUi, featured: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (featured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(TrailSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
    ) {
        QuotaRowHeader(card)
        when (card.displayKind) {
            QuotaWindowDisplayKind.Percent,
            QuotaWindowDisplayKind.MultiModelFraction,
            -> PercentQuotaContent(card, featured)
            QuotaWindowDisplayKind.Balance -> BalanceQuotaContent(card)
            QuotaWindowDisplayKind.UsageCount -> UsageCountQuotaContent(card)
        }
    }
}

@Composable
private fun QuotaRowHeader(card: HomeQuotaCardUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(card.titleResId),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(tone = card.tone)
            Text(
                text = stringResource(card.statusLabelResId),
                style = MaterialTheme.typography.labelMedium,
                color = toneColor(card.tone),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PercentQuotaContent(card: HomeQuotaCardUi, featured: Boolean) {
    AnimatedQuotaPercent(
        percent = card.percent,
        style = if (featured) {
            QuotaTrailTypography.current.number.copy(fontSize = 48.sp, lineHeight = 52.sp)
        } else {
            QuotaTrailTypography.current.number
        },
        color = MaterialTheme.colorScheme.onSurface,
    )
    MotionQuotaRail(percent = card.percent, color = toneColor(card.tone))
    RenewalText(card)
}

@Composable
private fun BalanceQuotaContent(card: HomeQuotaCardUi) {
    Text(
        text = app.quotatrail.presentation.quota.formatProviderBalance(
            card.balanceAmount,
            card.balanceCurrency,
        ).orEmpty(),
        style = QuotaTrailTypography.current.number,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val originalBalance = app.quotatrail.presentation.quota.formatProviderBalance(
        card.originalBalanceAmount,
        card.originalBalanceCurrency,
    )
    originalBalance?.let { SupportingText(it) }
    if (card.grantedBalance != null || card.toppedUpBalance != null) {
        val nativeCurrency = card.originalBalanceCurrency ?: card.balanceCurrency
        val granted = app.quotatrail.presentation.quota.formatProviderBalance(card.grantedBalance, nativeCurrency).orEmpty()
        val toppedUp = app.quotatrail.presentation.quota.formatProviderBalance(card.toppedUpBalance, nativeCurrency).orEmpty()
        SupportingText(stringResource(R.string.quota_balance_breakdown_format, granted, toppedUp))
    }
    card.subLabel?.let { SupportingText(it) }
}

@Composable
private fun UsageCountQuotaContent(card: HomeQuotaCardUi) {
    Text(
        text = "${card.usedCount ?: "–"} / ${card.limitCount ?: "–"}",
        style = QuotaTrailTypography.current.number,
        color = MaterialTheme.colorScheme.onSurface,
    )
    MotionQuotaRail(percent = card.percent, color = toneColor(card.tone))
    RenewalText(card)
}

@Composable
private fun RenewalText(card: HomeQuotaCardUi) {
    Text(
        text = card.resetAt?.let {
            stringResource(R.string.home_reset_at_format, formattedInstant(it))
        } ?: stringResource(R.string.home_reset_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusDot(tone: HomeStatusTone) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(toneColor(tone)),
    )
}
