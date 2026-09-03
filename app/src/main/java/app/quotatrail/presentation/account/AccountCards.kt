package app.quotatrail.presentation.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.presentation.motion.TrailMotion
import app.quotatrail.presentation.motion.rememberQuotaTrailAnimatorsEnabled
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.avatarColor
import app.quotatrail.presentation.theme.avatarInitialColor
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun EmptyAccountsCard(onAddAccountClick: () -> Unit) {
    AccountSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
            Text(
                text = stringResource(R.string.account_no_accounts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.account_no_accounts_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAddAccountClick, shape = QuotaTrailShapes.md) {
                Text(text = stringResource(R.string.account_add_account))
            }
        }
    }
}

@Composable
internal fun AccountCard(
    account: AccountItemUi,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
    onToggleExpanded: (LocalAccountId) -> Unit,
) {
    AccountSurfaceCard {
        val animatorsEnabled = rememberQuotaTrailAnimatorsEnabled()
        Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
            val toggleLabel = stringResource(
                if (account.isExpanded) R.string.account_collapse else R.string.account_expand,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = toggleLabel) { onToggleExpanded(account.id) },
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(account = account, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(account.statusLabelResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = account.lastSuccessfulRefreshAt?.let {
                            stringResource(account.refreshSummaryResId, formattedInstant(it))
                        } ?: stringResource(account.refreshSummaryResId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(account = account)
                    AccountExpandIconButton(
                        isExpanded = account.isExpanded,
                        contentDescription = toggleLabel,
                        animatorsEnabled = animatorsEnabled,
                        onClick = { onToggleExpanded(account.id) },
                    )
                }
            }
            AccountDetailsDrawer(
                account = account,
                animatorsEnabled = animatorsEnabled,
                onRenameClick = onRenameClick,
                onReloginClick = onReloginClick,
                onQuotaAlertToggle = onQuotaAlertToggle,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun AccountExpandIconButton(
    isExpanded: Boolean,
    contentDescription: String,
    animatorsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = if (animatorsEnabled) {
            tween(
                durationMillis = TrailMotion.AccountDrawerDurationMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "account_expand_icon_rotation",
    )
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = QuotaTrailTheme.colors.accent,
        )
    }
}

@Composable
private fun AccountDetailsDrawer(
    account: AccountItemUi,
    animatorsEnabled: Boolean,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    AnimatedVisibility(
        visible = account.isExpanded,
        modifier = Modifier.clipToBounds(),
        enter = if (animatorsEnabled) {
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(
                    durationMillis = TrailMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = TrailMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            EnterTransition.None
        },
        exit = if (animatorsEnabled) {
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(
                    durationMillis = TrailMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = TrailMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            ExitTransition.None
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
            PlanCreditsRow(account = account)
            QuotaSummaryRow(account.quotaSummaries)
            if (account.modelFamilies.isNotEmpty()) {
                ModelDetailsSection(account.modelFamilies)
            }
            QuotaAlertSwitches(
                account = account,
                onQuotaAlertToggle = onQuotaAlertToggle,
            )
            AccountActions(
                account = account,
                onRenameClick = onRenameClick,
                onReloginClick = onReloginClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
internal fun SectionLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun AccountSurfaceCard(content: @Composable () -> Unit) {
    Card(
        shape = QuotaTrailShapes.instrument,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TrailSpacing.lg),
        ) {
            content()
        }
    }
}

@Composable
private fun PlanCreditsRow(account: AccountItemUi) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    // Providers expose different fields: show only the tiles that actually have data so that, e.g.,
    // Kimi (no plan/credits) or Antigravity (plan only) don't render "Unavailable" placeholders.
    val planType = account.planType
    val hasCredits = account.credits !is AccountCreditsUi.Unavailable
    if (planType == null && !hasCredits) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
    ) {
        if (planType != null) {
            AccountMetadataTile(
                label = stringResource(R.string.account_plan_label),
                value = planType,
                modifier = Modifier.weight(1f),
            )
        }
        if (hasCredits) {
            AccountMetadataTile(
                label = stringResource(R.string.account_credits_label),
                value = account.credits.displayText(locale),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AccountMetadataTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(QuotaTrailShapes.sm)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(TrailSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
private fun QuotaSummaryRow(summaries: List<AccountQuotaSummaryUi>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
    ) {
        summaries.forEach { summary ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(QuotaTrailShapes.md)
                    .background(QuotaTrailTheme.colors.surfaceSoft)
                    .padding(TrailSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
            ) {
                Text(
                    text = stringResource(summary.labelResId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary.valueText
                        ?: summary.percent?.let {
                            stringResource(R.string.account_quota_percent_format, it)
                        }
                        ?: stringResource(R.string.account_quota_placeholder_no_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Original native balance (shown when conversion occurred)
                summary.originalValueText?.let { original ->
                    Text(
                        text = original,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // DeepSeek-style granted/topped-up breakdown (native currency)
                if (summary.grantedText != null || summary.toppedUpText != null) {
                    Text(
                        text = stringResource(
                            R.string.quota_balance_breakdown_format,
                            summary.grantedText.orEmpty(),
                            summary.toppedUpText.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelDetailsSection(families: List<AccountModelFamilyUi>) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuotaTrailShapes.md)
            .background(QuotaTrailTheme.colors.surfaceSoft)
            .padding(TrailSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_model_details_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            families.forEach { family ->
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                family.models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = TrailSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.account_quota_percent_format, model.remainingPercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaAlertSwitches(
    account: AccountItemUi,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuotaTrailShapes.md)
            .background(QuotaTrailTheme.colors.surfaceSoft)
            .padding(TrailSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.account_quota_alerts_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        account.alertToggles.forEach { toggle ->
            QuotaAlertSwitchLine(
                label = stringResource(toggle.labelResId),
                checked = toggle.enabled,
                onCheckedChange = { enabled ->
                    onQuotaAlertToggle(account.id, QuotaWindowId(toggle.windowId), enabled)
                },
            )
        }
    }
}

@Composable
private fun QuotaAlertSwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun AccountActions(
    account: AccountItemUi,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    val actionButtonBorder = BorderStroke(1.dp, QuotaTrailTheme.colors.border)
    Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm)) {
            OutlinedButton(
                onClick = { onRenameClick(account.id) },
                modifier = Modifier.weight(1f),
                shape = QuotaTrailShapes.md,
            ) {
                Text(text = stringResource(R.string.account_rename))
            }
            OutlinedButton(
                onClick = { onReloginClick(account.id) },
                modifier = Modifier.weight(1f),
                shape = QuotaTrailShapes.md,
            ) {
                Text(text = stringResource(R.string.account_relogin))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm)) {
            OutlinedButton(
                onClick = { onDeleteClick(account.id) },
                modifier = Modifier.weight(1f),
                shape = QuotaTrailShapes.md,
                border = actionButtonBorder,
            ) {
                Text(
                    text = stringResource(R.string.account_delete),
                    color = QuotaTrailTheme.colors.danger,
                )
            }
        }
    }
}


@Composable
private fun StatusBadge(account: AccountItemUi) {
    Box(
        modifier = Modifier
            .clip(QuotaTrailShapes.pill)
            .background(account.tone.softColor())
            .padding(horizontal = TrailSpacing.md, vertical = TrailSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(account.badgeLabelResId),
            style = MaterialTheme.typography.labelSmall,
            color = account.tone.color(),
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountAvatar(account: AccountItemUi, size: Dp) {
    val iconRes = account.providerIconResId
    if (iconRes != null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(QuotaTrailTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = QuotaTrailTheme.colors.primary,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor(account.avatarColorKey)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.avatarInitial,
                style = MaterialTheme.typography.titleMedium,
                color = avatarInitialColor(),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun formattedInstant(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(instant, locale) {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}

@Composable
private fun AccountCreditsUi.displayText(locale: Locale): String =
    when (this) {
        is AccountCreditsUi.Balance -> stringResource(
            R.string.account_credits_balance_format,
            formatCreditBalance(amount, locale),
        )
        AccountCreditsUi.Unlimited -> stringResource(R.string.account_credits_unlimited)
        AccountCreditsUi.Unavailable -> stringResource(R.string.account_credits_unavailable)
    }

private fun formatCreditBalance(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(amount)

@Composable
@ReadOnlyComposable
private fun AccountStatusTone.color(): Color =
    when (this) {
        AccountStatusTone.Neutral -> QuotaTrailTheme.colors.secondary
        AccountStatusTone.Success -> QuotaTrailTheme.colors.success
        AccountStatusTone.Warning -> QuotaTrailTheme.colors.warning
        AccountStatusTone.Danger -> QuotaTrailTheme.colors.danger
    }

@Composable
@ReadOnlyComposable
private fun AccountStatusTone.softColor(): Color =
    when (this) {
        AccountStatusTone.Neutral -> QuotaTrailTheme.colors.neutralAlt
        AccountStatusTone.Success -> QuotaTrailTheme.colors.successSoft
        AccountStatusTone.Warning -> QuotaTrailTheme.colors.warningSoft
        AccountStatusTone.Danger -> QuotaTrailTheme.colors.dangerSoft
    }
