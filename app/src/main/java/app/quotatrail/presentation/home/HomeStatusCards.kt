package app.quotatrail.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.presentation.components.InstrumentSurface
import app.quotatrail.presentation.components.InstrumentSurfaceLevel
import app.quotatrail.presentation.motion.TrailMotion
import app.quotatrail.presentation.motion.TrendUsagePoint
import app.quotatrail.presentation.motion.rememberQuotaTrailAnimatorsEnabled
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing

@Composable
internal fun HomeTrendCard(trend: HomeTrendUi) {
    HomeSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(trend.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(trend.metricLabelResId),
                    style = MaterialTheme.typography.labelMedium,
                    color = QuotaTrailTheme.colors.accent,
                    maxLines = 1,
                )
            }
            if (trend.displaysRemainingPercent) {
                RemainingPercentHistoryChart(trend)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .clip(QuotaTrailShapes.lg)
                        .background(QuotaTrailTheme.colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    if (trend.points.any { it.usageValue > 0.0 }) {
                        TrendBarChart(points = trend.points)
                    } else {
                        Text(
                            text = stringResource(trend.descriptionResId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AxisText(R.string.home_trend_axis_24h_ago)
                    AxisText(R.string.home_trend_axis_12h)
                    AxisText(R.string.home_trend_axis_now)
                }
            }
        }
    }
}

@Composable
private fun TrendBarChart(points: List<HomeTrendPointUi>) {
    val barColor = QuotaTrailTheme.colors.accent.copy(alpha = 0.76f)
    val animatorsEnabled = rememberQuotaTrailAnimatorsEnabled()
    val revealFraction = remember { Animatable(TrailMotion.initialTrendRevealTarget(animatorsEnabled)) }
    var hasRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(points.size, animatorsEnabled) {
        if (!animatorsEnabled || hasRevealed || points.isEmpty()) {
            revealFraction.snapTo(1f)
        } else {
            hasRevealed = true
            revealFraction.snapTo(0f)
            revealFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TrailMotion.TrendRedrawDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .padding(TrailSpacing.md),
    ) {
        val sorted = points.sortedBy { it.capturedAt }
        val maxIndex = (sorted.size - 1).coerceAtLeast(1)
        val topInset = 6.dp.toPx()
        val bottomInset = 5.dp.toPx()
        val baselineY = size.height - bottomInset
        val bars = TrailMotion.trendHourlyUsageBars(
            sorted.mapIndexed { index, point ->
                TrendUsagePoint(
                    x = (point.xPositionInWindow ?: (index.toFloat() / maxIndex)).coerceIn(0f, 1f),
                    value = point.usageValue.toFloat(),
                )
            },
        )
        val maxValue = bars.maxOfOrNull { it.value } ?: 0f
        val barWidth = TrailMotion.trendBarWidth(
            availableWidth = size.width,
            barCount = TrailMotion.TrendHourlyBucketCount,
            minWidth = 4.dp.toPx(),
            maxWidth = 10.dp.toPx(),
        )
        if (bars.isEmpty() || barWidth <= 0f || maxValue <= 0f) return@Canvas

        clipRect(right = size.width * revealFraction.value.coerceIn(0f, 1f)) {
            bars.forEach { bar ->
                val centerX = TrailMotion.trendBarCenterX(
                    normalizedX = bar.x,
                    width = size.width,
                    barWidth = barWidth,
                )
                val topY = TrailMotion.trendUsageBarY(
                    value = bar.value,
                    maxValue = maxValue,
                    height = size.height,
                    topInset = topInset,
                    bottomInset = bottomInset,
                )
                val barHeight = (baselineY - topY).coerceAtLeast(0f)
                val radius = TrailMotion.trendBarTopCornerRadius(
                    barWidth = barWidth,
                    barHeight = barHeight,
                )
                drawTopRoundedTrendBar(
                    left = centerX - barWidth / 2f,
                    top = topY,
                    right = centerX + barWidth / 2f,
                    bottom = baselineY,
                    radius = radius,
                    color = barColor,
                )
            }
        }
    }
}

private fun DrawScope.drawTopRoundedTrendBar(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    color: Color,
) {
    val boundedLeft = left.coerceAtMost(right)
    val boundedRight = right.coerceAtLeast(left)
    val boundedTop = top.coerceAtMost(bottom)
    val height = bottom - boundedTop
    val width = boundedRight - boundedLeft
    if (height <= 0f || width <= 0f) return

    val capRadius = radius.coerceIn(0f, minOf(width / 2f, height / 2f))
    val path = Path().apply {
        moveTo(boundedLeft, bottom)
        lineTo(boundedLeft, boundedTop + capRadius)
        quadraticTo(boundedLeft, boundedTop, boundedLeft + capRadius, boundedTop)
        lineTo(boundedRight - capRadius, boundedTop)
        quadraticTo(boundedRight, boundedTop, boundedRight, boundedTop + capRadius)
        lineTo(boundedRight, bottom)
        close()
    }
    drawPath(path = path, color = color)
}

@Composable
private fun AxisText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun HomeLoadingCard(loading: HomeLoadingUi) {
    HomeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = QuotaTrailTheme.colors.accent,
            )
            Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs)) {
                Text(
                    text = stringResource(loading.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(loading.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HomeRefreshCard(uiState: HomeUiState) {
    HomeSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
            Text(
                text = stringResource(uiState.refresh.titleResId),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(uiState.refresh.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.errorMessageResId?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            RefreshTimeline(refresh = uiState.refresh)
        }
    }
}

@Composable
private fun RefreshTimeline(refresh: HomeRefreshUi) {
    Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs)) {
        refresh.lastSuccessfulRefreshAt?.let {
            Text(
                text = stringResource(R.string.home_last_refresh_format, formattedInstant(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        refresh.lastAttemptFinishedAt?.let {
            Text(
                text = stringResource(R.string.home_last_attempt_format, formattedInstant(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HomeActionCard(
    uiState: HomeUiState,
    onLoginClick: () -> Unit,
) {
    InstrumentSurface(
        modifier = Modifier.fillMaxWidth(),
        level = InstrumentSurfaceLevel.Focal,
        contentPadding = PaddingValues(TrailSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
            Text(
                text = stringResource(uiState.statusTitleResId),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(uiState.statusDescriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
                uiState.primaryAction?.let { action ->
                    Button(
                        onClick = { action.onClick(onLoginClick) },
                        modifier = Modifier.weight(1f),
                        shape = QuotaTrailShapes.md,
                    ) {
                        Text(text = stringResource(action.labelResId))
                    }
                }
                uiState.secondaryAction?.let { action ->
                    OutlinedButton(
                        onClick = { action.onClick(onLoginClick) },
                        modifier = Modifier.weight(1f),
                        shape = QuotaTrailShapes.md,
                    ) {
                        Text(text = stringResource(action.labelResId))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSurfaceCard(content: @Composable () -> Unit) {
    InstrumentSurface(
        modifier = Modifier.fillMaxWidth(),
        level = InstrumentSurfaceLevel.Panel,
        contentPadding = PaddingValues(TrailSpacing.lg),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

private fun HomeActionUi.onClick(
    onLoginClick: () -> Unit,
) {
    when (kind) {
        HomeActionKind.LoginToCodex -> onLoginClick()
    }
}
