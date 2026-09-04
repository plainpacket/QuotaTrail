package app.quotatrail.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.refresh.RefreshTrigger
import app.quotatrail.surfaces.widget.WidgetRefreshFeedback
import app.quotatrail.surfaces.widget.WidgetRefreshReceiver
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun interface ExchangeRateRefresher {
    suspend fun refreshExchangeRates()
}

class UsageSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val outcome = runRefreshWithFeedback(
            showFeedback = inputData.getBoolean(KEY_SHOW_WIDGET_FEEDBACK, false),
            feedback = { event ->
                applicationContext.sendBroadcast(WidgetRefreshReceiver.feedbackIntent(applicationContext, event))
            },
        ) {
            UsageSyncWorkerOutcome.fromDependencies(
                provider = applicationContext as? QuotaRefreshDependenciesProvider,
                manual = inputData.getBoolean(KEY_MANUAL_REFRESH, false),
                targetLocalAccountIds = inputData.getStringArray(KEY_TARGET_LOCAL_ACCOUNT_IDS)
                    .orEmpty()
                    .map(::LocalAccountId)
                    .toSet(),
            )
        }
        return when (outcome) {
            UsageSyncWorkerOutcome.Success -> Result.success()
            UsageSyncWorkerOutcome.Retry -> Result.retry()
            UsageSyncWorkerOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        const val KEY_MANUAL_REFRESH = "manual_refresh"
        const val KEY_TARGET_LOCAL_ACCOUNT_IDS = "target_local_account_ids"
        const val KEY_SHOW_WIDGET_FEEDBACK = "show_widget_feedback"
    }
}

internal suspend fun runRefreshWithFeedback(
    showFeedback: Boolean,
    feedback: (WidgetRefreshFeedback) -> Unit,
    refresh: suspend () -> UsageSyncWorkerOutcome,
): UsageSyncWorkerOutcome {
    if (showFeedback) feedback(WidgetRefreshFeedback.Refreshing)
    val outcome = try {
        refresh()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        // Unexpected local/storage failures must not leave a manual request without feedback.
        UsageSyncWorkerOutcome.Retry
    }
    if (showFeedback) feedback(outcome.widgetFeedback())
    return outcome
}

internal fun UsageSyncWorkerOutcome.widgetFeedback(): WidgetRefreshFeedback = when (this) {
    UsageSyncWorkerOutcome.Success -> WidgetRefreshFeedback.Complete
    UsageSyncWorkerOutcome.Retry -> WidgetRefreshFeedback.Retrying
    UsageSyncWorkerOutcome.Failure -> WidgetRefreshFeedback.Failed
}

interface QuotaRefreshDependenciesProvider {
    val refreshCoordinator: UsageSyncCoordinator

    val exchangeRateRefresher: ExchangeRateRefresher
        get() = ExchangeRateRefresher { }

    suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount>

    suspend fun manuallyRefreshableQuotaRefreshAccounts(): List<ProviderAccount> =
        activeQuotaRefreshAccounts()
}

enum class UsageSyncWorkerOutcome {
    Success,
    Retry,
    Failure;

    companion object {
        suspend fun fromDependencies(
            provider: QuotaRefreshDependenciesProvider?,
            manual: Boolean = false,
            targetLocalAccountId: LocalAccountId? = null,
            targetLocalAccountIds: Set<LocalAccountId> = setOfNotNull(targetLocalAccountId),
        ): UsageSyncWorkerOutcome {
            val dependencies = provider ?: return Failure
            val eligibleAccounts = if (manual) {
                dependencies.manuallyRefreshableQuotaRefreshAccounts()
            } else {
                dependencies.activeQuotaRefreshAccounts()
            }
            val selectedAccounts = if (targetLocalAccountIds.isNotEmpty()) {
                eligibleAccounts.filter { it.localAccountId in targetLocalAccountIds }
            } else {
                eligibleAccounts
            }
            if (targetLocalAccountIds.isNotEmpty() && selectedAccounts.size != targetLocalAccountIds.size) return Failure
            val results = MultiAccountRefreshRunner(
                refreshCoordinator = dependencies.refreshCoordinator,
                exchangeRateRefresher = dependencies.exchangeRateRefresher,
            ).refresh(
                accounts = selectedAccounts,
                trigger = when {
                    targetLocalAccountIds.isNotEmpty() -> RefreshTrigger.Widget
                    manual -> RefreshTrigger.Manual
                    else -> RefreshTrigger.Periodic
                },
            )
            return when {
                results.any { it.isRetryableFailure() } -> Retry
                results.any { it is RefreshResult.Failure } -> Failure
                else -> Success
            }
        }
    }
}

class MultiAccountRefreshRunner(
    private val refreshCoordinator: UsageSyncCoordinator,
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val exchangeRateRefresher: ExchangeRateRefresher = ExchangeRateRefresher { },
) {
    init {
        require(parallelism > 0) { "parallelism must be positive" }
    }

    suspend fun refresh(
        accounts: List<ProviderAccount>,
        trigger: RefreshTrigger,
    ): List<RefreshResult> = coroutineScope {
        runCatching { exchangeRateRefresher.refreshExchangeRates() }
        val semaphore = Semaphore(parallelism)
        // The caller decides which accounts are eligible: activeAccounts for background refresh, or
        // manuallyRefreshableAccounts for a manual retry that may include NeedsReauth. The runner
        // refreshes exactly what it is handed and does not re-filter by status.
        accounts
            .map { account ->
                async {
                    semaphore.withPermit {
                        refreshCoordinator.refresh(account = account, trigger = trigger)
                    }
                }
            }
            .awaitAll()
    }

    companion object {
        // Refresh up to 4 accounts at once (bounded concurrency) so a multi-provider setup completes
        // a periodic cycle quickly without hammering every provider endpoint simultaneously.
        const val DEFAULT_PARALLELISM = 4
    }
}

private fun RefreshResult.isRetryableFailure(): Boolean =
    this is RefreshResult.Failure && error.retryable
