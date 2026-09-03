package app.quotatrail.surfaces.notification

import android.app.PendingIntent
import androidx.annotation.StringRes
import app.quotatrail.R
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.hasDisplayableQuotaValue
import app.quotatrail.providers.ProviderRegistry
import app.quotatrail.presentation.quota.formatProviderBalance
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NotificationRequestOptions(
    val notificationPermissionAvailable: Boolean,
    val statusNotificationEnabled: Boolean = false,
    val quotaAlertsEnabled: Boolean = true,
    val accountErrorsEnabled: Boolean = true,
)

data class NotificationText(
    @field:StringRes val resourceId: Int,
    val formatArgs: List<Any> = emptyList(),
)

data class NotificationPendingIntentMetadata(
    val requestCode: Int,
    val flags: Int,
    val destination: NotificationDestination = NotificationDestination.Home,
    val externalUrl: String? = null,
)

enum class NotificationDestination {
    Home,
    AddAccount,
    ExternalUrl,
    AppUpdate,
}

enum class NotificationActionType {
    CopyDeviceCode,
    RefreshAllQuota,
}

data class NotificationActionIntentMetadata(
    val requestCode: Int,
    val flags: Int,
    val action: NotificationActionType,
    val attemptId: String? = null,
)

data class NotificationAction(
    val title: NotificationText,
    val intent: NotificationActionIntentMetadata,
)

enum class AccountErrorNotificationReason {
    AuthRequired,
    RepeatedRefreshFailure,
}

data class AccountErrorNotificationEvent(
    val reason: AccountErrorNotificationReason,
)

data class NotificationRequest(
    val notificationId: Int,
    val channelId: String,
    val title: NotificationText,
    val body: NotificationText,
    val publicTitle: NotificationText? = null,
    val pendingIntent: NotificationPendingIntentMetadata,
    val ongoing: Boolean,
    val actions: List<NotificationAction> = emptyList(),
    val expandedLines: List<NotificationText> = emptyList(),
    val timeoutAfterMillis: Long? = null,
)

class NotificationCoordinator(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val renewalFormatter = DateTimeFormatter
        .ofPattern("MM/dd, h:mm a", Locale.ENGLISH)
        .withZone(zoneId)

    fun buildRequests(
        state: CurrentQuotaState,
        statusStates: List<CurrentQuotaState> = listOf(state),
        alertEvents: List<QuotaAlertEvent> = emptyList(),
        options: NotificationRequestOptions,
        accountErrorEvent: AccountErrorNotificationEvent? = null,
    ): List<NotificationRequest> {
        if (!options.notificationPermissionAvailable) {
            return emptyList()
        }

        return buildList {
            if (options.statusNotificationEnabled) {
                add(statusRequest(statusStates))
            }
            if (options.quotaAlertsEnabled) {
                addAll(alertEvents.map(::quotaAlertRequest))
            }
            if (options.accountErrorsEnabled) {
                accountErrorRequest(state, accountErrorEvent)?.let(::add)
            }
        }
    }

    private fun statusRequest(states: List<CurrentQuotaState>): NotificationRequest {
        val visibleStates = states
            .filter { it.account != null }
            .take(MAX_STATUS_ACCOUNTS)
        val fallbackState = states.firstOrNull()
        val displayStates = visibleStates.ifEmpty { listOfNotNull(fallbackState) }
        val singleState = displayStates.singleOrNull()
        val aggregate = displayStates.size > 1
        val title = when {
            aggregate -> multiStatusTitle(displayStates)
            singleState != null -> identityTitle(singleState, fallbackResId = R.string.notification_status_title)
            else -> NotificationText(R.string.notification_status_title)
        }
        val body = when {
            aggregate -> multiStatusBody(displayStates)
            singleState != null -> statusBody(singleState)
            else -> NotificationText(R.string.notification_status_body_no_data)
        }

        return NotificationRequest(
            notificationId = STATUS_NOTIFICATION_ID,
            channelId = NotificationChannels.STATUS_CHANNEL_ID,
            title = title,
            body = body,
            publicTitle = title.takeIf { aggregate },
            pendingIntent = homePendingIntent(STATUS_PENDING_INTENT_REQUEST_CODE),
            ongoing = true,
            actions = listOf(refreshAllAction()),
            expandedLines = statusExpandedLines(displayStates),
        )
    }

    private fun statusExpandedLines(states: List<CurrentQuotaState>): List<NotificationText> {
        val aggregate = states.size > 1
        return states.flatMap { state ->
            val windowLines = dualWindowStatusLines(state)
            val lines = windowLines.ifEmpty { listOf(statusBody(state)) }
            if (!aggregate) {
                lines
            } else {
                val providerName = state.account
                    ?.providerId
                    ?.let(ProviderRegistry::displayNameFor)
                    ?: return@flatMap lines
                lines.map { line ->
                    NotificationText(
                        resourceId = R.string.notification_status_provider_line_format,
                        formatArgs = listOf(providerName, line),
                    )
                }
            }
        }
    }

    private fun refreshAllAction(): NotificationAction =
        NotificationAction(
            title = NotificationText(R.string.notification_status_action_refresh_all),
            intent = NotificationActionIntentMetadata(
                requestCode = STATUS_REFRESH_ALL_PENDING_INTENT_REQUEST_CODE,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                action = NotificationActionType.RefreshAllQuota,
            ),
        )

    private fun multiStatusTitle(states: List<CurrentQuotaState>): NotificationText =
        NotificationText(
            resourceId = R.string.notification_status_two_accounts_title_format,
            formatArgs = states.take(2).map(::providerQuotaSummary),
        )

    private fun multiStatusBody(states: List<CurrentQuotaState>): NotificationText =
        NotificationText(
            resourceId = R.string.notification_status_two_accounts_body_format,
            formatArgs = states.take(2).map(::providerStatusLine),
        )

    private fun providerQuotaSummary(state: CurrentQuotaState): NotificationText {
        val providerName = state.account
            ?.providerId
            ?.let(ProviderRegistry::displayNameFor)
            ?: return NotificationText(R.string.notification_status_title)
        val quota = quotaText(state)
            ?: return NotificationText(
                resourceId = R.string.notification_status_provider_only_format,
                formatArgs = listOf(providerName),
            )
        return NotificationText(
            resourceId = R.string.notification_status_provider_quota_format,
            formatArgs = listOf(providerName, quota),
        )
    }

    private fun providerStatusLine(state: CurrentQuotaState): NotificationText {
        val providerName = state.account
            ?.providerId
            ?.let(ProviderRegistry::displayNameFor)
            ?: return statusBody(state)
        return NotificationText(
            resourceId = R.string.notification_status_provider_line_format,
            formatArgs = listOf(providerName, statusBody(state)),
        )
    }

    /**
     * "<provider>·<account>" so the persistent notification names which connected account it tracks
     * (the quota figure now lives in the body). Falls back to a generic title when no account is set.
     */
    private fun identityTitle(state: CurrentQuotaState, @StringRes fallbackResId: Int): NotificationText {
        val account = state.account ?: return NotificationText(resourceId = fallbackResId)
        return NotificationText(
            resourceId = R.string.notification_status_title_identity_format,
            formatArgs = listOf(
                ProviderRegistry.displayNameFor(account.providerId),
                account.displayName,
            ),
        )
    }

    /** Body pairs the remaining-quota figure (when known) with the freshness/status clause. */
    private fun statusBody(state: CurrentQuotaState): NotificationText {
        val statusText = NotificationText(statusBodyResource(state.status))
        val quota = dualWindowQuotaText(state) ?: quotaText(state) ?: return statusText
        return NotificationText(
            resourceId = R.string.notification_status_body_with_quota_format,
            formatArgs = listOf(quota, statusText),
        )
    }

    private fun dualWindowQuotaText(state: CurrentQuotaState): NotificationText? {
        val lines = dualWindowStatusLines(state)
        if (lines.isEmpty()) return null
        return NotificationText(
            resourceId = R.string.notification_status_two_windows_format,
            formatArgs = lines,
        )
    }

    private fun dualWindowStatusLines(state: CurrentQuotaState): List<NotificationText> {
        val providerId = state.account?.providerId ?: return emptyList()
        if (providerId != ProviderRegistry.CLAUDE && providerId != ProviderRegistry.CODEX) return emptyList()
        val windows = state.snapshot?.windows.orEmpty()
        val fiveHour = windows.firstOrNull { it.isFiveHourStatusWindow() }
        val sevenDay = windows.firstOrNull { it.isSevenDayStatusWindow() }
        return listOf(
            statusWindowText(R.string.notification_status_window_five_hour, fiveHour),
            statusWindowText(R.string.notification_status_window_seven_day, sevenDay),
        )
    }

    private fun statusWindowText(
        @StringRes labelResId: Int,
        window: QuotaWindow?,
    ): NotificationText {
        val percent = window
            ?.takeIf { it.hasDisplayableQuotaValue() && it.displayKind == QuotaWindowDisplayKind.Percent }
            ?.displayPercent
        val label = NotificationText(labelResId)
        return if (percent == null) {
            NotificationText(
                resourceId = R.string.notification_status_window_unavailable_format,
                formatArgs = listOf(label),
            )
        } else if (window.resetAt == null) {
            NotificationText(
                resourceId = R.string.notification_status_window_percent_renewal_unavailable_format,
                formatArgs = listOf(label, percent),
            )
        } else {
            NotificationText(
                resourceId = R.string.notification_status_window_percent_format,
                formatArgs = listOf(label, percent, renewalFormatter.format(window.resetAt)),
            )
        }
    }

    private fun QuotaWindow.isFiveHourStatusWindow(): Boolean =
        windowId.value in FIVE_HOUR_WINDOW_IDS || limitWindowSeconds == FIVE_HOUR_SECONDS

    private fun QuotaWindow.isSevenDayStatusWindow(): Boolean =
        windowId.value in SEVEN_DAY_WINDOW_IDS ||
            (limitWindowSeconds == SEVEN_DAY_SECONDS && windowId.value !in MODEL_SPECIFIC_SEVEN_DAY_WINDOW_IDS)

    private fun quotaText(state: CurrentQuotaState): NotificationText? {
        val window = state.primaryWindow ?: return null
        if (window.displayKind == QuotaWindowDisplayKind.Balance) {
            val balance = formatProviderBalance(window.balanceAmount, window.balanceCurrency) ?: return null
            return NotificationText(
                resourceId = R.string.notification_status_title_balance_format,
                formatArgs = listOf(balance),
            )
        }
        val percent = window.displayPercent ?: return null
        return NotificationText(
            resourceId = R.string.notification_status_title_percent_format,
            formatArgs = listOf(percent),
        )
    }

    @StringRes
    private fun statusBodyResource(status: CurrentQuotaStatus): Int =
        when (status) {
            CurrentQuotaStatus.Unauthenticated -> R.string.notification_status_body_unauthenticated
            CurrentQuotaStatus.Loading -> R.string.notification_status_body_loading
            CurrentQuotaStatus.Fresh -> R.string.notification_status_body_fresh
            CurrentQuotaStatus.PossiblyStale -> R.string.notification_status_body_possibly_stale
            CurrentQuotaStatus.Expired -> R.string.notification_status_body_expired
            CurrentQuotaStatus.AuthRequired -> R.string.notification_status_body_auth_required
            CurrentQuotaStatus.ErrorWithLastKnownGood -> R.string.notification_status_body_error
            CurrentQuotaStatus.NoData -> R.string.notification_status_body_no_data
        }

    private fun quotaAlertRequest(event: QuotaAlertEvent): NotificationRequest =
        when (event.level) {
            AlertLevel.Caution -> NotificationRequest(
                notificationId = quotaAlertNotificationId(event),
                channelId = NotificationChannels.QUOTA_ALERTS_CHANNEL_ID,
                title = NotificationText(
                    resourceId = R.string.notification_alert_warning_title_percent_format,
                    formatArgs = listOf(event.remainingText),
                ),
                body = NotificationText(resourceId = R.string.notification_alert_warning_body),
                pendingIntent = homePendingIntent(QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE),
                ongoing = false,
            )
            AlertLevel.Warning -> NotificationRequest(
                notificationId = quotaAlertNotificationId(event),
                channelId = NotificationChannels.QUOTA_ALERTS_CHANNEL_ID,
                title = NotificationText(
                    resourceId = R.string.notification_alert_warning_title_percent_format,
                    formatArgs = listOf(event.remainingText),
                ),
                body = NotificationText(resourceId = R.string.notification_alert_warning_body),
                pendingIntent = homePendingIntent(QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE),
                ongoing = false,
            )
            AlertLevel.Limit -> NotificationRequest(
                notificationId = quotaAlertNotificationId(event),
                channelId = NotificationChannels.QUOTA_ALERTS_CHANNEL_ID,
                title = NotificationText(resourceId = R.string.notification_alert_limit_title),
                body = NotificationText(resourceId = R.string.notification_alert_limit_body),
                pendingIntent = homePendingIntent(QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE),
                ongoing = false,
            )
        }

    private fun accountErrorRequest(
        state: CurrentQuotaState,
        accountErrorEvent: AccountErrorNotificationEvent?,
    ): NotificationRequest? {
        val reason = when {
            state.status == CurrentQuotaStatus.AuthRequired -> AccountErrorNotificationReason.AuthRequired
            accountErrorEvent?.reason == AccountErrorNotificationReason.RepeatedRefreshFailure ->
                AccountErrorNotificationReason.RepeatedRefreshFailure
            else -> return null
        }
        val bodyResId = when (reason) {
            AccountErrorNotificationReason.AuthRequired -> R.string.notification_account_error_body_auth_required
            AccountErrorNotificationReason.RepeatedRefreshFailure -> R.string.notification_account_error_body_refresh_failed
        }

        return NotificationRequest(
            notificationId = accountErrorNotificationId(state),
            channelId = NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID,
            title = identityTitle(state, fallbackResId = R.string.notification_account_error_title),
            body = NotificationText(resourceId = bodyResId),
            pendingIntent = homePendingIntent(ACCOUNT_ERROR_PENDING_INTENT_REQUEST_CODE),
            ongoing = false,
        )
    }

    private fun quotaAlertNotificationId(event: QuotaAlertEvent): Int =
        stableNotificationId(
            base = QUOTA_ALERT_NOTIFICATION_ID_BASE,
            event.key.providerId.value,
            event.key.localAccountId.value,
            event.windowId.value,
            event.level.name,
        )

    private fun accountErrorNotificationId(state: CurrentQuotaState): Int {
        val account = state.account ?: return ACCOUNT_ERROR_NOTIFICATION_ID_BASE
        return stableNotificationId(
            base = ACCOUNT_ERROR_NOTIFICATION_ID_BASE,
            account.providerId.value,
            account.localAccountId.value,
        )
    }

    private fun stableNotificationId(
        base: Int,
        vararg parts: String,
    ): Int =
        base + Math.floorMod(parts.joinToString(separator = "|").hashCode(), NOTIFICATION_ID_BUCKET_SIZE)

    private fun homePendingIntent(requestCode: Int): NotificationPendingIntentMetadata =
        NotificationPendingIntentMetadata(
            requestCode = requestCode,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val STATUS_NOTIFICATION_ID = 1_001
        const val AUTH_LOGIN_NOTIFICATION_ID = 3_002
        const val APP_UPDATE_NOTIFICATION_ID = 4_001
        private const val QUOTA_ALERT_NOTIFICATION_ID_BASE = 20_000
        private const val ACCOUNT_ERROR_NOTIFICATION_ID_BASE = 30_000
        private const val NOTIFICATION_ID_BUCKET_SIZE = 10_000
        private const val STATUS_PENDING_INTENT_REQUEST_CODE = 11
        private const val STATUS_REFRESH_ALL_PENDING_INTENT_REQUEST_CODE = 12
        private const val QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE = 21
        private const val ACCOUNT_ERROR_PENDING_INTENT_REQUEST_CODE = 31
        private const val MAX_STATUS_ACCOUNTS = 2
        private const val FIVE_HOUR_SECONDS = 5 * 60 * 60
        private const val SEVEN_DAY_SECONDS = 7 * 24 * 60 * 60
        private val FIVE_HOUR_WINDOW_IDS = setOf("five_hour", "claude_5h_window")
        private val SEVEN_DAY_WINDOW_IDS = setOf("weekly", "claude_7d_window")
        private val MODEL_SPECIFIC_SEVEN_DAY_WINDOW_IDS = setOf(
            "claude_7d_opus_window",
            "claude_7d_sonnet_window",
        )
    }
}
