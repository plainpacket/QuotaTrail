package app.quotatrail.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.presentation.motion.TrailMotion
import app.quotatrail.presentation.motion.TrendUsagePoint
import app.quotatrail.presentation.motion.rememberQuotaTrailAnimatorsEnabled
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.QuotaTrailTheme

@Composable
internal fun RemainingPercentHistoryChart(trend: HomeTrendUi) {
    val chartDescription = if (trend.points.isEmpty()) {
        stringResource(trend.descriptionResId)
    } else {
        stringResource(
            R.string.home_trend_chart_content_description,
            trend.points.maxBy { it.capturedAt }.remainingPercent.toInt(),
        )
    }
    Column(
        modifier = Modifier.semantics { contentDescription = chartDescription },
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        if (trend.comparisonPoints.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistoryLegendItem(
                    labelResId = R.string.home_trend_legend_seven_day,
                    color = QuotaTrailTheme.colors.accent,
                )
                HistoryLegendItem(
                    labelResId = R.string.home_trend_legend_five_hour,
                    color = QuotaTrailTheme.colors.signalAmber,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .padding(vertical = TrailSpacing.xs),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                WeeklyAxisText(R.string.home_trend_axis_100_percent)
                WeeklyAxisText(R.string.home_trend_axis_50_percent)
                WeeklyAxisText(R.string.home_trend_axis_0_percent)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = TrailSpacing.xs)
                    .clip(QuotaTrailShapes.sm)
                    .background(QuotaTrailTheme.colors.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                if (trend.points.isEmpty()) {
                    Text(
                        text = stringResource(trend.descriptionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    RemainingPercentLineChart(
                        points = trend.points,
                        comparisonPoints = trend.comparisonPoints,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WeeklyAxisText(R.string.home_trend_axis_3d_ago)
            WeeklyAxisText(R.string.home_trend_axis_2d_ago)
            WeeklyAxisText(R.string.home_trend_axis_1d_ago)
            WeeklyAxisText(R.string.home_trend_axis_now)
        }
    }
}

@Composable
private fun HistoryLegendItem(
    @androidx.annotation.StringRes labelResId: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.padding(start = TrailSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(3.dp)
                .clip(QuotaTrailShapes.pill)
                .background(color),
        )
        WeeklyAxisText(labelResId)
    }
}

@Composable
private fun RemainingPercentLineChart(
    points: List<HomeTrendPointUi>,
    comparisonPoints: List<HomeTrendPointUi>,
) {
    val weeklyLineColor = QuotaTrailTheme.colors.accent
    val fiveHourLineColor = QuotaTrailTheme.colors.signalAmber
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val animatorsEnabled = rememberQuotaTrailAnimatorsEnabled()
    val revealFraction = remember { Animatable(TrailMotion.initialTrendRevealTarget(animatorsEnabled)) }
    var hasRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(points.size, comparisonPoints.size, animatorsEnabled) {
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
            .fillMaxHeight(),
    ) {
        val horizontalInset = 10.dp.toPx()
        val verticalInset = 10.dp.toPx()
        val plotWidth = (size.width - horizontalInset * 2f).coerceAtLeast(0f)
        val plottedPoints = points.mapIndexed { index, point ->
            TrendUsagePoint(
                x = (point.xPositionInWindow ?: fallbackX(index, points.size)).coerceIn(0f, 1f),
                value = point.remainingPercent.toFloat().coerceIn(0f, 100f),
            )
        }
        val plottedComparisonPoints = comparisonPoints.mapIndexed { index, point ->
            TrendUsagePoint(
                x = (point.xPositionInWindow ?: fallbackX(index, comparisonPoints.size)).coerceIn(0f, 1f),
                value = point.remainingPercent.toFloat().coerceIn(0f, 100f),
            )
        }
        drawChartGrid(horizontalInset, verticalInset, plotWidth, gridColor)
        clipRect(right = size.width * revealFraction.value.coerceIn(0f, 1f)) {
            drawRemainingSegments(
                plottedComparisonPoints,
                horizontalInset,
                verticalInset,
                plotWidth,
                fiveHourLineColor,
            )
            drawRemainingSegments(plottedPoints, horizontalInset, verticalInset, plotWidth, weeklyLineColor)
        }
    }
}

private fun fallbackX(index: Int, pointCount: Int): Float =
    if (pointCount <= 1) 0.5f else index.toFloat() / (pointCount - 1)

@Composable
private fun WeeklyAxisText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartGrid(
    horizontalInset: Float,
    verticalInset: Float,
    plotWidth: Float,
    gridColor: androidx.compose.ui.graphics.Color,
) {
    listOf(0f, 0.5f, 1f).forEach { fraction ->
        val y = verticalInset + (size.height - verticalInset * 2f) * fraction
        drawLine(
            color = gridColor,
            start = Offset(horizontalInset, y),
            end = Offset(size.width - horizontalInset, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
    listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { fraction ->
        val x = horizontalInset + plotWidth * fraction
        drawLine(
            color = gridColor,
            start = Offset(x, verticalInset),
            end = Offset(x, size.height - verticalInset),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRemainingSegments(
    points: List<TrendUsagePoint>,
    horizontalInset: Float,
    verticalInset: Float,
    plotWidth: Float,
    lineColor: androidx.compose.ui.graphics.Color,
) {
    TrailMotion.remainingHistorySegments(
        points = points,
        maximumGapFraction = 1.5f / 72f,
    ).forEach { segment ->
        if (segment.size > 1) {
            val path = Path().apply {
                segment.forEachIndexed { index, point ->
                    val position = chartOffset(point, horizontalInset, verticalInset, plotWidth)
                    if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        segment.forEach { point ->
            drawCircle(
                color = lineColor,
                radius = 2.75.dp.toPx(),
                center = chartOffset(point, horizontalInset, verticalInset, plotWidth),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.chartOffset(
    point: TrendUsagePoint,
    horizontalInset: Float,
    verticalInset: Float,
    plotWidth: Float,
): Offset = Offset(
    x = horizontalInset + plotWidth * point.x,
    y = TrailMotion.remainingPercentY(
        remainingPercent = point.value,
        height = size.height,
        topInset = verticalInset,
        bottomInset = verticalInset,
    ),
)
