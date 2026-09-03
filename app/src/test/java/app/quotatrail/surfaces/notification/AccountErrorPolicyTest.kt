package app.quotatrail.surfaces.notification

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.CurrentQuotaFreshness
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.refresh.QuotaError
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountErrorPolicyTest {
    private val policy = AccountErrorPolicy()

    @Test
    fun `auth-required state emits account error notification`() {
        val event = policy.evaluate(
            state = state(
                status = CurrentQuotaStatus.AuthRequired,
                error = QuotaError.AuthRequired(httpStatus = 401, diagnosticsDigest = "safe-digest"),
            ),
            consecutiveFailureCount = 0,
        )

        assertEquals(AccountErrorNotificationReason.AuthRequired, event?.reason)
    }

    @Test
    fun `three consecutive failed attempts emit repeated-failure notification`() {
        val event = policy.evaluate(
            state = state(status = CurrentQuotaStatus.ErrorWithLastKnownGood),
            consecutiveFailureCount = 3,
        )

        assertEquals(AccountErrorNotificationReason.RepeatedRefreshFailure, event?.reason)
    }

    @Test
    fun `fewer than three consecutive failed attempts do not emit repeated-failure notification`() {
        val event = policy.evaluate(
            state = state(status = CurrentQuotaStatus.ErrorWithLastKnownGood),
            consecutiveFailureCount = 2,
        )

        assertNull(event)
    }

    private fun state(
        status: CurrentQuotaStatus,
        error: QuotaError? = null,
    ): CurrentQuotaState =
        CurrentQuotaState(
            status = status,
            freshness = CurrentQuotaFreshness.Fresh,
            account = ProviderAccount.createNew(
                localAccountId = LocalAccountId("local-1"),
                providerId = ProviderId("codex"),
                providerAccountId = ProviderAccountId("acct-local-1"),
                displayName = "Codex Main",
                now = Instant.parse("2026-05-23T09:00:00Z"),
            ),
            snapshot = null,
            latestAttempt = null,
            primaryWindow = null,
            secondaryWindows = emptyList(),
            primaryWindowCanAlert = false,
            error = error,
        )
}
