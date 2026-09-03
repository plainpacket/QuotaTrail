package app.quotatrail.providers.zaibalance.mapper

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
import app.quotatrail.providers.zaibalance.dto.ZaiBalanceResponseDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

object ZaiBalanceMapper {
    fun map(
        dto: ZaiBalanceResponseDto,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        fetchedAt: Instant,
        source: QuotaSnapshotSource,
    ): QuotaSnapshot {
        // A successful fetch always yields a balance window — even at zero balance — so the account
        // never reads as "No quota yet" on Home. Reuses windowId "balance" so the shared label
        // resolver (quotaWindowLabelRes) renders the localized "余额"/"Balance" title for free.
        val window = QuotaWindow(
            windowId = QuotaWindowId("balance"),
            titleKey = "zai_balance",
            usedPercent = null,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.Balance,
            balanceAmount = formatAmount(dto.availableBalance),
            balanceCurrency = "CNY",
            subLabel = null,
            grantedBalance = null,
            toppedUpBalance = null,
        )
        return QuotaSnapshot(
            snapshotId = SnapshotId("zaibal_${fetchedAt}"),
            providerId = ZAI_BALANCE_PROVIDER_ID,
            localAccountId = localAccountId,
            providerAccountId = providerAccountId,
            fetchedAt = fetchedAt,
            source = source,
            planType = null,
            windows = listOf(window),
            credits = null,
            responseDigest = null,
        )
    }

    // formatProviderBalance() prints the stored string verbatim after the ¥ symbol, so format to a
    // clean 2-decimal plain string here (handles scientific-notation inputs like 0E-9 -> "0.00").
    private fun formatAmount(value: Double?): String =
        BigDecimal.valueOf(value ?: 0.0).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private val ZAI_BALANCE_PROVIDER_ID = ProviderId("zai_balance")
}
