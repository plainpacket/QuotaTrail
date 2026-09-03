package app.quotatrail.surfaces.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.quotatrail.QuotaTrailApplication
import app.quotatrail.domain.model.AccountStatus
import app.quotatrail.domain.quota.hasDisplayableQuotaValue
import app.quotatrail.presentation.quota.quotaWindowLabelRes
import app.quotatrail.presentation.theme.QuotaTrailTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun isOwnWidgetProvider(
    providerInfo: AppWidgetProviderInfo?,
    expectedProvider: ComponentName,
): Boolean = providerInfo?.provider == expectedProvider

class QuotaTrailWidgetSetupActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        configureDialogWindow()

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val expectedProvider = ComponentName(this, QuotaTrailWidgetReceiver::class.java)
        val providerInfo = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (!isOwnWidgetProvider(providerInfo, expectedProvider)) {
            finish()
            return
        }

        setContent {
            QuotaTrailTheme {
                WidgetConfigurationRoute(
                    appWidgetId = appWidgetId,
                    loadState = { loadConfigurationState(appWidgetId) },
                    onCancel = { finish() },
                    onSave = { slots -> saveConfiguration(appWidgetId, slots) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun configureDialogWindow() {
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(WIDGET_CONFIG_DIM_AMOUNT)
    }

    private suspend fun loadConfigurationState(appWidgetId: Int): WidgetConfigurationScreenState =
        withContext(Dispatchers.IO) {
            val app = application as QuotaTrailApplication
            val glanceId = GlanceAppWidgetManager(this@QuotaTrailWidgetSetupActivity)
                .getGlanceIdBy(appWidgetId)
            val preferences = getAppWidgetState<Preferences>(
                context = this@QuotaTrailWidgetSetupActivity,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId,
            )
            val configuration = preferences.toWidgetQuotaConfiguration()
            val accounts = app.accountListUseCase.loadAccounts()
            val selectableAccounts = accounts.accounts.filterNot { it.status == AccountStatus.Deleted }
            val firstConfiguredSlot = configuration.slots.firstOrNull()
            val selectedAccount = selectableAccounts.firstOrNull {
                firstConfiguredSlot?.providerId == it.providerId.value &&
                    firstConfiguredSlot.localAccountId == it.localAccountId.value
            } ?: selectableAccounts.firstOrNull { it.localAccountId == accounts.currentAccountId }
                ?: selectableAccounts.firstOrNull()

            val windowOptionsByAccount = selectableAccounts.associate { account ->
                val options = accounts.latestQuotaSnapshots[account.localAccountId]?.windows
                    ?.filter { it.hasDisplayableQuotaValue() }
                    ?.map { WidgetWindowOption(it.windowId.value, quotaWindowLabelRes(it.windowId.value)) }
                    ?.distinctBy { it.windowId }
                    .orEmpty()
                account.localAccountId.value to options
            }

            val defaultWindowId = app.primaryQuotaWindowPreferenceStore.primaryQuotaWindowId().value
            val sanitizedSlots = sanitizeSlots(configuration.slots, selectableAccounts, windowOptionsByAccount)
            val configuredSlots = sanitizedSlots.ifEmpty {
                listOfNotNull(
                    defaultSlotFor(
                        account = selectedAccount,
                        options = windowOptionsByAccount[selectedAccount?.localAccountId?.value].orEmpty(),
                        preferredWindowId = defaultWindowId,
                    ),
                )
            }
            val editableSlots = configuredSlots + List(WIDGET_MAX_FIELDS - configuredSlots.size) {
                WidgetSlotConfiguration("", "", "")
            }

            WidgetConfigurationScreenState(
                accounts = selectableAccounts,
                slots = editableSlots,
                defaultWindowId = defaultWindowId,
                currentAccountId = accounts.currentAccountId?.value,
                windowOptionsByAccount = windowOptionsByAccount,
            )
        }

    private fun saveConfiguration(
        appWidgetId: Int,
        slots: List<WidgetSlotConfiguration>,
    ) {
        activityScope.launch {
            val app = application as QuotaTrailApplication
            val glanceId = GlanceAppWidgetManager(this@QuotaTrailWidgetSetupActivity)
                .getGlanceIdBy(appWidgetId)
            val configuration = WidgetQuotaConfiguration(slots = slots.filter { it.isComplete }.take(WIDGET_MAX_FIELDS))
            val widgetState = withContext(Dispatchers.IO) {
                app.widgetQuotaStateLoader.loadWidgetQuotaState(configuration)
            }
            updateAppWidgetState(this@QuotaTrailWidgetSetupActivity, glanceId) { preferences ->
                preferences.writeWidgetQuotaConfiguration(configuration)
                preferences.writeWidgetQuotaState(widgetState)
            }
            QuotaTrailWidget().update(this@QuotaTrailWidgetSetupActivity, glanceId)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private companion object {
        const val WIDGET_CONFIG_DIM_AMOUNT = 0.32f
    }
}
