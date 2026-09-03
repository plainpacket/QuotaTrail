package app.quotatrail.surfaces.widget

import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.domain.quota.hasDisplayableQuotaValue
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.providers.ProviderRegistry

data class WidgetSlotProjection(
    val configuration: WidgetSlotConfiguration,
    val state: CurrentQuotaState,
)

class WidgetQuotaStateFactory(
    private val providerDisplayName: String = UNCONFIGURED_HEADER,
) {
    /** 微件未选账号时的引导态。 */
    fun unconfigured(hasAccounts: Boolean): WidgetQuotaState =
        WidgetQuotaState(
            status = WidgetQuotaStatus.NoAccount,
            providerName = providerDisplayName,
            providerId = null,
            localAccountId = null,
            accountName = null,
            tone = WidgetQuotaTone.Neutral,
            // Tapping an unconfigured widget opens the app's Home tab (where the no-account state already
            // surfaces an add-account entry). It must not deep-link to the retired provider-selection
            // screen; the no-accounts hint still tells the user to add an account in-app first.
            clickTarget = WidgetClickTarget.Home,
            fields = emptyList(),
            isUnconfigured = true,
            hasAccounts = hasAccounts,
        )

    fun create(
        state: CurrentQuotaState,
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        selectedWindowIds: List<String> = emptyList(),
    ): WidgetQuotaState {
        val account = state.account
        if (account == null) {
            return unconfigured(hasAccounts = false)
        }
        val fields = state.buildFields(selectedWindowIds, notificationPreferences)
        val firstField = fields.firstOrNull()
        return WidgetQuotaState(
            status = state.status.toWidgetStatus(),
            providerName = ProviderRegistry.displayNameFor(account.providerId),
            providerIconRes = ProviderRegistry.iconFor(account.providerId),
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
            accountName = account.displayName,
            tone = state.headerTone(firstField),
            clickTarget = WidgetClickTarget.Home,
            fields = fields,
            lastUpdatedAt = state.snapshot?.fetchedAt,
            isUnconfigured = false,
            hasAccounts = true,
        )
    }

    /** Builds one widget from independently configured account/window slots. */
    fun createFromSlots(
        projections: List<WidgetSlotProjection>,
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        hasAccounts: Boolean = true,
    ): WidgetQuotaState {
        val projectedFields = projections.mapNotNull { projection ->
            projection.state.buildField(
                windowId = projection.configuration.windowId,
                notificationPreferences = notificationPreferences,
            )
        }.take(WIDGET_MAX_FIELDS)
        if (projectedFields.isEmpty()) return unconfigured(hasAccounts)

        val accountKeys = projectedFields.mapNotNull { field ->
            val providerId = field.providerId ?: return@mapNotNull null
            val accountId = field.localAccountId ?: return@mapNotNull null
            providerId to accountId
        }.distinct()
        val singleProjection = projections.firstOrNull().takeIf { accountKeys.size == 1 }
        val statuses = projections.map { it.state.status }
        val status = statuses.maxByOrNull { it.widgetSeverity() }?.toWidgetStatus()
            ?: WidgetQuotaStatus.NoData
        val tone = projectedFields.maxByOrNull { it.tone.severity() }?.tone ?: WidgetQuotaTone.Neutral

        return WidgetQuotaState(
            status = status,
            providerName = singleProjection?.state?.account?.providerId
                ?.let(ProviderRegistry::displayNameFor)
                ?: UNCONFIGURED_HEADER,
            providerIconRes = singleProjection?.state?.account?.providerId
                ?.let(ProviderRegistry::iconFor),
            providerId = singleProjection?.state?.account?.providerId?.value,
            localAccountId = singleProjection?.state?.account?.localAccountId?.value,
            accountName = singleProjection?.state?.account?.displayName,
            tone = tone,
            clickTarget = WidgetClickTarget.Home,
            fields = projectedFields,
            refreshAccountIds = projectedFields.mapNotNull { it.localAccountId }.distinct(),
            lastUpdatedAt = projections.mapNotNull { it.state.snapshot?.fetchedAt }.minOrNull(),
            isUnconfigured = false,
            hasAccounts = true,
        )
    }

    private fun CurrentQuotaState.buildFields(
        selectedWindowIds: List<String>,
        notificationPreferences: NotificationPreferences,
    ): List<WidgetField> {
        val windows = snapshot?.windows.orEmpty()
        val selected = if (selectedWindowIds.isEmpty()) {
            windows
        } else {
            windows.filter { selectedWindowIds.contains(it.windowId.value) }
        }
        return selected
            .filter { it.isDisplayable() }
            .take(WIDGET_MAX_FIELDS)
            .map { window ->
                val isBalance = window.displayKind == QuotaWindowDisplayKind.Balance
                WidgetField(
                    windowId = window.windowId.value,
                    isBalance = isBalance,
                    percent = if (isBalance) null else window.displayPercent,
                    balanceAmount = if (isBalance) window.balanceAmount else null,
                    balanceCurrency = if (isBalance) window.balanceCurrency else null,
                    resetAt = window.resetAt,
                    tone = window.percentTone(notificationPreferences),
                    providerName = account?.providerId?.let(ProviderRegistry::displayNameFor),
                    providerId = account?.providerId?.value,
                    localAccountId = account?.localAccountId?.value,
                    accountName = account?.displayName,
                )
            }
    }

    private fun CurrentQuotaState.buildField(
        windowId: String,
        notificationPreferences: NotificationPreferences,
    ): WidgetField? {
        val account = account ?: return null
        val window = snapshot?.windows?.firstOrNull { it.windowId.value == windowId }
            ?.takeIf { it.isDisplayable() }
            ?: return null
        val isBalance = window.displayKind == QuotaWindowDisplayKind.Balance
        return WidgetField(
            windowId = window.windowId.value,
            isBalance = isBalance,
            percent = if (isBalance) null else window.displayPercent,
            balanceAmount = if (isBalance) window.balanceAmount else null,
            balanceCurrency = if (isBalance) window.balanceCurrency else null,
            resetAt = window.resetAt,
            tone = window.percentTone(notificationPreferences),
            providerName = ProviderRegistry.displayNameFor(account.providerId),
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
            accountName = account.displayName,
        )
    }

    private fun CurrentQuotaStatus.toWidgetStatus(): WidgetQuotaStatus =
        when (this) {
            CurrentQuotaStatus.Unauthenticated -> WidgetQuotaStatus.NoAccount
            CurrentQuotaStatus.Loading,
            CurrentQuotaStatus.Fresh -> WidgetQuotaStatus.Fresh
            CurrentQuotaStatus.PossiblyStale -> WidgetQuotaStatus.PossiblyStale
            CurrentQuotaStatus.Expired -> WidgetQuotaStatus.Expired
            CurrentQuotaStatus.AuthRequired -> WidgetQuotaStatus.AuthRequired
            CurrentQuotaStatus.ErrorWithLastKnownGood -> WidgetQuotaStatus.ErrorWithLastKnownGood
            CurrentQuotaStatus.NoData -> WidgetQuotaStatus.NoData
        }

    private fun CurrentQuotaState.headerTone(firstField: WidgetField?): WidgetQuotaTone =
        when (status) {
            CurrentQuotaStatus.Unauthenticated,
            CurrentQuotaStatus.PossiblyStale,
            CurrentQuotaStatus.Expired,
            CurrentQuotaStatus.NoData -> WidgetQuotaTone.Neutral
            CurrentQuotaStatus.AuthRequired -> WidgetQuotaTone.Danger
            CurrentQuotaStatus.ErrorWithLastKnownGood -> WidgetQuotaTone.Warning
            CurrentQuotaStatus.Loading,
            CurrentQuotaStatus.Fresh -> firstField?.tone ?: WidgetQuotaTone.Neutral
        }

    private fun QuotaWindow.percentTone(notificationPreferences: NotificationPreferences): WidgetQuotaTone {
        if (displayKind == QuotaWindowDisplayKind.Balance) return WidgetQuotaTone.Neutral
        val percent = displayPercent
        return when {
            availability == QuotaWindowAvailability.Depleted -> WidgetQuotaTone.Danger
            availability != QuotaWindowAvailability.Available || percent == null -> WidgetQuotaTone.Neutral
            percent <= notificationPreferences.limitThreshold -> WidgetQuotaTone.Danger
            percent <= notificationPreferences.warningThreshold -> WidgetQuotaTone.Danger
            percent <= notificationPreferences.cautionThreshold -> WidgetQuotaTone.Warning
            else -> WidgetQuotaTone.Success
        }
    }

    private fun QuotaWindow.isDisplayable(): Boolean =
        when (displayKind) {
            QuotaWindowDisplayKind.Balance ->
                hasDisplayableQuotaValue() && !balanceAmount.isNullOrBlank()
            else ->
                hasDisplayableQuotaValue() && displayPercent != null
        }

    private fun CurrentQuotaStatus.widgetSeverity(): Int = when (this) {
        CurrentQuotaStatus.Unauthenticated -> 7
        CurrentQuotaStatus.AuthRequired -> 6
        CurrentQuotaStatus.ErrorWithLastKnownGood -> 5
        CurrentQuotaStatus.Expired -> 4
        CurrentQuotaStatus.PossiblyStale -> 3
        CurrentQuotaStatus.NoData -> 2
        CurrentQuotaStatus.Loading -> 1
        CurrentQuotaStatus.Fresh -> 0
    }

    private fun WidgetQuotaTone.severity(): Int = when (this) {
        WidgetQuotaTone.Neutral -> 0
        WidgetQuotaTone.Success -> 1
        WidgetQuotaTone.Warning -> 2
        WidgetQuotaTone.Danger -> 3
    }

    private companion object {
        // Header shown on the unconfigured/no-account widget: app branding, not a single provider,
        // since QuotaTrail now supports multiple AI providers.
        const val UNCONFIGURED_HEADER = "QuotaTrail"
    }
}
