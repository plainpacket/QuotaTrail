package app.quotatrail.surfaces.notification

import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus

class AccountErrorPolicy(
    private val repeatedFailureThreshold: Int = REPEATED_FAILURE_THRESHOLD,
) {
    fun evaluate(
        state: CurrentQuotaState,
        consecutiveFailureCount: Int,
    ): AccountErrorNotificationEvent? =
        when {
            state.status == CurrentQuotaStatus.AuthRequired ->
                AccountErrorNotificationEvent(AccountErrorNotificationReason.AuthRequired)
            consecutiveFailureCount >= repeatedFailureThreshold ->
                AccountErrorNotificationEvent(AccountErrorNotificationReason.RepeatedRefreshFailure)
            else -> null
        }

    companion object {
        const val REPEATED_FAILURE_THRESHOLD = 3
    }
}
