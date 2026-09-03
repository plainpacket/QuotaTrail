package app.quotatrail.surfaces.notification

import android.app.PendingIntent
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.CurrentQuotaFreshness
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.refresh.QuotaError
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class NotificationCoordinatorTest {
    private val orchestrator = NotificationCoordinator(zoneId = ZoneOffset.UTC)
    private val resetAt = Instant.parse("2026-05-23T17:00:00Z")

    @Test
    fun `channel ids match spec`() {
        assertEquals("quota_status", NotificationChannels.STATUS_CHANNEL_ID)
        assertEquals("quota_alerts", NotificationChannels.QUOTA_ALERTS_CHANNEL_ID)
        assertEquals("account_errors", NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID)
        assertEquals(
            listOf("quota_status", "quota_alerts", "account_errors", "app_updates"),
            NotificationChannels.definitions.map { it.id },
        )
    }

    @Test
    fun `created Android channels keep status quiet and alerts visible`() {
        val context = RuntimeEnvironment.getApplication()

        NotificationChannels.createAll(context)

        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        val statusChannel = notificationManager.getNotificationChannel(NotificationChannels.STATUS_CHANNEL_ID)
        val alertChannel = notificationManager.getNotificationChannel(NotificationChannels.QUOTA_ALERTS_CHANNEL_ID)
        val accountErrorChannel = notificationManager.getNotificationChannel(NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID)

        assertNotNull(statusChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, statusChannel.importance)
        assertFalse(statusChannel.canShowBadge())
        assertNull(statusChannel.sound)
        assertFalse(statusChannel.shouldVibrate())
        assertNotNull(alertChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, alertChannel.importance)
        assertTrue(alertChannel.canShowBadge())
        assertNotNull(accountErrorChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, accountErrorChannel.importance)
        assertTrue(accountErrorChannel.canShowBadge())
    }

    @Test
    fun `permission unavailable produces no notification requests`() {
        val requests = orchestrator.buildRequests(
            state = state(),
            alertEvents = listOf(alertEvent()),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = false,
                statusNotificationEnabled = true,
                quotaAlertsEnabled = true,
                accountErrorsEnabled = true,
            ),
        )

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `disabled status notification produces no status request`() {
        val requests = orchestrator.buildRequests(
            state = state(),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = false,
                quotaAlertsEnabled = true,
                accountErrorsEnabled = true,
            ),
        )

        assertFalse(requests.any { it.channelId == NotificationChannels.STATUS_CHANNEL_ID })
    }

    @Test
    fun `alert and account error requests use expected channels`() {
        val requests = orchestrator.buildRequests(
            state = state(
                status = CurrentQuotaStatus.AuthRequired,
                error = QuotaError.AuthRequired(httpStatus = 401, diagnosticsDigest = "safe-digest"),
            ),
            alertEvents = listOf(alertEvent()),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = false,
                quotaAlertsEnabled = true,
                accountErrorsEnabled = true,
            ),
        )

        assertTrue(requests.any { it.channelId == NotificationChannels.QUOTA_ALERTS_CHANNEL_ID })
        assertTrue(requests.any { it.channelId == NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID })
    }

    @Test
    fun `first transient refresh failure does not create account error request`() {
        val requests = orchestrator.buildRequests(
            state = state(
                status = CurrentQuotaStatus.ErrorWithLastKnownGood,
                error = QuotaError.Network(diagnosticsDigest = "safe-digest"),
            ),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                accountErrorsEnabled = true,
            ),
        )

        assertFalse(requests.any { it.channelId == NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID })
    }

    @Test
    fun `repeated refresh failure signal creates account error request`() {
        val requests = orchestrator.buildRequests(
            state = state(
                status = CurrentQuotaStatus.ErrorWithLastKnownGood,
                error = QuotaError.Network(diagnosticsDigest = "safe-digest"),
            ),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                accountErrorsEnabled = true,
            ),
            accountErrorEvent = AccountErrorNotificationEvent(AccountErrorNotificationReason.RepeatedRefreshFailure),
        )

        assertTrue(requests.any { it.channelId == NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID })
    }

    @Test
    fun `warning alert notification id is stable when threshold changes`() {
        val firstRequest = orchestrator.buildRequests(
            state = state(),
            alertEvents = listOf(alertEvent(threshold = 8.0, remainingText = "8%")),
            options = NotificationRequestOptions(notificationPermissionAvailable = true),
        ).single { it.channelId == NotificationChannels.QUOTA_ALERTS_CHANNEL_ID }
        val secondRequest = orchestrator.buildRequests(
            state = state(),
            alertEvents = listOf(alertEvent(threshold = 12.0, remainingText = "8%")),
            options = NotificationRequestOptions(notificationPermissionAvailable = true),
        ).single { it.channelId == NotificationChannels.QUOTA_ALERTS_CHANNEL_ID }

        assertEquals(firstRequest.notificationId, secondRequest.notificationId)
    }

    @Test
    fun `quota alert notification id is stable per account window and level`() {
        val baseRequest = quotaAlertRequest(alertEvent())
        val thresholdChangedRequest = quotaAlertRequest(alertEvent(threshold = 12.0))
        val otherAccountRequest = quotaAlertRequest(alertEvent(localAccountId = "local-2"))
        val otherWindowRequest = quotaAlertRequest(alertEvent(windowId = QuotaWindowId("weekly")))
        val limitRequest = quotaAlertRequest(
            alertEvent(
                threshold = 0.0,
                remainingText = "0%",
                level = AlertLevel.Limit,
            ),
        )

        assertEquals(baseRequest.notificationId, thresholdChangedRequest.notificationId)
        assertNotEquals(baseRequest.notificationId, otherAccountRequest.notificationId)
        assertNotEquals(baseRequest.notificationId, otherWindowRequest.notificationId)
        assertNotEquals(baseRequest.notificationId, limitRequest.notificationId)
    }

    @Test
    fun `auth-required account error notification id is stable per account`() {
        val firstRequest = accountErrorRequest(localAccountId = "local-1")
        val repeatRequest = accountErrorRequest(localAccountId = "local-1")
        val otherAccountRequest = accountErrorRequest(localAccountId = "local-2")

        assertEquals(firstRequest.notificationId, repeatRequest.notificationId)
        assertNotEquals(firstRequest.notificationId, otherAccountRequest.notificationId)
    }

    @Test
    fun `repeated failure account error notification id is stable per account`() {
        val firstRequest = repeatedFailureAccountErrorRequest(localAccountId = "local-1")
        val repeatRequest = repeatedFailureAccountErrorRequest(localAccountId = "local-1")
        val otherAccountRequest = repeatedFailureAccountErrorRequest(localAccountId = "local-2")

        assertEquals(firstRequest.notificationId, repeatRequest.notificationId)
        assertNotEquals(firstRequest.notificationId, otherAccountRequest.notificationId)
    }

    @Test
    fun `notification content does not expose diagnostic secret fields`() {
        val forbiddenTerms = forbiddenDiagnosticTerms()
        // The account display name is intentionally user-facing now (it titles the notification), so
        // secrets are injected only through the digest fields, which must never reach rendered text.
        val requests = orchestrator.buildRequests(
            state = state(
                status = CurrentQuotaStatus.AuthRequired,
                accountDisplayName = "Codex Main",
                responseDigest = forbiddenTerms.take(4).joinToString(separator = " "),
                error = QuotaError.AuthRequired(
                    httpStatus = 401,
                    diagnosticsDigest = forbiddenTerms.joinToString(separator = " "),
                ),
            ),
            alertEvents = listOf(alertEvent()),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
                quotaAlertsEnabled = true,
                accountErrorsEnabled = true,
            ),
        )

        val exposedContent = requests.joinToString(separator = "\n") { it.renderedContentForTest() }
        forbiddenTerms.forEach { forbidden ->
            assertFalse("Forbidden notification content leaked: $forbidden", exposedContent.contains(forbidden))
        }
    }

    @Test
    fun `status notification title names provider and account with quota in body`() {
        val statusRequest = orchestrator.buildRequests(
            state = state(accountDisplayName = "Work account"),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.STATUS_CHANNEL_ID }

        val title = statusRequest.title.renderedTextForTest()
        val body = statusRequest.body.renderedTextForTest()
        assertTrue("title should name the provider: $title", title.contains("Codex"))
        assertTrue("title should name the account: $title", title.contains("Work account"))
        // 62% used → 38% remaining is the figure shown.
        assertTrue("body should carry the quota figure: $body", body.contains("38"))
    }

    @Test
    fun `multi account status notification shows Claude and Codex in one ongoing request`() {
        val codexState = state(
            providerId = "codex",
            localAccountId = "codex-local",
            accountDisplayName = "Codex account",
            usedPercent = 62,
            weeklyUsedPercent = 28,
        )
        val claudeState = state(
            providerId = "claude",
            localAccountId = "claude-local",
            accountDisplayName = "Claude account",
            usedPercent = 100,
            weeklyUsedPercent = 56,
        )

        val request = orchestrator.buildRequests(
            state = codexState,
            statusStates = listOf(claudeState, codexState),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.STATUS_CHANNEL_ID }

        val title = request.title.renderedTextForTest()
        val body = request.body.renderedTextForTest()
        assertTrue(request.ongoing)
        assertTrue("collapsed title should show Claude quota: $title", title.contains("Claude 0%"))
        assertTrue("collapsed title should show Codex quota: $title", title.contains("Codex 38%"))
        assertTrue("expanded body should show Claude: $body", body.contains("Claude"))
        assertTrue("expanded body should show Codex: $body", body.contains("Codex"))
        assertTrue(
            "Claude line should show exhausted 5h quota and renewal: $body",
            body.contains("5h 0% · Renews 05/23, 5:00 PM"),
        )
        assertTrue(
            "Claude line should show 7-day quota and renewal: $body",
            body.contains("7-day 44% · Renews 05/29, 12:00 AM"),
        )
        assertTrue(
            "Codex line should show 5h quota and renewal: $body",
            body.contains("5h 38% · Renews 05/23, 5:00 PM"),
        )
        assertTrue(
            "Codex line should show 7-day quota and renewal: $body",
            body.contains("7-day 72% · Renews 05/29, 12:00 AM"),
        )
        assertFalse("aggregate notification must not expose Claude alias", body.contains("Claude account"))
        assertFalse("aggregate notification must not expose Codex alias", body.contains("Codex account"))
        assertEquals(1, request.actions.size)
        assertEquals(NotificationActionType.RefreshAllQuota, request.actions.single().intent.action)
        assertEquals("Refresh all", request.actions.single().title.renderedTextForTest())
        assertEquals(4, request.expandedLines.size)
        assertEquals(
            "Claude · 5h 0% · Renews 05/23, 5:00 PM",
            request.expandedLines.first().renderedTextForTest(),
        )
    }

    @Test
    fun `single account status body also shows both quota windows`() {
        val request = orchestrator.buildRequests(
            state = state(usedPercent = 25, weeklyUsedPercent = 40),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.STATUS_CHANNEL_ID }

        val body = request.body.renderedTextForTest()
        assertTrue(body.contains("5h 75% · Renews 05/23, 5:00 PM"))
        assertTrue(body.contains("7-day 60% · Renews 05/29, 12:00 AM"))
        assertEquals(
            listOf(
                "5h 75% · Renews 05/23, 5:00 PM",
                "7-day 60% · Renews 05/29, 12:00 AM",
            ),
            request.expandedLines.map { it.renderedTextForTest() },
        )
        assertEquals(NotificationActionType.RefreshAllQuota, request.actions.single().intent.action)
    }

    @Test
    fun `missing official 5h window renders unavailable instead of inventing quota`() {
        val request = orchestrator.buildRequests(
            state = state(
                usedPercent = 28,
                weeklyUsedPercent = 28,
                includeFiveHourWindow = false,
            ),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.STATUS_CHANNEL_ID }

        val body = request.body.renderedTextForTest()
        assertTrue(body.contains("5h — · Renewal unavailable"))
        assertTrue(body.contains("7-day 72% · Renews 05/29, 12:00 AM"))
    }

    @Test
    fun `official quota without reset timestamp reports unavailable renewal`() {
        val request = orchestrator.buildRequests(
            state = state(
                usedPercent = 25,
                weeklyUsedPercent = 40,
                fiveHourResetAt = null,
            ),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.STATUS_CHANNEL_ID }

        assertTrue(request.body.renderedTextForTest().contains("5h 75% · Renewal unavailable"))
    }

    @Test
    fun `renewal timestamp uses device time zone`() {
        val seoulOrchestrator = NotificationCoordinator(zoneId = ZoneId.of("Asia/Seoul"))

        val request = seoulOrchestrator.buildRequests(
            state = state(usedPercent = 25, weeklyUsedPercent = 40),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.STATUS_CHANNEL_ID }

        assertTrue(request.body.renderedTextForTest().contains("05/24, 2:00 AM"))
        assertFalse(request.body.renderedTextForTest().contains("Sun"))
    }

    @Test
    fun `auth-required account error title names provider and account`() {
        val request = accountErrorRequest(localAccountId = "local-1")

        val title = request.title.renderedTextForTest()
        assertTrue("title should name the provider: $title", title.contains("Codex"))
        assertTrue("title should name the account: $title", title.contains("Codex Main"))
    }

    @Test
    fun `notification requests expose immutable pending intent metadata`() {
        val requests = orchestrator.buildRequests(
            state = state(),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                statusNotificationEnabled = true,
            ),
        )

        assertTrue(requests.isNotEmpty())
        requests.forEach { request ->
            assertTrue(request.pendingIntent.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        }
    }

    private fun NotificationRequest.renderedContentForTest(): String =
        listOf(title, body)
            .map { it.renderedTextForTest() }
            .joinToString(separator = " ")

    private fun NotificationText.renderedTextForTest(): String {
        val context = RuntimeEnvironment.getApplication()
        val resolvedArgs = formatArgs.map { arg ->
            if (arg is NotificationText) arg.renderedTextForTest() else arg
        }
        return context.getString(resourceId, *resolvedArgs.toTypedArray())
    }

    private fun forbiddenDiagnosticTerms(): List<String> =
        listOf(
            "access" + "_" + "token",
            "refresh" + "_" + "token",
            "id" + "_" + "token",
            "auth " + "code",
            "Cook" + "ie",
            "Author" + "ization",
            "auth" + ".json",
            "raw " + "usage API response body",
            "raw " + "response",
        )

    private fun alertEvent(
        localAccountId: String = "local-1",
        windowId: QuotaWindowId = QuotaWindowId("five_hour"),
        threshold: Double = 10.0,
        remainingText: String = "8%",
        level: AlertLevel = AlertLevel.Warning,
    ): QuotaAlertEvent =
        QuotaAlertEvent(
            key = AlertDedupeKey(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId(localAccountId),
                windowId = windowId,
                resetAt = resetAt,
                threshold = threshold,
            ),
            accountDisplayName = "Codex Main",
            windowId = windowId,
            threshold = threshold,
            level = level,
            remainingText = remainingText,
            resetAt = resetAt,
        )

    private fun quotaAlertRequest(event: QuotaAlertEvent): NotificationRequest =
        orchestrator.buildRequests(
            state = state(),
            alertEvents = listOf(event),
            options = NotificationRequestOptions(notificationPermissionAvailable = true),
        ).single { it.channelId == NotificationChannels.QUOTA_ALERTS_CHANNEL_ID }

    private fun accountErrorRequest(localAccountId: String): NotificationRequest =
        orchestrator.buildRequests(
            state = state(
                status = CurrentQuotaStatus.AuthRequired,
                localAccountId = localAccountId,
                error = QuotaError.AuthRequired(httpStatus = 401, diagnosticsDigest = "safe-digest"),
            ),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                accountErrorsEnabled = true,
            ),
        ).single { it.channelId == NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID }

    private fun repeatedFailureAccountErrorRequest(localAccountId: String): NotificationRequest =
        orchestrator.buildRequests(
            state = state(
                status = CurrentQuotaStatus.ErrorWithLastKnownGood,
                localAccountId = localAccountId,
                error = QuotaError.Network(diagnosticsDigest = "safe-digest"),
            ),
            options = NotificationRequestOptions(
                notificationPermissionAvailable = true,
                accountErrorsEnabled = true,
            ),
            accountErrorEvent = AccountErrorNotificationEvent(AccountErrorNotificationReason.RepeatedRefreshFailure),
        ).single { it.channelId == NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID }

    private fun state(
        status: CurrentQuotaStatus = CurrentQuotaStatus.Fresh,
        providerId: String = "codex",
        localAccountId: String = "local-1",
        accountDisplayName: String = "Codex Main",
        usedPercent: Int = 62,
        fiveHourResetAt: Instant? = resetAt,
        weeklyUsedPercent: Int? = null,
        weeklyResetAt: Instant? = Instant.parse("2026-05-29T00:00:00Z"),
        includeFiveHourWindow: Boolean = true,
        responseDigest: String? = "safe-digest",
        error: QuotaError? = null,
    ): CurrentQuotaState {
        val primaryWindow = quotaWindow(
            windowId = QuotaWindowId(
                if (providerId == "claude") "claude_5h_window" else "five_hour",
            ),
            usedPercent = usedPercent,
            windowResetAt = fiveHourResetAt,
        )
        val windows = buildList {
            if (includeFiveHourWindow) add(primaryWindow)
            weeklyUsedPercent?.let { weeklyPercent ->
                add(
                    quotaWindow(
                        windowId = QuotaWindowId(
                            if (providerId == "claude") "claude_7d_window" else "weekly",
                        ),
                        usedPercent = weeklyPercent,
                        windowResetAt = weeklyResetAt,
                    ),
                )
            }
        }
        return CurrentQuotaState(
            status = status,
            freshness = when (status) {
                CurrentQuotaStatus.Expired -> CurrentQuotaFreshness.Expired
                CurrentQuotaStatus.PossiblyStale -> CurrentQuotaFreshness.PossiblyStale
                CurrentQuotaStatus.Fresh -> CurrentQuotaFreshness.Fresh
                else -> CurrentQuotaFreshness.Unknown
            },
            account = account(
                providerId = providerId,
                localAccountId = localAccountId,
                displayName = accountDisplayName,
            ),
            snapshot = quotaSnapshot(
                windows = windows,
                providerId = providerId,
                localAccountId = localAccountId,
                responseDigest = responseDigest,
            ),
            latestAttempt = null,
            primaryWindow = primaryWindow,
            secondaryWindows = emptyList(),
            primaryWindowCanAlert = true,
            error = error,
        )
    }

    private fun account(
        providerId: String,
        localAccountId: String,
        displayName: String,
    ): ProviderAccount =
        ProviderAccount.createNew(
            localAccountId = LocalAccountId(localAccountId),
            providerId = ProviderId(providerId),
            providerAccountId = ProviderAccountId("acct-$localAccountId"),
            displayName = displayName,
            now = Instant.parse("2026-05-23T09:00:00Z"),
        )

    private fun quotaSnapshot(
        windows: List<QuotaWindow>,
        providerId: String,
        localAccountId: String,
        responseDigest: String?,
    ): QuotaSnapshot =
        QuotaSnapshot(
            snapshotId = SnapshotId("snapshot-1"),
            providerId = ProviderId(providerId),
            localAccountId = LocalAccountId(localAccountId),
            providerAccountId = ProviderAccountId("acct-$localAccountId"),
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
            planType = "plus",
            windows = windows,
            credits = null,
            responseDigest = responseDigest,
        )

    private fun quotaWindow(
        windowId: QuotaWindowId = QuotaWindowId("five_hour"),
        usedPercent: Int = 62,
        windowResetAt: Instant? = resetAt,
    ): QuotaWindow =
        QuotaWindow(
            windowId = windowId,
            titleKey = if (windowId.value == "weekly") "quota_window_weekly" else "quota_window_five_hour",
            usedPercent = usedPercent,
            resetAt = windowResetAt,
            limitWindowSeconds = if (
                windowId.value == "weekly" || windowId.value == "claude_7d_window"
            ) {
                604_800
            } else {
                18_000
            },
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
        )
}
