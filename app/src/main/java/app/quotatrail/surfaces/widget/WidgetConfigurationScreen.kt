package app.quotatrail.surfaces.widget

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.providers.ProviderRegistry

internal data class WidgetWindowOption(
    val windowId: String,
    @get:StringRes val labelResId: Int,
)

internal data class WidgetConfigurationScreenState(
    val accounts: List<ProviderAccount> = emptyList(),
    val slots: List<WidgetSlotConfiguration> = emptyList(),
    val defaultWindowId: String = FIVE_HOUR_WINDOW_ID,
    val currentAccountId: String? = null,
    val windowOptionsByAccount: Map<String, List<WidgetWindowOption>> = emptyMap(),
) {
    fun slot(index: Int): WidgetSlotConfiguration? = slots.getOrNull(index)?.takeIf { it.isComplete }

    fun windowsFor(accountId: String): List<WidgetWindowOption> =
        windowOptionsByAccount[accountId].orEmpty()

    fun replaceSlot(index: Int, slot: WidgetSlotConfiguration?): WidgetConfigurationScreenState {
        val mutable = slots.toMutableList()
        while (mutable.size <= index) mutable += EMPTY_SLOT
        mutable[index] = slot ?: EMPTY_SLOT
        return copy(slots = mutable.take(WIDGET_MAX_FIELDS))
    }

    companion object {
        private val EMPTY_SLOT = WidgetSlotConfiguration("", "", "")
    }
}

@Composable
internal fun WidgetConfigurationRoute(
    appWidgetId: Int,
    loadState: suspend () -> WidgetConfigurationScreenState,
    onCancel: () -> Unit,
    onSave: (List<WidgetSlotConfiguration>) -> Unit,
) {
    var state by remember(appWidgetId) { mutableStateOf<WidgetConfigurationScreenState?>(null) }
    var expandedSlot by remember(appWidgetId) { mutableStateOf(0) }
    var isSaving by remember(appWidgetId) { mutableStateOf(false) }

    LaunchedEffect(appWidgetId) { state = loadState() }
    val currentState = state
    if (currentState == null) {
        LoadingConfigurationScreen()
        return
    }

    WidgetConfigurationScreen(
        state = currentState,
        expandedSlot = expandedSlot,
        isSaving = isSaving,
        onSlotToggle = { expandedSlot = if (expandedSlot == it) -1 else it },
        onAccountSelected = { index, account ->
            val options = currentState.windowsFor(account.localAccountId.value)
            val existingWindow = currentState.slot(index)?.windowId
                ?.takeIf { id -> options.any { it.windowId == id } }
            val windowId = existingWindow
                ?: options.firstOrNull { it.windowId == currentState.defaultWindowId }?.windowId
                ?: options.firstOrNull()?.windowId
                ?: ""
            state = currentState.replaceSlot(
                index,
                WidgetSlotConfiguration(
                    providerId = account.providerId.value,
                    localAccountId = account.localAccountId.value,
                    windowId = windowId,
                ),
            )
        },
        onWindowSelected = { index, windowId ->
            currentState.slot(index)?.let { slot ->
                state = currentState.replaceSlot(index, slot.copy(windowId = windowId))
            }
        },
        onSlotCleared = { index -> state = currentState.replaceSlot(index, null) },
        onCancel = onCancel,
        onSave = {
            isSaving = true
            onSave(currentState.slots.filter(WidgetSlotConfiguration::isComplete))
        },
    )
}

@Composable
private fun LoadingConfigurationScreen() {
    WidgetConfigurationDialogContainer {
        Column(
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.widget_config_loading))
        }
    }
}

@Composable
private fun WidgetConfigurationScreen(
    state: WidgetConfigurationScreenState,
    expandedSlot: Int,
    isSaving: Boolean,
    onSlotToggle: (Int) -> Unit,
    onAccountSelected: (Int, ProviderAccount) -> Unit,
    onWindowSelected: (Int, String) -> Unit,
    onSlotCleared: (Int) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    WidgetConfigurationDialogContainer {
        Column(
            modifier = Modifier
                .heightIn(max = WIDGET_CONFIG_DIALOG_MAX_HEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs)) {
                Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.widget_config_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            repeat(WIDGET_MAX_FIELDS) { index ->
                SlotEditor(
                    index = index,
                    state = state,
                    expanded = expandedSlot == index,
                    onToggle = { onSlotToggle(index) },
                    onAccountSelected = { onAccountSelected(index, it) },
                    onWindowSelected = { onWindowSelected(index, it) },
                    onClear = { onSlotCleared(index) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !isSaving) {
                    Text(stringResource(R.string.widget_config_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving && state.slots.any(WidgetSlotConfiguration::isComplete),
                    shape = QuotaTrailShapes.md,
                ) {
                    Text(stringResource(if (isSaving) R.string.widget_config_saving else R.string.widget_config_save))
                }
            }
        }
    }
}

@Composable
private fun SlotEditor(
    index: Int,
    state: WidgetConfigurationScreenState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAccountSelected: (ProviderAccount) -> Unit,
    onWindowSelected: (String) -> Unit,
    onClear: () -> Unit,
) {
    val slot = state.slot(index)
    val selectedAccount = state.accounts.firstOrNull {
        it.providerId.value == slot?.providerId && it.localAccountId.value == slot.localAccountId
    }
    val selectedWindow = selectedAccount?.let { state.windowsFor(it.localAccountId.value) }
        ?.firstOrNull { it.windowId == slot?.windowId }
    val summary = if (selectedAccount == null || selectedWindow == null) {
        stringResource(R.string.widget_config_slot_empty)
    } else {
        "${ProviderRegistry.displayNameFor(selectedAccount.providerId)} · ${stringResource(selectedWindow.labelResId)}"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = QuotaTrailShapes.instrument,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(TrailSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(QuotaTrailShapes.sm)
                        .background(if (index % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index % 2 == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.widget_config_slot_title, index + 1), style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(if (expanded) R.string.widget_config_collapse else R.string.widget_config_expand),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Text(stringResource(R.string.widget_config_choose_account), style = MaterialTheme.typography.labelLarge)
                state.accounts.forEach { account ->
                    AccountOptionRow(
                        account = account,
                        selected = account == selectedAccount,
                        current = account.localAccountId.value == state.currentAccountId,
                        onClick = { onAccountSelected(account) },
                    )
                }
                if (selectedAccount != null) {
                    Text(stringResource(R.string.widget_config_choose_limit), style = MaterialTheme.typography.labelLarge)
                    state.windowsFor(selectedAccount.localAccountId.value).forEach { option ->
                        RadioOptionRow(
                            label = stringResource(option.labelResId),
                            selected = option.windowId == slot?.windowId,
                            onClick = { onWindowSelected(option.windowId) },
                        )
                    }
                }
                if (slot != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.widget_config_clear_slot)) }
                }
            }
        }
    }
}

@Composable
private fun AccountOptionRow(
    account: ProviderAccount,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit,
) {
    RadioOptionRow(
        label = account.displayName,
        supporting = if (current) stringResource(R.string.widget_config_account_current)
        else ProviderRegistry.displayNameFor(account.providerId),
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WidgetConfigurationDialogContainer(content: @Composable () -> Unit) {
    Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .widthIn(min = WIDGET_CONFIG_DIALOG_MIN_WIDTH, max = WIDGET_CONFIG_DIALOG_MAX_WIDTH)
                .padding(WIDGET_CONFIG_DIALOG_OUTER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = QuotaTrailShapes.instrument,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
            ) {
                Box(modifier = Modifier.padding(TrailSpacing.lg)) { content() }
            }
        }
    }
}

internal fun defaultSlotFor(
    account: ProviderAccount?,
    options: List<WidgetWindowOption>,
    preferredWindowId: String,
): WidgetSlotConfiguration? {
    account ?: return null
    val window = options.firstOrNull { it.windowId == preferredWindowId } ?: options.firstOrNull() ?: return null
    return WidgetSlotConfiguration(account.providerId.value, account.localAccountId.value, window.windowId)
}

internal fun sanitizeSlots(
    slots: List<WidgetSlotConfiguration>,
    accounts: List<ProviderAccount>,
    optionsByAccount: Map<String, List<WidgetWindowOption>>,
): List<WidgetSlotConfiguration> = slots.filter { slot ->
    accounts.any { it.providerId.value == slot.providerId && it.localAccountId.value == slot.localAccountId } &&
        optionsByAccount[slot.localAccountId].orEmpty().any { it.windowId == slot.windowId }
}.take(WIDGET_MAX_FIELDS)

private val WIDGET_CONFIG_DIALOG_MIN_WIDTH = 300.dp
private val WIDGET_CONFIG_DIALOG_MAX_WIDTH = 420.dp
private val WIDGET_CONFIG_DIALOG_MAX_HEIGHT = 620.dp
private val WIDGET_CONFIG_DIALOG_OUTER_PADDING = 12.dp
private const val FIVE_HOUR_WINDOW_ID = "five_hour"
