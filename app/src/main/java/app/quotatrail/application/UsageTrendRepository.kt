package app.quotatrail.application

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.presentation.home.HomeTrendHistoryLoader
import app.quotatrail.presentation.home.HomeTrendMetric
import app.quotatrail.presentation.home.HomeTrendPointUi
import app.quotatrail.presentation.home.HomeTrendQuery
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class UsageTrendRepository(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val clock: Clock,
) : HomeTrendHistoryLoader {
    override suspend fun loadTrend(accountId: LocalAccountId?, query: HomeTrendQuery): List<HomeTrendPointUi> {
        val selection = if (accountId == null) currentAccountReader.currentAccountSelection() else null
        val resolvedAccountId = accountId ?: selection?.localAccountId ?: return emptyList()
        val account = providerAccountDao.getById(resolvedAccountId.value)
            ?.takeIf { selection == null || it.providerId == selection.providerId.value }
            ?: return emptyList()

        val now = clock.instant()
        val trendWindow = if (query.metric == HomeTrendMetric.RemainingPercent) {
            REMAINING_PERCENT_WINDOW
        } else {
            CONSUMPTION_WINDOW
        }
        val windowStart = now.minus(trendWindow)
        val snapshots = quotaSnapshotDao.listForAccountSince(
            providerId = account.providerId,
            localAccountId = account.localAccountId,
            sinceMillis = windowStart.toEpochMilli(),
        ).mapNotNull { entity -> runCatching { entity.toDomain() }.getOrNull() }
            .sortedBy { it.fetchedAt }

        return if (query.metric == HomeTrendMetric.RemainingPercent) {
            hourlyRemainingPercentPoints(snapshots, query.windowId, windowStart, now)
        } else if (query.useModelBucketSum) {
            modelBucketSumPoints(snapshots, windowStart, now)
        } else {
            scalarDiffPoints(snapshots, query.windowId, query.displayKind, windowStart, now)
        }
    }

    private fun hourlyRemainingPercentPoints(
        snapshots: List<QuotaSnapshot>,
        windowId: String,
        windowStart: Instant,
        now: Instant,
    ): List<HomeTrendPointUi> =
        snapshots.mapNotNull { snapshot ->
            val window = snapshot.windows.firstOrNull {
                it.windowId.value == windowId &&
                    (it.availability == QuotaWindowAvailability.Available ||
                        it.availability == QuotaWindowAvailability.Depleted)
            } ?: return@mapNotNull null
            val remainingPercent = window.remainingPercent ?: return@mapNotNull null
            val bucketIndex = Duration.between(windowStart, snapshot.fetchedAt)
                .toHours()
                .toInt()
                .coerceIn(0, REMAINING_PERCENT_BUCKET_COUNT - 1)
            bucketIndex to remainingPercent.toDouble()
        }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .toSortedMap()
            .map { (bucketIndex, values) ->
                val capturedAt = windowStart
                    .plus(Duration.ofHours(bucketIndex.toLong()))
                    .plus(Duration.ofMinutes(30))
                HomeTrendPointUi(
                    capturedAt = capturedAt,
                    usageValue = values.average().coerceIn(0.0, 100.0),
                    xPositionInWindow = capturedAt.positionInTrendWindow(windowStart, now),
                )
            }

    private fun scalarDiffPoints(
        snapshots: List<QuotaSnapshot>,
        windowId: String,
        displayKind: QuotaWindowDisplayKind,
        windowStart: Instant,
        now: Instant,
    ): List<HomeTrendPointUi> {
        val series = snapshots.mapNotNull { snapshot ->
            val window = snapshot.windows
                .firstOrNull { it.windowId.value == windowId && it.availability == QuotaWindowAvailability.Available }
                ?: return@mapNotNull null
            val value = window.cumulativeValue(displayKind) ?: return@mapNotNull null
            snapshot.fetchedAt to value
        }
        return series.zipWithNext().map { (prev, curr) ->
            val delta = consumptionDelta(prev.second, curr.second, displayKind).coerceAtLeast(0.0)
            HomeTrendPointUi(
                capturedAt = curr.first,
                usageValue = delta,
                xPositionInWindow = curr.first.positionInTrendWindow(windowStart, now),
            )
        }
    }

    private fun modelBucketSumPoints(
        snapshots: List<QuotaSnapshot>,
        windowStart: Instant,
        now: Instant,
    ): List<HomeTrendPointUi> {
        val series = snapshots.mapNotNull { snapshot ->
            // query.windowId is intentionally NOT used here: we sum across ALL of the provider's
            // model-bucket-sum windows (e.g. Antigravity's per-family windows) so the chart
            // reflects total usage across every model family, not just the primary one.
            val usedByModel = snapshot.windows
                .filter { it.usesModelBucketSum && it.availability == QuotaWindowAvailability.Available }
                .flatMap { it.modelBuckets }
                .associate { it.modelId to (1.0 - it.remainingFraction).coerceIn(0.0, 1.0) }
            if (usedByModel.isEmpty()) null else snapshot.fetchedAt to usedByModel
        }
        return series.zipWithNext().map { (prev, curr) ->
            val prevUsed = prev.second
            val delta = curr.second.entries.sumOf { (modelId, used) ->
                (used - (prevUsed[modelId] ?: 0.0)).coerceAtLeast(0.0)
            }
            HomeTrendPointUi(
                capturedAt = curr.first,
                usageValue = delta,
                xPositionInWindow = curr.first.positionInTrendWindow(windowStart, now),
            )
        }
    }

    private fun QuotaWindow.cumulativeValue(displayKind: QuotaWindowDisplayKind): Double? =
        when (displayKind) {
            QuotaWindowDisplayKind.Balance -> balanceAmount?.toDoubleOrNull()
            QuotaWindowDisplayKind.UsageCount -> usedCount?.toDouble()
            // Providers that use MultiModelFraction set usesModelBucketSum = true and therefore
            // take the modelBucketSumPoints path instead; in practice this scalar path only sees
            // plain Percent windows.
            else -> usedPercent?.toDouble()
        }

    // Used metrics rise with consumption; balance falls as it is spent.
    private fun consumptionDelta(prev: Double, curr: Double, displayKind: QuotaWindowDisplayKind): Double =
        if (displayKind == QuotaWindowDisplayKind.Balance) prev - curr else curr - prev

    private fun Instant.positionInTrendWindow(windowStart: Instant, windowEnd: Instant): Float {
        val elapsedMillis = Duration.between(windowStart, this).toMillis().coerceAtLeast(0L)
        val windowMillis = Duration.between(windowStart, windowEnd).toMillis().coerceAtLeast(1L)
        return (elapsedMillis.toFloat() / windowMillis.toFloat()).coerceIn(0f, 1f)
    }

    private companion object {
        const val REMAINING_PERCENT_BUCKET_COUNT = 72
        val CONSUMPTION_WINDOW: Duration = Duration.ofHours(24)
        val REMAINING_PERCENT_WINDOW: Duration = Duration.ofHours(REMAINING_PERCENT_BUCKET_COUNT.toLong())
    }
}
