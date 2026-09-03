package app.quotatrail.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.quotatrail.BuildConfig
import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.local.dao.RefreshAttemptDao
import app.quotatrail.storage.local.db.QUOTA_TRAIL_DATABASE_SCHEMA_VERSION
import app.quotatrail.storage.local.entity.ProviderAccountEntity
import app.quotatrail.storage.local.entity.QuotaSnapshotEntity
import app.quotatrail.storage.local.entity.RefreshAttemptEntity
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.auth.DeviceCodeLoginDiagnosticsReader
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.domain.settings.RetentionPreference
import app.quotatrail.domain.settings.RetentionPreferenceReader
import app.quotatrail.sync.SyncWorkScheduler
import app.quotatrail.presentation.settings.DiagnosticsAccountSummary
import app.quotatrail.presentation.settings.SettingsDiagnosticsInput
import app.quotatrail.presentation.settings.SettingsDiagnosticsReader
import app.quotatrail.presentation.settings.WorkManagerDiagnostics
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SettingsDiagnosticsRepository(
    private val context: Context,
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val sessionStore: SecureSessionStore,
    private val deviceCodeLoginDiagnosticsReader: DeviceCodeLoginDiagnosticsReader,
    private val workManagerStatusReader: WorkManagerStatusReader = AndroidWorkManagerStatusReader(context),
    private val notificationPermissionReader: NotificationPermissionReader = AndroidNotificationPermissionReader(context),
    private val notificationPreferenceReader: NotificationPreferenceReader,
    private val retentionPreferenceReader: RetentionPreferenceReader,
    private val deviceEnvironmentReader: DeviceEnvironmentReader = AndroidDeviceEnvironmentReader(context),
    private val widgetCountReader: WidgetCountReader = GlanceWidgetCountReader(context),
    private val roomSchemaVersion: Int = QUOTA_TRAIL_DATABASE_SCHEMA_VERSION,
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) : SettingsDiagnosticsReader {
    override suspend fun diagnosticsInput(): SettingsDiagnosticsInput {
        val selection = currentAccountReader.currentAccountSelection()
        val account = selection?.let { providerAccountDao.getById(it.localAccountId.value) }
        val accountMatchesSelection = account != null && account.providerId == selection?.providerId?.value
        val selectedProviderId = selection?.providerId?.value ?: CODEX_PROVIDER_ID
        val selectedLocalAccountId = selection?.localAccountId?.value
        val latestSnapshot = if (accountMatchesSelection && selectedLocalAccountId != null) {
            quotaSnapshotDao.getLatestForAccount(
                providerId = selectedProviderId,
                localAccountId = selectedLocalAccountId,
            )
        } else {
            null
        }
        val latestAttempt = if (accountMatchesSelection && selectedLocalAccountId != null) {
            refreshAttemptDao.getLatestForAccount(
                providerId = selectedProviderId,
                localAccountId = selectedLocalAccountId,
            )
        } else {
            null
        }
        val sessionEnvelope = if (accountMatchesSelection && selectedLocalAccountId != null) {
            runCatching {
                sessionStore.load(
                    providerId = selectedProviderId,
                    localAccountId = selectedLocalAccountId,
                )
            }.getOrNull()
        } else {
            null
        }
        val accountCount = providerAccountDao.listByProvider(CODEX_PROVIDER_ID).size
        val loginDiagnostics = deviceCodeLoginDiagnosticsReader.latestDeviceCodeLoginDiagnostics()

        val now = nowMillisProvider()
        val env = deviceEnvironmentReader.read()
        val wm = workManagerStatusReader.status()
        val prefs = notificationPreferenceReader.notificationPreferences()
        val retention = retentionPreferenceReader.retentionPreference()
        val widgetCount = widgetCountReader.configuredWidgetCount()

        val consecutiveFailures = if (accountMatchesSelection && selectedLocalAccountId != null) {
            refreshAttemptDao.countConsecutiveFailuresSinceLatestSuccess(
                providerId = selectedProviderId,
                localAccountId = selectedLocalAccountId,
            )
        } else {
            null
        }

        val accountSummaries = providerAccountDao.listAll().map { acct ->
            val attempt = refreshAttemptDao.getLatestForAccount(acct.providerId, acct.localAccountId)
            val snapshot = quotaSnapshotDao.getLatestForAccount(acct.providerId, acct.localAccountId)
            DiagnosticsAccountSummary(
                providerId = acct.providerId,
                accountIdHash = "sha256:${acct.localAccountId.diagnosticsSafeHash()}",
                status = acct.status,
                lastAttemptStatus = attempt?.status ?: "none",
                lastAttemptErrorCode = attempt?.errorCode,
                lastAttemptAt = attempt?.startedAt?.toString(),
                lastSuccessfulRefreshAt = acct.lastSuccessfulRefreshAt?.toString(),
                latestSnapshotAt = snapshot?.fetchedAt?.toString(),
            )
        }

        val accountAlertConfigs = buildDiagnosticsAccountAlertConfigs(prefs.accountQuotaAlertSettings)

        return SettingsDiagnosticsInput(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            androidSdk = Build.VERSION.SDK_INT.toString(),
            providerId = selectedProviderId,
            accountDiagnosticId = selectedLocalAccountId?.let { "sha256:${it.diagnosticsSafeHash()}" } ?: "not_connected",
            currentAccountSelectionStatus = selectionStatus(
                hasSelection = selection != null,
                account = account,
                accountMatchesSelection = accountMatchesSelection,
            ),
            accountStatus = account?.status,
            accountCount = accountCount,
            sessionEnvelopeStatus = when {
                !accountMatchesSelection -> "not_checked"
                sessionEnvelope == null -> "missing_or_unreadable"
                else -> "present"
            },
            sessionProviderAccountIdStatus = when {
                sessionEnvelope == null -> null
                sessionEnvelope.providerAccountId.isNullOrBlank() -> "missing"
                else -> "present"
            },
            currentState = currentState(
                account = account,
                accountMatchesSelection = accountMatchesSelection,
                latestSnapshot = latestSnapshot,
                latestAttempt = latestAttempt,
            ),
            latestSnapshotStatus = if (latestSnapshot == null) "missing" else "present",
            latestSnapshotSource = latestSnapshot?.source,
            latestSnapshotFetchedAt = latestSnapshot?.fetchedAt?.toString(),
            latestSnapshotDigestStatus = latestSnapshot?.responseDigest?.let { "present" } ?: "missing",
            lastSuccessfulRefreshAt = account?.lastSuccessfulRefreshAt?.toString() ?: "unavailable",
            lastAttemptStatus = latestAttempt?.status ?: "none",
            lastAttemptTrigger = latestAttempt?.trigger,
            lastAttemptStartedAt = latestAttempt?.startedAt?.toString(),
            lastAttemptFinishedAt = latestAttempt?.finishedAt?.toString(),
            safeErrorCode = latestAttempt?.errorCode,
            httpStatus = latestAttempt?.httpStatus,
            retryable = latestAttempt?.retryable,
            userActionRequired = latestAttempt?.userActionRequired,
            diagnosticsDigest = latestAttempt?.diagnosticsDigest,
            deviceCodeLoginStatus = loginDiagnostics.status,
            deviceCodeLoginAttemptId = loginDiagnostics.attemptId?.let { "sha256:${it.diagnosticsSafeHash()}" },
            deviceCodeLoginSafeErrorCode = loginDiagnostics.safeErrorCode,
            deviceCodeLoginVerificationUriStatus = loginDiagnostics.verificationUriStatus,
            deviceCodeLoginPollIntervalSeconds = loginDiagnostics.pollIntervalSeconds,
            deviceCodeLoginExpiresAt = loginDiagnostics.expiresAt?.toString(),
            workManagerStatus = wm.periodicStatus,
            notificationPermissionStatus = notificationPermissionReader.status(),
            generatedAtMillis = now,
            androidRelease = env.androidRelease,
            deviceModel = env.deviceModel,
            locale = env.locale,
            batteryOptimizationIgnored = env.batteryOptimizationIgnored,
            backgroundRestricted = env.backgroundRestricted,
            networkType = env.networkType,
            dataSaver = env.dataSaver,
            appFirstInstallAt = env.appFirstInstallAtMillis?.toString(),
            appLastUpdateAt = env.appLastUpdateAtMillis?.toString(),
            workManagerRunAttemptCount = wm.periodicRunAttemptCount,
            workManagerNextScheduleAt = wm.periodicNextScheduleAtMillis?.toString(),
            workManagerStopReason = wm.periodicStopReason,
            onceWorkManagerStatus = wm.onceStatus,
            consecutiveFailures = consecutiveFailures,
            accountSummaries = accountSummaries,
            roomSchemaVersion = roomSchemaVersion,
            retentionDays = when (retention) {
                RetentionPreference.SevenDays -> "7"
                RetentionPreference.ThirtyDays -> "30"
                RetentionPreference.NinetyDays -> "90"
                RetentionPreference.Forever -> "forever"
            },
            refreshIntervalMinutes = prefs.backgroundRefreshIntervalMinutes,
            statusNotificationEnabled = prefs.statusNotificationEnabled,
            quotaAlertsEnabled = prefs.quotaAlertsEnabled,
            alertThresholds = "caution=${prefs.cautionThreshold}," +
                "warning=${prefs.warningThreshold},limit=${prefs.limitThreshold}",
            accountAlertConfigs = accountAlertConfigs,
            widgetCount = widgetCount,
        )
    }

    private fun selectionStatus(
        hasSelection: Boolean,
        account: ProviderAccountEntity?,
        accountMatchesSelection: Boolean,
    ): String =
        when {
            !hasSelection -> "no_selection"
            account == null -> "selected_account_missing"
            !accountMatchesSelection -> "selected_account_provider_mismatch"
            else -> "selected_account_present"
        }

    private fun currentState(
        account: ProviderAccountEntity?,
        accountMatchesSelection: Boolean,
        latestSnapshot: QuotaSnapshotEntity?,
        latestAttempt: RefreshAttemptEntity?,
    ): String =
        when {
            account == null -> "noData"
            !accountMatchesSelection -> "accountSelectionMismatch"
            latestAttempt?.status == "failed" -> "failed"
            latestAttempt?.status == "cancelled" -> "cancelled"
            latestSnapshot != null -> "ready"
            else -> "noData"
        }

    private companion object {
        const val CODEX_PROVIDER_ID = "codex"
    }
}

internal fun interface WorkManagerStatusReader {
    suspend fun status(): WorkManagerDiagnostics
}

internal fun interface NotificationPermissionReader {
    fun status(): String
}

private class AndroidWorkManagerStatusReader(
    context: Context,
) : WorkManagerStatusReader {
    private val appContext = context.applicationContext

    override suspend fun status(): WorkManagerDiagnostics = withContext(Dispatchers.IO) {
        val wm = WorkManager.getInstance(appContext)
        val periodicName = SyncWorkScheduler.UNIQUE_PERIODIC_WORK_NAME
        val periodic = runCatching {
            wm.getWorkInfosForUniqueWork(periodicName).get(750, TimeUnit.MILLISECONDS)
        }.getOrNull()

        // No one-time unique work exists in this project; report a static sentinel.
        WorkManagerDiagnostics(
            periodicStatus = periodic.formatStatus(periodicName),
            periodicRunAttemptCount = periodic?.maxOfOrNull { it.runAttemptCount },
            periodicNextScheduleAtMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                periodic?.mapNotNull { info -> info.nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE } }
                    ?.minOrNull()
            } else {
                null
            },
            periodicStopReason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                periodic?.mapNotNull { info ->
                    info.stopReason.takeIf { it != WorkInfo.STOP_REASON_NOT_STOPPED }
                }?.firstOrNull()?.toString()
            } else {
                null
            },
            onceStatus = "not_enqueued:quota_refresh_once",
        )
    }

    private fun List<WorkInfo>?.formatStatus(uniqueName: String): String =
        when {
            this == null -> "query_failed:$uniqueName"
            isEmpty() -> "not_enqueued:$uniqueName"
            else -> "$uniqueName:" + joinToString(separator = ",") { it.state.name.lowercase() }
        }
}

private class AndroidNotificationPermissionReader(
    context: Context,
) : NotificationPermissionReader {
    private val appContext = context.applicationContext

    override fun status(): String =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "not_required"
        } else if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "granted"
        } else {
            "denied"
        }
}

