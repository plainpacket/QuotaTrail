package app.quotatrail.providers.zai.mapper

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.providers.zai.dto.ZaiQuotaResponseDto
import java.time.Instant

object ZaiQuotaMapper {
    fun map(
        dto: ZaiQuotaResponseDto,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        fetchedAt: Instant,
        source: QuotaSnapshotSource,
    ): QuotaSnapshot {
        val limits = dto.data?.limits.orEmpty()
        if (limits.isEmpty()) {
            return emptySnapshot(localAccountId, fetchedAt, source)
        }

        // Map every limit entry so real responses are never silently dropped; the shortest window
        // (5h-style unit=hours) is the primary candidate, else the first entry.
        val primaryIndex = limits.indexOfFirst { it.unit == PRIMARY_WINDOW_UNIT }
            .takeIf { it >= 0 } ?: 0
        val windows = limits.mapIndexed { index, item ->
            item.toQuotaWindow(isPrimary = index == primaryIndex)
        }

        return QuotaSnapshot(
            snapshotId = SnapshotId("zai_${fetchedAt}"),
            providerId = ZAI_PROVIDER_ID,
            localAccountId = localAccountId,
            providerAccountId = providerAccountId,
            fetchedAt = fetchedAt,
            source = source,
            planType = dto.data?.planName ?: dto.data?.level,
            windows = windows,
            credits = null,
            responseDigest = null,
        )
    }

    private fun ZaiQuotaResponseDto.LimitItem.toQuotaWindow(isPrimary: Boolean): QuotaWindow {
        val resetAt = nextResetTime?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }

        // `usage` is the limit/total; used = currentValue or (limit - remaining). Fall back to the
        // server-provided `percentage` (which is already a used percent) when counts are absent.
        val limit = usage
        val used = when {
            limit != null && remaining != null ->
                maxOf(limit - remaining, currentValue ?: Int.MIN_VALUE).coerceAtLeast(0)
            currentValue != null -> currentValue
            else -> null
        }
        val usedPercent = if (limit != null && limit > 0 && used != null) {
            (used * 100.0 / limit).coerceIn(0.0, 100.0).toInt()
        } else {
            percentage?.coerceIn(0.0, 100.0)?.toInt()
        }
        val availability = when {
            usedPercent == null -> QuotaWindowAvailability.Missing
            usedPercent >= 100 -> QuotaWindowAvailability.Depleted
            else -> QuotaWindowAvailability.Available
        }

        val windowLabel = when (unit) {
            PRIMARY_WINDOW_UNIT -> "zai_5h_window"
            SECONDARY_WINDOW_UNIT -> "zai_weekly_window"
            else -> "zai_${type}_window"
        }

        return QuotaWindow(
            windowId = QuotaWindowId(windowLabel),
            titleKey = windowLabel,
            usedPercent = usedPercent,
            resetAt = resetAt,
            limitWindowSeconds = when (unit) {
                PRIMARY_WINDOW_UNIT -> 5 * 3600 // 5 hours
                SECONDARY_WINDOW_UNIT -> 7 * 24 * 3600 // 1 week
                else -> null
            },
            isPrimaryCandidate = isPrimary,
            availability = availability,
            displayKind = QuotaWindowDisplayKind.Percent,
            subLabel = type,
        )
    }

    private fun emptySnapshot(
        localAccountId: LocalAccountId,
        fetchedAt: Instant,
        source: QuotaSnapshotSource,
    ): QuotaSnapshot = QuotaSnapshot(
        snapshotId = SnapshotId("zai_empty_${fetchedAt}"),
        providerId = ZAI_PROVIDER_ID,
        localAccountId = localAccountId,
        providerAccountId = null,
        fetchedAt = fetchedAt,
        source = source,
        planType = null,
        windows = emptyList(),
        credits = null,
        responseDigest = null,
    )

    private const val PRIMARY_WINDOW_UNIT = 3   // 5h granularity
    private const val SECONDARY_WINDOW_UNIT = 6 // weekly
    private val ZAI_PROVIDER_ID = ProviderId("zai")
}
