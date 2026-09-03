package app.quotatrail.presentation.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quotatrail.R
import app.quotatrail.application.NotificationWindowChoicesLoader
import app.quotatrail.domain.account.AccountListUseCase
import app.quotatrail.domain.account.NoopAccountListUseCase
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.currency.CurrencyPreferenceStore
import app.quotatrail.domain.settings.NotificationPreferenceStore
import app.quotatrail.domain.theme.AppearancePreferenceStore
import app.quotatrail.domain.theme.ThemeMode
import app.quotatrail.domain.settings.NoopQuotaHistoryClearUseCase
import app.quotatrail.domain.settings.QuotaHistoryClearUseCase
import app.quotatrail.domain.settings.RetentionPreferenceStore
import app.quotatrail.domain.update.AppUpdateCheckUseCase
import app.quotatrail.domain.update.AppUpdateDownloadUseCase
import app.quotatrail.domain.update.NoopAppUpdateCheckUseCase
import app.quotatrail.domain.update.NoopAppUpdateDownloadUseCase
import app.quotatrail.domain.update.NoopUpdatePreferenceStore
import app.quotatrail.domain.update.UpdatePreferenceStore
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.QuotaTrailTypography
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    accountListUseCase: AccountListUseCase = NoopAccountListUseCase,
    retentionPreferenceStore: RetentionPreferenceStore = InMemoryRetentionPreferenceStore(),
    notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
    backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader = InMemoryBackgroundRefreshStatusReader(),
    diagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
    quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
    appUpdateChecker: AppUpdateCheckUseCase = NoopAppUpdateCheckUseCase,
    appUpdateDownloader: AppUpdateDownloadUseCase = NoopAppUpdateDownloadUseCase,
    backgroundRefreshScheduler: BackgroundRefreshScheduler = NoopBackgroundRefreshScheduler,
    notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
        NotificationWindowChoicesLoader { _, _ -> emptyList() },
    currencyPreferenceStore: CurrencyPreferenceStore = NoopCurrencyPreferenceStore,
    appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
    updatePreferenceStore: UpdatePreferenceStore = NoopUpdatePreferenceStore,
    updateCheckScheduler: UpdateCheckScheduler = NoopUpdateCheckScheduler,
    openUpdateDialogOnLaunch: Boolean = false,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            accountListUseCase = accountListUseCase,
            retentionPreferenceStore = retentionPreferenceStore,
            notificationPreferenceStore = notificationPreferenceStore,
            backgroundRefreshStatusReader = backgroundRefreshStatusReader,
            diagnosticsReader = diagnosticsReader,
            quotaHistoryClearUseCase = quotaHistoryClearUseCase,
            appUpdateChecker = appUpdateChecker,
            appUpdateDownloader = appUpdateDownloader,
            backgroundRefreshScheduler = backgroundRefreshScheduler,
            notificationWindowChoicesLoader = notificationWindowChoicesLoader,
            currencyPreferenceStore = currencyPreferenceStore,
            appearancePreferenceStore = appearancePreferenceStore,
            updatePreferenceStore = updatePreferenceStore,
            updateCheckScheduler = updateCheckScheduler,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val coroutineScope = rememberCoroutineScope()
    val diagnosticsClipboardLabel = stringResource(R.string.settings_diagnostics_clipboard_label)
    var pendingPermissionTarget by remember { mutableStateOf<SettingsNotificationPermissionTarget?>(null) }
    var batteryOptimizationIgnored by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    // The system dialog always returns RESULT_CANCELED, so re-read PowerManager on return instead of
    // trusting the result code to decide whether the hint should disappear.
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when (pendingPermissionTarget) {
            SettingsNotificationPermissionTarget.StatusNotification -> {
                if (granted) {
                    viewModel.setStatusNotificationEnabled(true)
                }
            }
            SettingsNotificationPermissionTarget.AccountErrors -> {
                if (granted) {
                    viewModel.setAccountErrorsEnabled(true)
                }
            }
            null -> Unit
        }
        pendingPermissionTarget = null
    }
    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }
    var updateDialogConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openUpdateDialogOnLaunch) {
        if (openUpdateDialogOnLaunch && !updateDialogConsumed) {
            updateDialogConsumed = true
            viewModel.showAvailableUpdateDialog()
        }
    }
    fun requestPermissionOrEnable(target: SettingsNotificationPermissionTarget, requestedEnabled: Boolean) {
        if (
            SettingsNotificationPermissionGate.shouldRequestPermission(
                sdkInt = Build.VERSION.SDK_INT,
                notificationPermissionGranted = context.hasNotificationPermission(),
                requestedEnabled = requestedEnabled,
            )
        ) {
            pendingPermissionTarget = target
        } else {
            when (target) {
                SettingsNotificationPermissionTarget.StatusNotification ->
                    viewModel.setStatusNotificationEnabled(requestedEnabled)
                SettingsNotificationPermissionTarget.AccountErrors ->
                    viewModel.setAccountErrorsEnabled(requestedEnabled)
            }
        }
    }
    SettingsScreen(
        uiState = uiState,
        modifier = modifier,
        onChoiceDialogRequested = viewModel::requestChoiceDialog,
        onChoiceDialogDismiss = viewModel::dismissChoiceDialog,
        onPersistentNotificationAccountSelected = viewModel::updatePersistentNotificationAccount,
        onPersistentNotificationWindowSelected = viewModel::updatePersistentNotificationWindow,
        onStatusNotificationChanged = { enabled ->
            requestPermissionOrEnable(SettingsNotificationPermissionTarget.StatusNotification, enabled)
        },
        onAccountErrorsChanged = { enabled ->
            requestPermissionOrEnable(SettingsNotificationPermissionTarget.AccountErrors, enabled)
        },
        onCautionThresholdChanged = { caution ->
            viewModel.updateAlertThresholds(caution, uiState.alerts.thresholds.warning)
        },
        onWarningThresholdChanged = { warning ->
            viewModel.updateAlertThresholds(uiState.alerts.thresholds.caution, warning)
        },
        onBalanceCautionChanged = { caution ->
            viewModel.updateBalanceThresholds(caution, uiState.alerts.balanceWarning)
        },
        onBalanceWarningChanged = { warning ->
            viewModel.updateBalanceThresholds(uiState.alerts.balanceCaution, warning)
        },
        onBackgroundRefreshChanged = viewModel::setBackgroundRefreshEnabled,
        onRefreshIntervalSelected = viewModel::updateRefreshInterval,
        onCurrencyTargetSelected = viewModel::updateCurrencyTarget,
        onThemeModeSelected = viewModel::updateThemeMode,
        onRetentionSelected = viewModel::updateRetention,
        onClearCurrentHistoryClick = viewModel::requestClearCurrentHistory,
        onClearAllHistoryClick = viewModel::requestClearAllHistory,
        onDataActionConfirm = viewModel::confirmDataAction,
        onDataActionDismiss = viewModel::cancelDataAction,
        onDiagnosticsToggle = viewModel::toggleDiagnosticsExpanded,
        onDiagnosticsCopyClick = {
            coroutineScope.launch {
                val copyModel = viewModel.copyDiagnostics()
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(diagnosticsClipboardLabel, copyModel.text),
                )
            }
        },
        onDiagnosticsRecheckClick = viewModel::requestDiagnosticsRecheck,
        onAboutRepositoryClick = {
            runCatching { context.startActivity(SettingsRepositoryLinkTarget.openIntent()) }
        },
        onAppUpdateCheckClick = viewModel::checkForUpdates,
        onAppUpdateDismiss = viewModel::dismissPendingUpdate,
        onAppUpdateDownloadClick = viewModel::downloadPendingUpdate,
        onAppUpdateAvailableClick = viewModel::showAvailableUpdateDialog,
        onAppUpdateAutoCheckChanged = viewModel::setAutoCheckUpdates,
        onAppUpdateNotifyChanged = viewModel::setNotifyOnUpdate,
        showBatteryOptimizationHint = SettingsBatteryOptimizationGate.shouldOfferExemption(
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            backgroundRefreshEnabled = uiState.refresh.backgroundRefreshEnabled,
        ),
        onBatteryOptimizationClick = {
            runCatching {
                batteryOptimizationLauncher.launch(
                    SettingsBatteryOptimizationTarget.requestExemptionIntent(context.packageName),
                )
            }
        },
    )
    pendingPermissionTarget?.let {
        NotificationPermissionRationaleDialog(
            onConfirm = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                pendingPermissionTarget = null
            },
        )
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    modifier: Modifier = Modifier,
    onChoiceDialogRequested: (SettingsChoiceDialog) -> Unit = {},
    onChoiceDialogDismiss: () -> Unit = {},
    onPersistentNotificationAccountSelected: (SettingsNotificationAccountSelection) -> Unit = {},
    onPersistentNotificationWindowSelected: (QuotaWindowId) -> Unit = {},
    onStatusNotificationChanged: (Boolean) -> Unit = {},
    onAccountErrorsChanged: (Boolean) -> Unit = {},
    onCautionThresholdChanged: (Int) -> Unit = {},
    onWarningThresholdChanged: (Int) -> Unit = {},
    onBalanceCautionChanged: (Double) -> Unit = {},
    onBalanceWarningChanged: (Double) -> Unit = {},
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    onRefreshIntervalSelected: (SettingsRefreshInterval) -> Unit = {},
    onCurrencyTargetSelected: (String) -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onRetentionSelected: (SettingsRetentionOption) -> Unit = {},
    onClearCurrentHistoryClick: () -> Unit = {},
    onClearAllHistoryClick: () -> Unit = {},
    onDataActionConfirm: () -> Unit = {},
    onDataActionDismiss: () -> Unit = {},
    onDiagnosticsToggle: () -> Unit = {},
    onDiagnosticsCopyClick: () -> Unit = {},
    onDiagnosticsRecheckClick: () -> Unit = {},
    onAboutRepositoryClick: () -> Unit = {},
    onAppUpdateCheckClick: () -> Unit = {},
    onAppUpdateDismiss: () -> Unit = {},
    onAppUpdateDownloadClick: () -> Unit = {},
    onAppUpdateAvailableClick: () -> Unit = {},
    onAppUpdateAutoCheckChanged: (Boolean) -> Unit = {},
    onAppUpdateNotifyChanged: (Boolean) -> Unit = {},
    showBatteryOptimizationHint: Boolean = false,
    onBatteryOptimizationClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.lg),
    ) {
        SettingsHeader()
        SettingsSectionLabel(R.string.settings_group_appearance)
        AppearanceCard(
            appearance = uiState.appearance,
            onThemeModeSelected = onThemeModeSelected,
        )
        SettingsSectionLabel(R.string.settings_group_persistent_notification)
        PersistentNotificationCard(
            persistentNotification = uiState.persistentNotification,
            onEnabledChanged = onStatusNotificationChanged,
            onAccountClick = { onChoiceDialogRequested(SettingsChoiceDialog.NotificationAccount) },
            onWindowClick = { onChoiceDialogRequested(SettingsChoiceDialog.NotificationWindow) },
        )
        SettingsSectionLabel(R.string.settings_group_alerts)
        AlertsCard(
            alerts = uiState.alerts,
            targetCurrency = uiState.currency.targetCurrency,
            onAccountErrorsChanged = onAccountErrorsChanged,
            onCautionThresholdChanged = onCautionThresholdChanged,
            onWarningThresholdChanged = onWarningThresholdChanged,
            onBalanceCautionChanged = onBalanceCautionChanged,
            onBalanceWarningChanged = onBalanceWarningChanged,
            onCurrencyTargetClick = { onChoiceDialogRequested(SettingsChoiceDialog.CurrencyTarget) },
        )
        SettingsSectionLabel(R.string.settings_group_refresh)
        RefreshCard(
            refresh = uiState.refresh,
            onBackgroundRefreshChanged = onBackgroundRefreshChanged,
            onIntervalClick = { onChoiceDialogRequested(SettingsChoiceDialog.RefreshInterval) },
            showBatteryOptimizationHint = showBatteryOptimizationHint,
            onBatteryOptimizationClick = onBatteryOptimizationClick,
        )
        SettingsSectionLabel(R.string.settings_group_data)
        DataCard(
            data = uiState.data,
            onRetentionClick = { onChoiceDialogRequested(SettingsChoiceDialog.Retention) },
            onClearCurrentHistoryClick = onClearCurrentHistoryClick,
            onClearAllHistoryClick = onClearAllHistoryClick,
        )
        SettingsSectionLabel(R.string.settings_group_diagnostics)
        DiagnosticsCard(uiState.diagnostics, onDiagnosticsToggle, onDiagnosticsCopyClick, onDiagnosticsRecheckClick)
        SettingsSectionLabel(R.string.settings_group_about)
        AboutCard(uiState.about, onAboutRepositoryClick)
        if (app.quotatrail.security.PersonalSecurityPolicy.UPSTREAM_UPDATES_ENABLED) {
            AppUpdateCard(
                update = uiState.update,
                onCheckClick = onAppUpdateCheckClick,
                onAvailableUpdateClick = onAppUpdateAvailableClick,
                onAutoCheckChanged = onAppUpdateAutoCheckChanged,
                onNotifyOnUpdateChanged = onAppUpdateNotifyChanged,
            )
        }
        Spacer(modifier = Modifier.height(TrailSpacing.bottomNavigationClearance))
    }

    uiState.data.pendingAction?.let { action ->
        ConfirmDataActionDialog(action, onDataActionConfirm, onDataActionDismiss)
    }
    uiState.pendingChoiceDialog?.let { dialog ->
        SettingsChoiceDialogContent(
            dialog = dialog,
            uiState = uiState,
            onPersistentNotificationAccountSelected = onPersistentNotificationAccountSelected,
            onPersistentNotificationWindowSelected = onPersistentNotificationWindowSelected,
            onRetentionSelected = onRetentionSelected,
            onRefreshIntervalSelected = onRefreshIntervalSelected,
            onCurrencyTargetSelected = onCurrencyTargetSelected,
            onDismiss = onChoiceDialogDismiss,
        )
    }
    if (app.quotatrail.security.PersonalSecurityPolicy.UPSTREAM_UPDATES_ENABLED) {
        uiState.update.pendingUpdate?.let { update ->
            AppUpdateAvailableDialog(
                versionName = update.versionName,
                releaseNotes = update.releaseNotes,
                onDownloadClick = onAppUpdateDownloadClick,
                onDismiss = onAppUpdateDismiss,
            )
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm)) {
        Text(
            text = stringResource(R.string.settings_title),
            style = QuotaTrailTypography.current.display,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SwitchRow(
    @StringRes titleResId: Int,
    @StringRes descriptionResId: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsItemText(titleResId, descriptionResId, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun DestructiveActionRow(@StringRes titleResId: Int, @StringRes descriptionResId: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsItemText(titleResId, descriptionResId, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, shape = QuotaTrailShapes.md) {
            Text(text = stringResource(R.string.settings_data_clear_action), color = QuotaTrailTheme.colors.danger)
        }
    }
}

@Composable
internal fun SettingsItemText(@StringRes titleResId: Int, @StringRes descriptionResId: Int?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs)) {
        Text(text = stringResource(titleResId), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (descriptionResId != null) {
            Text(text = stringResource(descriptionResId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OptionColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm), content = content)
}

@Composable
internal fun OptionButton(@StringRes labelResId: Int, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = QuotaTrailShapes.md) {
            Text(text = stringResource(labelResId))
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = QuotaTrailShapes.md) {
            Text(text = stringResource(labelResId))
        }
    }
}

@Composable
internal fun OptionTextButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = QuotaTrailShapes.md) {
            Text(text = label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = QuotaTrailShapes.md) {
            Text(text = label)
        }
    }
}

@Composable
private fun ConfirmDataActionDialog(
    action: SettingsPendingDataAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(action.titleResId)) },
        text = { Text(text = stringResource(action.messageResId)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.settings_data_confirm_clear), color = QuotaTrailTheme.colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun AppUpdateAvailableDialog(
    versionName: String,
    releaseNotes: String?,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_update_dialog_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.settings_update_dialog_message, versionName))
                if (!releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(TrailSpacing.sm))
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownloadClick) {
                Text(text = stringResource(R.string.settings_update_dialog_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_update_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SettingsChoiceDialogContent(
    dialog: SettingsChoiceDialog,
    uiState: SettingsUiState,
    onPersistentNotificationAccountSelected: (SettingsNotificationAccountSelection) -> Unit,
    onPersistentNotificationWindowSelected: (QuotaWindowId) -> Unit,
    onRetentionSelected: (SettingsRetentionOption) -> Unit,
    onRefreshIntervalSelected: (SettingsRefreshInterval) -> Unit,
    onCurrencyTargetSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    when (dialog) {
                        SettingsChoiceDialog.NotificationAccount -> R.string.settings_notification_account_title
                        SettingsChoiceDialog.NotificationWindow -> R.string.settings_notification_display_quota_title
                        SettingsChoiceDialog.Retention -> R.string.settings_retention_title
                        SettingsChoiceDialog.RefreshInterval -> R.string.settings_refresh_interval_title
                        SettingsChoiceDialog.CurrencyTarget -> R.string.settings_currency_target_title
                    },
                ),
            )
        },
        text = {
            OptionColumn {
                when (dialog) {
                    SettingsChoiceDialog.NotificationAccount ->
                        uiState.persistentNotification.accountChoices.forEach { choice ->
                            OptionTextButton(
                                label = choice.selection.displayLabel(),
                                selected = uiState.persistentNotification.accountSelection == choice.selection,
                            ) {
                                onPersistentNotificationAccountSelected(choice.selection)
                            }
                        }
                    SettingsChoiceDialog.NotificationWindow ->
                        uiState.persistentNotification.windowChoices.forEach { choice ->
                            OptionButton(
                                choice.labelResId,
                                uiState.persistentNotification.selectedWindowId == choice.windowId,
                            ) {
                                onPersistentNotificationWindowSelected(choice.windowId)
                            }
                        }
                    SettingsChoiceDialog.Retention -> SettingsRetentionOption.entries.forEach { option ->
                        OptionButton(option.labelResId, uiState.data.retention == option) {
                            onRetentionSelected(option)
                        }
                    }
                    SettingsChoiceDialog.RefreshInterval -> SettingsRefreshInterval.entries.forEach { option ->
                        OptionButton(option.labelResId, uiState.refresh.interval == option) {
                            onRefreshIntervalSelected(option)
                        }
                    }
                    SettingsChoiceDialog.CurrencyTarget -> uiState.currency.supportedCurrencies.forEach { code ->
                        OptionTextButton(
                            label = code,
                            selected = uiState.currency.targetCurrency == code,
                        ) {
                            onCurrencyTargetSelected(code)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun SettingsSectionLabel(@StringRes resId: Int) {
    Text(text = stringResource(resId), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NotificationPermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_notification_permission_title)) },
        text = { Text(text = stringResource(R.string.settings_notification_permission_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.settings_notification_permission_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
internal fun SettingsSurfaceCard(content: @Composable () -> Unit) {
    Card(
        shape = QuotaTrailShapes.sm,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(TrailSpacing.lg)) {
            content()
        }
    }
}

private enum class SettingsNotificationPermissionTarget {
    StatusNotification,
    AccountErrors,
}

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

// Null when PowerManager is unavailable so the hint stays hidden rather than guessing.
private fun Context.isIgnoringBatteryOptimizations(): Boolean? =
    (getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(packageName)
