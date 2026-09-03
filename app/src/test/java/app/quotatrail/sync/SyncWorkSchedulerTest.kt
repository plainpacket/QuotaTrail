package app.quotatrail.sync

import app.quotatrail.QuotaTrailApplication
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.RefreshAttemptId
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.surfaces.widget.WidgetRefreshFeedback
import java.time.Duration
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkSchedulerTest {
    @Test
    fun `periodic refresh uses architecture unique work name`() {
        val enqueuer = RecordingRefreshWorkEnqueuer()
        val scheduler = SyncWorkScheduler(enqueuer)

        val plan = scheduler.schedulePeriodicRefresh()

        assertEquals("quota_periodic_refresh", plan.uniqueWorkName)
        assertEquals(listOf(plan), enqueuer.enqueuedPlans)
    }

    @Test
    fun `periodic refresh uses fifteen minute WorkManager interval class`() {
        val plan = SyncWorkScheduler.periodicRefreshPlan()

        assertEquals(Duration.ofMinutes(15), plan.repeatInterval)
        assertTrue(plan.repeatInterval >= Duration.ofMinutes(15))
    }

    @Test
    fun `periodic refresh waits for connected network`() {
        val plan = SyncWorkScheduler.periodicRefreshPlan()

        assertTrue(plan.requiresConnectedNetwork)
    }

    @Test
    fun `application bootstrap registers periodic refresh work`() {
        val enqueuer = RecordingRefreshWorkEnqueuer()
        val scheduler = SyncWorkScheduler(enqueuer)

        QuotaTrailApplication.registerRefreshWork(scheduler)

        assertEquals(listOf(SyncWorkScheduler.periodicRefreshPlan()), enqueuer.enqueuedPlans)
    }

    @Test
    fun `apply interval reschedules with chosen minutes and manual cancels`() {
        val enqueuer = RecordingRefreshWorkEnqueuer()
        val scheduler = SyncWorkScheduler(enqueuer)

        scheduler.applyIntervalMinutes(30)
        assertEquals(Duration.ofMinutes(30), enqueuer.enqueuedPlans.single().repeatInterval)

        scheduler.applyIntervalMinutes(0)
        assertEquals(listOf(SyncWorkScheduler.UNIQUE_PERIODIC_WORK_NAME), enqueuer.cancelledNames)
    }

    @Test
    fun `notification refresh enqueues connected one-time manual work`() {
        val enqueuer = RecordingRefreshWorkEnqueuer()
        val scheduler = SyncWorkScheduler(enqueuer)

        val plan = scheduler.scheduleImmediateRefresh()

        assertEquals("quota_refresh_once", plan.uniqueWorkName)
        assertTrue(plan.requiresConnectedNetwork)
        assertEquals(listOf(plan), enqueuer.enqueuedImmediatePlans)
    }

    @Test
    fun `widget refresh enqueues connected work scoped to its account`() {
        val enqueuer = RecordingRefreshWorkEnqueuer()
        val scheduler = SyncWorkScheduler(enqueuer)

        val plan = scheduler.scheduleImmediateRefresh(LocalAccountId("codex-local"))

        assertTrue(plan.uniqueWorkName.startsWith("quota_widget_refresh_"))
        assertEquals("codex-local", plan.targetLocalAccountId)
        assertTrue(plan.requiresConnectedNetwork)
        assertTrue(plan.isExpedited)
        assertTrue(plan.showUserFeedback)
        assertEquals(listOf(plan), enqueuer.enqueuedImmediatePlans)
    }

    @Test
    fun `widget callback waits until its expedited work is durably enqueued`() = runTest {
        val enqueuer = RecordingRefreshWorkEnqueuer()
        val scheduler = SyncWorkScheduler(enqueuer)

        val plan = scheduler.scheduleImmediateRefreshAndAwait(LocalAccountId("claude-local"))

        assertEquals("claude-local", plan.targetLocalAccountId)
        assertTrue(plan.isExpedited)
        assertEquals(listOf(plan), enqueuer.awaitedImmediatePlans)
    }

    @Test
    fun `application exposes refresh dependencies for scheduled worker`() {
        assertTrue(QuotaRefreshDependenciesProvider::class.java.isAssignableFrom(QuotaTrailApplication::class.java))
    }

    @Test
    fun `missing worker runner fails instead of reporting fake success`() = runTest {
        val outcome = UsageSyncWorkerOutcome.fromDependencies(provider = null)

        assertEquals(UsageSyncWorkerOutcome.Failure, outcome)
    }

    @Test
    fun `worker dependency path refreshes current account through coordinator`() = runTest {
        val account = account()
        val refreshProvider = RecordingRefreshProvider()
        val dependencies = RecordingRefreshDependenciesProvider(
            account = account,
            refreshCoordinator = UsageSyncCoordinator(
                provider = refreshProvider,
                snapshotStore = EmptySnapshotStore,
                attemptStore = RecordingAttemptStore(),
                attemptIdProvider = AttemptIdProvider { RefreshAttemptId("attempt-1") },
                clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
            ),
        )

        val outcome = UsageSyncWorkerOutcome.fromDependencies(dependencies)

        assertEquals(UsageSyncWorkerOutcome.Failure, outcome)
        assertEquals(listOf(account to RefreshTrigger.Periodic), refreshProvider.requests)
    }

    @Test
    fun `immediate worker refreshes all supplied accounts with manual trigger`() = runTest {
        val accounts = listOf(
            account(providerId = "claude", localAccountId = "claude-local"),
            account(providerId = "codex", localAccountId = "codex-local"),
        )
        val refreshProvider = RecordingRefreshProvider()
        val dependencies = RecordingRefreshDependenciesProvider(
            accounts = accounts,
            refreshCoordinator = UsageSyncCoordinator(
                provider = refreshProvider,
                snapshotStore = EmptySnapshotStore,
                attemptStore = RecordingAttemptStore(),
                attemptIdProvider = AttemptIdProvider { RefreshAttemptId("attempt") },
                clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
            ),
        )

        UsageSyncWorkerOutcome.fromDependencies(dependencies, manual = true)

        assertEquals(accounts.toSet(), refreshProvider.requests.map { it.first }.toSet())
        assertTrue(refreshProvider.requests.all { it.second == RefreshTrigger.Manual })
    }

    @Test
    fun `widget worker refreshes only its selected account with widget trigger`() = runTest {
        val accounts = listOf(
            account(providerId = "claude", localAccountId = "claude-local"),
            account(providerId = "codex", localAccountId = "codex-local"),
        )
        val refreshProvider = RecordingRefreshProvider()
        val dependencies = RecordingRefreshDependenciesProvider(
            accounts = accounts,
            refreshCoordinator = UsageSyncCoordinator(
                provider = refreshProvider,
                snapshotStore = EmptySnapshotStore,
                attemptStore = RecordingAttemptStore(),
                attemptIdProvider = AttemptIdProvider { RefreshAttemptId("attempt") },
                clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
            ),
        )

        val outcome = UsageSyncWorkerOutcome.fromDependencies(
            provider = dependencies,
            manual = true,
            targetLocalAccountId = LocalAccountId("codex-local"),
        )

        assertEquals(UsageSyncWorkerOutcome.Failure, outcome)
        assertEquals(
            listOf(accounts[1] to RefreshTrigger.Widget),
            refreshProvider.requests,
        )
    }

    @Test
    fun `widget worker completion maps to user feedback`() {
        assertEquals(WidgetRefreshFeedback.Complete, UsageSyncWorkerOutcome.Success.widgetFeedback())
        assertEquals(WidgetRefreshFeedback.Retrying, UsageSyncWorkerOutcome.Retry.widgetFeedback())
        assertEquals(WidgetRefreshFeedback.Failed, UsageSyncWorkerOutcome.Failure.widgetFeedback())
    }

    @Test
    fun `widget worker reports failure when configured account no longer exists`() = runTest {
        val dependencies = RecordingRefreshDependenciesProvider(
            accounts = listOf(account(providerId = "codex", localAccountId = "other-local")),
            refreshCoordinator = UsageSyncCoordinator(
                provider = RecordingRefreshProvider(),
                snapshotStore = EmptySnapshotStore,
                attemptStore = RecordingAttemptStore(),
                attemptIdProvider = AttemptIdProvider { RefreshAttemptId("attempt") },
                clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
            ),
        )

        val outcome = UsageSyncWorkerOutcome.fromDependencies(
            provider = dependencies,
            manual = true,
            targetLocalAccountId = LocalAccountId("missing-local"),
        )

        assertEquals(UsageSyncWorkerOutcome.Failure, outcome)
    }

    @Test
    fun `retryable refresh failure asks WorkManager to retry`() = runTest {
        val dependencies = RecordingRefreshDependenciesProvider(
            account = account(),
            refreshCoordinator = UsageSyncCoordinator(
                provider = RecordingRefreshProvider(
                    app.quotatrail.domain.refresh.QuotaError.Network(
                        diagnosticsDigest = "safe",
                    ),
                ),
                snapshotStore = EmptySnapshotStore,
                attemptStore = RecordingAttemptStore(),
                attemptIdProvider = AttemptIdProvider { RefreshAttemptId("attempt-1") },
                clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
            ),
        )

        val outcome = UsageSyncWorkerOutcome.fromDependencies(dependencies)

        assertEquals(UsageSyncWorkerOutcome.Retry, outcome)
    }

    private class RecordingRefreshWorkEnqueuer : RefreshWorkEnqueuer {
        val enqueuedPlans = mutableListOf<RefreshWorkPlan>()
        val enqueuedImmediatePlans = mutableListOf<ImmediateRefreshWorkPlan>()
        val awaitedImmediatePlans = mutableListOf<ImmediateRefreshWorkPlan>()
        val cancelledNames = mutableListOf<String>()

        override fun enqueue(plan: RefreshWorkPlan) {
            enqueuedPlans += plan
        }

        override fun enqueueImmediate(plan: ImmediateRefreshWorkPlan) {
            enqueuedImmediatePlans += plan
        }

        override suspend fun enqueueImmediateAndAwait(plan: ImmediateRefreshWorkPlan) {
            awaitedImmediatePlans += plan
        }

        override fun cancel(uniqueWorkName: String) {
            cancelledNames += uniqueWorkName
        }
    }

    private class RecordingRefreshDependenciesProvider(
        private val account: ProviderAccount? = null,
        private val accounts: List<ProviderAccount> = account?.let(::listOf).orEmpty(),
        override val refreshCoordinator: UsageSyncCoordinator,
    ) : QuotaRefreshDependenciesProvider {
        override suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount> =
            accounts

        override suspend fun manuallyRefreshableQuotaRefreshAccounts(): List<ProviderAccount> =
            accounts
    }

    private class RecordingRefreshProvider(
        private val error: app.quotatrail.domain.refresh.QuotaError =
            app.quotatrail.domain.refresh.QuotaError.AuthRequired(
                httpStatus = 401,
                diagnosticsDigest = "safe",
            ),
    ) : RefreshProvider {
        val requests = mutableListOf<Pair<ProviderAccount, RefreshTrigger>>()

        override suspend fun refresh(account: ProviderAccount, trigger: RefreshTrigger): ProviderRefreshResult {
            requests += account to trigger
            return ProviderRefreshResult.Failure(error)
        }
    }

    private object EmptySnapshotStore : SnapshotStore {
        override suspend fun save(snapshot: app.quotatrail.domain.quota.QuotaSnapshot) = Unit
        override suspend fun latestFor(account: ProviderAccount): app.quotatrail.domain.quota.QuotaSnapshot? = null
    }

    private class RecordingAttemptStore : RefreshAttemptStore {
        override suspend fun save(attempt: app.quotatrail.domain.refresh.RefreshAttempt) = Unit
    }

    private fun account(
        providerId: String = "codex",
        localAccountId: String = "local-1",
    ): ProviderAccount =
        ProviderAccount.createNew(
            localAccountId = LocalAccountId(localAccountId),
            providerId = ProviderId(providerId),
            providerAccountId = ProviderAccountId("acct-$localAccountId"),
            displayName = providerId,
            now = Instant.parse("2026-05-23T12:00:00Z"),
        )
}
