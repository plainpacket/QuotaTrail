package app.quotatrail.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import app.quotatrail.domain.model.LocalAccountId
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RefreshWorkPlan(
    val uniqueWorkName: String,
    val repeatInterval: Duration,
    val requiresConnectedNetwork: Boolean,
)

data class ImmediateRefreshWorkPlan(
    val uniqueWorkName: String,
    val requiresConnectedNetwork: Boolean,
    val targetLocalAccountId: String? = null,
    val targetLocalAccountIds: List<String> = listOfNotNull(targetLocalAccountId),
    val isExpedited: Boolean = false,
    val showUserFeedback: Boolean = false,
)

interface RefreshWorkEnqueuer {
    fun enqueue(plan: RefreshWorkPlan)

    fun enqueueImmediate(plan: ImmediateRefreshWorkPlan)

    suspend fun enqueueImmediateAndAwait(plan: ImmediateRefreshWorkPlan) {
        enqueueImmediate(plan)
    }

    fun cancel(uniqueWorkName: String)
}

class SyncWorkScheduler(
    private val enqueuer: RefreshWorkEnqueuer,
) {
    fun schedulePeriodicRefresh(): RefreshWorkPlan {
        val plan = periodicRefreshPlan()
        enqueuer.enqueue(plan)
        return plan
    }

    fun scheduleImmediateRefresh(targetLocalAccountId: LocalAccountId? = null): ImmediateRefreshWorkPlan {
        val plan = immediateRefreshPlan(targetLocalAccountId)
        enqueuer.enqueueImmediate(plan)
        return plan
    }

    suspend fun scheduleImmediateRefreshAndAwait(targetLocalAccountId: LocalAccountId): ImmediateRefreshWorkPlan {
        val plan = immediateRefreshPlan(targetLocalAccountId)
        enqueuer.enqueueImmediateAndAwait(plan)
        return plan
    }

    suspend fun scheduleImmediateRefreshAndAwait(targetLocalAccountIds: List<LocalAccountId>): ImmediateRefreshWorkPlan {
        val targets = targetLocalAccountIds.distinctBy { it.value }
        require(targets.isNotEmpty()) { "At least one widget account is required" }
        val plan = immediateRefreshPlan(targets)
        enqueuer.enqueueImmediateAndAwait(plan)
        return plan
    }

    private fun immediateRefreshPlan(targetLocalAccountId: LocalAccountId?): ImmediateRefreshWorkPlan =
        ImmediateRefreshWorkPlan(
            uniqueWorkName = targetLocalAccountId?.let(::widgetRefreshWorkName)
                ?: UNIQUE_IMMEDIATE_WORK_NAME,
            requiresConnectedNetwork = true,
            targetLocalAccountId = targetLocalAccountId?.value,
            isExpedited = targetLocalAccountId != null,
            showUserFeedback = targetLocalAccountId != null,
        )

    private fun immediateRefreshPlan(targetLocalAccountIds: List<LocalAccountId>): ImmediateRefreshWorkPlan =
        ImmediateRefreshWorkPlan(
            uniqueWorkName = widgetRefreshWorkName(targetLocalAccountIds),
            requiresConnectedNetwork = true,
            targetLocalAccountIds = targetLocalAccountIds.map { it.value },
            isExpedited = true,
            showUserFeedback = true,
        )

    /**
     * Reschedule the periodic refresh to [intervalMinutes], or cancel it entirely when the user picks
     * "Manual" (<= 0). WorkManager floors periodic work at 15 minutes, so shorter values are clamped.
     */
    fun applyIntervalMinutes(intervalMinutes: Int) {
        if (intervalMinutes <= 0) {
            enqueuer.cancel(UNIQUE_PERIODIC_WORK_NAME)
        } else {
            enqueuer.enqueue(planForMinutes(intervalMinutes))
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "quota_periodic_refresh"
        const val UNIQUE_IMMEDIATE_WORK_NAME = "quota_refresh_once"
        const val UNIQUE_WIDGET_REFRESH_WORK_PREFIX = "quota_widget_refresh_"
        val PERIODIC_REFRESH_INTERVAL: Duration = Duration.ofMinutes(15)

        fun periodicRefreshPlan(): RefreshWorkPlan =
            RefreshWorkPlan(
                uniqueWorkName = UNIQUE_PERIODIC_WORK_NAME,
                repeatInterval = PERIODIC_REFRESH_INTERVAL,
                requiresConnectedNetwork = true,
            )

        fun planForMinutes(intervalMinutes: Int): RefreshWorkPlan =
            RefreshWorkPlan(
                uniqueWorkName = UNIQUE_PERIODIC_WORK_NAME,
                repeatInterval = Duration.ofMinutes(intervalMinutes.toLong()).coerceAtLeast(PERIODIC_REFRESH_INTERVAL),
                requiresConnectedNetwork = true,
            )

        fun widgetRefreshWorkName(localAccountId: LocalAccountId): String =
            UNIQUE_WIDGET_REFRESH_WORK_PREFIX + UUID.nameUUIDFromBytes(
                localAccountId.value.toByteArray(StandardCharsets.UTF_8),
            )

        fun widgetRefreshWorkName(localAccountIds: List<LocalAccountId>): String =
            UNIQUE_WIDGET_REFRESH_WORK_PREFIX + UUID.nameUUIDFromBytes(
                localAccountIds.map { it.value }.distinct().sorted().joinToString("\n")
                    .toByteArray(StandardCharsets.UTF_8),
            )

        fun from(context: Context): SyncWorkScheduler =
            SyncWorkScheduler(
                WorkManagerRefreshWorkEnqueuer(
                    workManager = WorkManager.getInstance(context),
                ),
            )
    }
}

private class WorkManagerRefreshWorkEnqueuer(
    private val workManager: WorkManager,
) : RefreshWorkEnqueuer {
    override fun enqueue(plan: RefreshWorkPlan) {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
            plan.repeatInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (plan.requiresConnectedNetwork) {
                        NetworkType.CONNECTED
                    } else {
                        NetworkType.NOT_REQUIRED
                    },
                )
                .build(),
        ).build()

        workManager.enqueueUniquePeriodicWork(
            plan.uniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun enqueueImmediate(plan: ImmediateRefreshWorkPlan) {
        enqueueImmediateOperation(plan)
    }

    override suspend fun enqueueImmediateAndAwait(plan: ImmediateRefreshWorkPlan) {
        enqueueImmediateOperation(plan).await()
    }

    private fun enqueueImmediateOperation(plan: ImmediateRefreshWorkPlan): androidx.work.Operation {
        val inputData = Data.Builder().putBoolean(UsageSyncWorker.KEY_MANUAL_REFRESH, true).apply {
            if (plan.targetLocalAccountIds.isNotEmpty()) {
                putStringArray(UsageSyncWorker.KEY_TARGET_LOCAL_ACCOUNT_IDS, plan.targetLocalAccountIds.toTypedArray())
            }
            putBoolean(UsageSyncWorker.KEY_SHOW_WIDGET_FEEDBACK, plan.showUserFeedback)
        }.build()
        val requestBuilder = OneTimeWorkRequestBuilder<UsageSyncWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (plan.requiresConnectedNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED,
                    )
                    .build(),
            )
        if (plan.isExpedited) {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = requestBuilder.build()

        return workManager.enqueueUniqueWork(
            plan.uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(uniqueWorkName: String) {
        workManager.cancelUniqueWork(uniqueWorkName)
    }
}
