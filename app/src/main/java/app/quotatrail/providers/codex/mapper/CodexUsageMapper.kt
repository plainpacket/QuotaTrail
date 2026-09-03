package app.quotatrail.providers.codex.mapper

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.Credits
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.providers.codex.dto.CodexCreditsDto
import app.quotatrail.providers.codex.dto.CodexUsageResponseDto
import app.quotatrail.providers.codex.dto.CodexWindowDto
import java.time.DateTimeException
import java.time.Instant

class CodexUsageMapper {
    fun map(
        dto: CodexUsageResponseDto,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        fetchedAt: Instant,
        source: QuotaSnapshotSource,
    ): QuotaSnapshot {
        val slots = listOf(
            WindowSlot(
                providerWindow = dto.rateLimit?.primaryWindow,
                decodeFailed = dto.rateLimit?.primaryWindowDecodeFailed == true,
                fallbackKind = WindowKind.FiveHour,
            ),
            WindowSlot(
                providerWindow = dto.rateLimit?.secondaryWindow,
                decodeFailed = dto.rateLimit?.secondaryWindowDecodeFailed == true,
                fallbackKind = WindowKind.Weekly,
            ),
        )

        return QuotaSnapshot(
            snapshotId = SnapshotId(
                "codex:${localAccountId.value}:${fetchedAt.toEpochMilli()}:${source.name}",
            ),
            providerId = codexProviderId,
            localAccountId = localAccountId,
            providerAccountId = providerAccountId,
            fetchedAt = fetchedAt,
            source = source,
            planType = dto.planType,
            windows = listOf(
                toQuotaWindow(
                    windowId = QuotaWindowId(FIVE_HOUR_WINDOW_ID),
                    titleKey = FIVE_HOUR_TITLE_KEY,
                    slot = slots.slotFor(WindowKind.FiveHour),
                ),
                toQuotaWindow(
                    windowId = QuotaWindowId(WEEKLY_WINDOW_ID),
                    titleKey = WEEKLY_TITLE_KEY,
                    slot = slots.slotFor(WindowKind.Weekly),
                ),
            ),
            credits = dto.toCredits(),
            responseDigest = null,
        )
    }

    private fun toQuotaWindow(
        windowId: QuotaWindowId,
        titleKey: String,
        slot: WindowSlot?,
    ): QuotaWindow {
        val providerWindow = slot?.providerWindow
        val decodeFailed = slot?.decodeFailed == true
        val resetAt = providerWindow?.resetAt?.toInstantOrNull()
        val resetDecodeFailed = providerWindow?.resetAt != null && resetAt == null
        val mappedWindow = providerWindow?.takeUnless { decodeFailed || resetDecodeFailed }
        val availability = when {
            decodeFailed || resetDecodeFailed -> QuotaWindowAvailability.DecodeFailed
            mappedWindow == null -> QuotaWindowAvailability.Missing
            else -> QuotaWindowAvailability.Available
        }

        return QuotaWindow(
            windowId = windowId,
            titleKey = titleKey,
            usedPercent = mappedWindow?.usedPercent,
            resetAt = mappedWindow?.let { resetAt },
            limitWindowSeconds = mappedWindow?.limitWindowSeconds,
            isPrimaryCandidate = true,
            availability = availability,
        )
    }

    /**
     * The API historically used primary=5h and secondary=weekly, but newer plans may return only a
     * seven-day primary window. Prefer the server-provided duration and use slot position only when
     * the duration is absent or unfamiliar.
     */
    private fun List<WindowSlot>.slotFor(kind: WindowKind): WindowSlot? =
        firstOrNull { it.providerWindow?.limitWindowSeconds.classifyWindow() == kind }
            ?: firstOrNull {
                it.fallbackKind == kind && it.providerWindow?.limitWindowSeconds.classifyWindow() == null
            }

    private fun Int?.classifyWindow(): WindowKind? = when {
        this == null -> null
        this >= FIVE_DAYS_SECONDS -> WindowKind.Weekly
        this <= ONE_DAY_SECONDS -> WindowKind.FiveHour
        else -> null
    }

    private fun CodexUsageResponseDto.toCredits(): Credits? {
        if (creditsDecodeFailed) {
            return null
        }

        return credits?.toCredits()
    }

    private fun CodexCreditsDto.toCredits(): Credits? {
        val hasCredits = hasCredits ?: return null
        val unlimited = unlimited ?: return null
        return Credits(
            hasCredits = hasCredits,
            unlimited = unlimited,
            balance = balance,
        )
    }

    private fun Long.toInstantOrNull(): Instant? =
        try {
            Instant.ofEpochSecond(this)
        } catch (_: DateTimeException) {
            null
        }

    private companion object {
        val codexProviderId = ProviderId("codex")
        const val FIVE_HOUR_WINDOW_ID = "five_hour"
        const val FIVE_HOUR_TITLE_KEY = "quota_window_five_hour"
        const val WEEKLY_WINDOW_ID = "weekly"
        const val WEEKLY_TITLE_KEY = "quota_window_weekly"
        const val ONE_DAY_SECONDS = 86_400
        const val FIVE_DAYS_SECONDS = 432_000
    }

    private data class WindowSlot(
        val providerWindow: CodexWindowDto?,
        val decodeFailed: Boolean,
        val fallbackKind: WindowKind,
    )

    private enum class WindowKind { FiveHour, Weekly }
}
