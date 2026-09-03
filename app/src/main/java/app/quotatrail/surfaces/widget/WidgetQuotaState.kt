package app.quotatrail.surfaces.widget

import java.time.Instant

enum class WidgetQuotaStatus {
    NoAccount,
    Fresh,
    PossiblyStale,
    Expired,
    AuthRequired,
    ErrorWithLastKnownGood,
    NoData,
}

enum class WidgetQuotaTone {
    Neutral,
    Success,
    Warning,
    Danger,
}

enum class WidgetClickTarget {
    Home,
    AddAccount,
}

/** One independently configured widget slot. Balance windows show money; others show remaining percent. */
data class WidgetField(
    val windowId: String,
    val isBalance: Boolean,
    val percent: Int?,
    val balanceAmount: String?,
    val balanceCurrency: String?,
    val resetAt: Instant?,
    val tone: WidgetQuotaTone,
    val providerName: String? = null,
    val providerId: String? = null,
    val localAccountId: String? = null,
    val accountName: String? = null,
)

data class WidgetQuotaState(
    val status: WidgetQuotaStatus,
    val providerName: String,
    val providerId: String?,
    val localAccountId: String?,
    val accountName: String?,
    val tone: WidgetQuotaTone,
    val clickTarget: WidgetClickTarget,
    val fields: List<WidgetField> = emptyList(),
    /** Accounts represented by the visible slots; refresh de-duplicates this list. */
    val refreshAccountIds: List<String> =
        (fields.mapNotNull { it.localAccountId } + listOfNotNull(localAccountId)).distinct(),
    /** Time of the latest successfully fetched quota snapshot shown by this widget. */
    val lastUpdatedAt: Instant? = null,
    // True when the widget has no configured slot and should show setup guidance.
    val isUnconfigured: Boolean = false,
    // Distinguishes an empty app from an app with accounts awaiting widget configuration.
    val hasAccounts: Boolean = true,
    @get:androidx.annotation.DrawableRes val providerIconRes: Int? = null,
)
