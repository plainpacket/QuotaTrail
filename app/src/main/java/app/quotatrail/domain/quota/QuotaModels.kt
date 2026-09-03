package app.quotatrail.domain.quota

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.SnapshotId
import java.time.Instant

enum class QuotaWindowAvailability { Available, Depleted, Missing, DecodeFailed, Unsupported }

enum class QuotaSnapshotSource {
    DeviceCodeLogin,
    // Legacy values are kept so older local history can still be decoded.
    OAuthWebView,
    AuthJsonImport,
    BackgroundRefresh,
    ManualRefresh,
    WidgetRefresh,
    AppOpenRefresh,
    ApiKeyImport,
    CookieAuth,
    OAuthPkceLogin,
}

enum class QuotaWindowDisplayKind {
    Percent,
    Balance,
    UsageCount,
    MultiModelFraction,
}

data class QuotaModelBucket(
    val modelId: String,
    val displayName: String,
    val remainingFraction: Double,
    val resetAt: Instant?,
)

data class QuotaWindow(
    val windowId: QuotaWindowId,
    val titleKey: String,
    val usedPercent: Int?,
    val resetAt: Instant?,
    val limitWindowSeconds: Int?,
    val isPrimaryCandidate: Boolean,
    val availability: QuotaWindowAvailability,
    val displayKind: QuotaWindowDisplayKind = QuotaWindowDisplayKind.Percent,
    val balanceAmount: String? = null,
    val balanceCurrency: String? = null,
    // Pre-conversion balance, set only when a currency conversion was applied (presentation-only, never persisted).
    val originalBalanceAmount: String? = null,
    val originalBalanceCurrency: String? = null,
    // Optional credit breakdown (e.g. DeepSeek granted vs topped-up), persisted from the provider snapshot.
    val grantedBalance: String? = null,
    val toppedUpBalance: String? = null,
    val usedCount: Int? = null,
    val limitCount: Int? = null,
    val subLabel: String? = null,
    val modelBuckets: List<QuotaModelBucket> = emptyList(),
    // When true, the home usage chart aggregates this window's per-hour usage by summing
    // per-model used-fraction deltas instead of diffing usedPercent. Set by providers whose
    // usedPercent is not a usage total (e.g. Antigravity reports the worst single model).
    val usesModelBucketSum: Boolean = false,
) {
    val remainingPercent: Int? = usedPercent?.let { 100 - it.coerceIn(0, 100) }
    val displayPercent: Int? = remainingPercent
}

data class QuotaSnapshot(
    val snapshotId: SnapshotId,
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
    val providerAccountId: ProviderAccountId?,
    val fetchedAt: Instant,
    val source: QuotaSnapshotSource,
    val planType: String?,
    val windows: List<QuotaWindow>,
    val credits: Credits?,
    val responseDigest: String?,
)

data class Credits(
    val hasCredits: Boolean,
    val unlimited: Boolean,
    val balance: Double?,
)

internal fun QuotaWindow.canAlert(): Boolean = when {
    availability != QuotaWindowAvailability.Available -> false
    displayKind == QuotaWindowDisplayKind.Balance -> {
        val amount = balanceAmount?.toDoubleOrNull() ?: 0.0
        amount > 0.0
    }
    displayKind == QuotaWindowDisplayKind.UsageCount -> {
        usedCount != null && limitCount != null && usedCount > 0
    }
    else -> usedPercent in 0..100 && resetAt != null  // Percent, MultiModelFraction
}

/**
 * A depleted quota is still valid provider data. Keep it visible so the UI can show zero remaining
 * and, when supplied, the provider's reset time. Missing/failed/unsupported windows stay hidden.
 */
fun QuotaWindow.hasDisplayableQuotaValue(): Boolean =
    availability == QuotaWindowAvailability.Available ||
        availability == QuotaWindowAvailability.Depleted
