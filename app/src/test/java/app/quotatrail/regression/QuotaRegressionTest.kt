package app.quotatrail.regression

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.quota.CurrentQuotaStateFactory
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.refresh.QuotaError
import app.quotatrail.foundation.network.ProviderHttpClient
import app.quotatrail.presentation.home.HomeViewModel
import app.quotatrail.providers.claude.dto.ClaudeUsageResponseDto
import app.quotatrail.providers.claude.mapper.ClaudeUsageMapper
import app.quotatrail.providers.codex.dto.CodexUsageResponseDto
import app.quotatrail.providers.codex.mapper.CodexUsageMapper
import app.quotatrail.providers.common.auth.OAuthTokenClient
import app.quotatrail.surfaces.notification.AlertLevel
import app.quotatrail.surfaces.notification.AlertPolicy
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** Regressions for provider edge cases observed during release verification. */
class QuotaRegressionTest {
    private val now = Instant.parse("2026-09-04T00:00:00Z")
    private val accountId = LocalAccountId("release-review")

    private fun account(provider: String) = ProviderAccount.createNew(
        accountId, ProviderId(provider), null, "Review", now,
    )

    @Test
    fun otherBadRequestsAndMalformedErrorsDoNotForceRelogin() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val client = OAuthTokenClient(ProviderHttpClient(), server.url("/token").toString(), "test")
            for (body in listOf("""{"error":"invalid_request"}""", "<html>error</html>", "[]", """{"error":{}}""")) {
                server.enqueue(MockResponse.Builder().code(400).body(body).build())
                val result = client.refresh("synthetic") as OAuthTokenClient.Result.Failure
                assertTrue("Non-grant error should remain recoverable", result.error is QuotaError.Network)
            }
            server.enqueue(MockResponse.Builder().code(429).body("""{"error":"invalid_grant"}""").build())
            assertTrue((client.refresh("synthetic") as OAuthTokenClient.Result.Failure).error is QuotaError.Network)
        } finally {
            server.close()
        }
    }

    @Test
    fun exhaustedClaudeWindowStaysVisibleAndAlertDeduplicates() {
        val snapshot = ClaudeUsageMapper.map(
            ClaudeUsageResponseDto(five_hour = ClaudeUsageResponseDto.Window(100.0, "2026-09-04T05:00:00Z")),
            accountId, null, now, QuotaSnapshotSource.ManualRefresh,
        )
        val windowId = QuotaWindowId("claude_5h_window")
        val state = CurrentQuotaStateFactory().create(account("claude"), snapshot, null, now, windowId)
        val ui = HomeViewModel().mapToUiState(state)
        assertEquals(100, ui.quotaCards.single { it.windowId == windowId.value }.usedPercent)
        val policy = AlertPolicy()
        val event = policy.evaluate(state).single()
        assertEquals("0%", event.remainingText)
        assertTrue(policy.evaluate(state, alreadyNotified = setOf(event.key)).isEmpty())
    }

    @Test
    fun claudeExhaustedWindowShouldEmitLimitAlert() {
        val snapshot = ClaudeUsageMapper.map(
            ClaudeUsageResponseDto(five_hour = ClaudeUsageResponseDto.Window(
                utilization = 100.0, resets_at = "2026-09-04T05:00:00Z",
            )), accountId, null, now, QuotaSnapshotSource.ManualRefresh,
        )
        val windowId = QuotaWindowId("claude_5h_window")
        val state = CurrentQuotaStateFactory().create(
            account("claude"), snapshot, null, now, windowId,
        )
        val alerts = AlertPolicy().evaluate(state, enabledWindowIds = setOf(windowId))
        assertEquals("Exhausted Claude window must trigger the enabled limit alert", 1, alerts.size)
        assertEquals(AlertLevel.Limit, alerts.single().level)
    }

    @Test
    fun rejectedOAuthRefreshGrantShouldRequireLogin() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(400)
                .body("""{"error":"invalid_grant","error_description":"Refresh token revoked"}""")
                .build())
            val client = OAuthTokenClient(
                ProviderHttpClient(), server.url("/token").toString(), "review-client", useJsonBody = true,
            )
            val result = client.refresh("synthetic-review-token") as OAuthTokenClient.Result.Failure
            assertTrue("invalid_grant must be AuthRequired; actual: ${result.error}", result.error is QuotaError.AuthRequired)
            assertFalse(result.error.retryable)
        } finally {
            server.close()
        }
    }

    @Test
    fun weeklyOnlyCodexPlanShouldNotShowMissingFiveHourCard() {
        val dto = Json.decodeFromString<CodexUsageResponseDto>("""
            {"rate_limit":{"primary_window":{"used_percent":25,"limit_window_seconds":604800,"reset_at":1789084800}}}
        """.trimIndent())
        val snapshot = CodexUsageMapper().map(dto, accountId, null, now, QuotaSnapshotSource.ManualRefresh)
        val state = CurrentQuotaStateFactory().create(account("codex"), snapshot, null, now)
        val ui = HomeViewModel().mapToUiState(state)
        assertNotNull(ui.weeklyCard)
        assertNull("A weekly-only Codex plan should not render an unavailable 5h card", ui.fiveHourCard)
    }
}
