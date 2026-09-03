package app.quotatrail.surfaces.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.providers.ProviderRegistry
import java.time.Instant

internal fun Preferences.toWidgetQuotaState(): WidgetQuotaState {
    val providerId = this[WidgetQuotaPreferenceKeys.providerId]
    return WidgetQuotaState(
        status = enumValue(WidgetQuotaPreferenceKeys.status, WidgetQuotaStatus.NoAccount),
        providerName = this[WidgetQuotaPreferenceKeys.providerName] ?: DEFAULT_PROVIDER_NAME,
        providerId = providerId,
        localAccountId = this[WidgetQuotaPreferenceKeys.localAccountId],
        accountName = this[WidgetQuotaPreferenceKeys.accountName],
        tone = enumValue(WidgetQuotaPreferenceKeys.tone, WidgetQuotaTone.Neutral),
        // Default a never-written widget to Home, not the retired provider-selection deep-link.
        clickTarget = enumValue(WidgetQuotaPreferenceKeys.clickTarget, WidgetClickTarget.Home),
        fields = readFields(),
        lastUpdatedAt = this[WidgetQuotaPreferenceKeys.lastUpdatedAt]?.toInstantOrNull(),
        // A freshly-placed widget has no persisted state yet. Default to the unconfigured guide card
        // (the clean centered "尚未配置" copy) rather than the data layout, and to no-accounts so the
        // hint reads "add an account first" — matching what the app writes once it computes real state,
        // so the first render isn't a different-looking placeholder than the post-open state.
        isUnconfigured = this[WidgetQuotaPreferenceKeys.isUnconfigured] ?: true,
        hasAccounts = this[WidgetQuotaPreferenceKeys.hasAccounts] ?: false,
        // Resolve the brand icon from the provider id at read time — resource ids aren't stable
        // enough to persist across app updates, so we never serialize them.
        providerIconRes = providerId?.takeIf { it.isNotBlank() }?.let { ProviderRegistry.iconFor(ProviderId(it)) },
    )
}

internal fun Preferences.toWidgetQuotaConfiguration(): WidgetQuotaConfiguration =
    WidgetQuotaConfiguration(
        slots = (0 until WIDGET_MAX_FIELDS).mapNotNull { index ->
            val providerId = this[WidgetQuotaPreferenceKeys.configSlotProviderId(index)] ?: return@mapNotNull null
            val localAccountId = this[WidgetQuotaPreferenceKeys.configSlotAccountId(index)] ?: return@mapNotNull null
            val windowId = this[WidgetQuotaPreferenceKeys.configSlotWindowId(index)] ?: return@mapNotNull null
            WidgetSlotConfiguration(providerId, localAccountId, windowId).takeIf { it.isComplete }
        },
    )

internal fun MutablePreferences.writeWidgetQuotaConfiguration(configuration: WidgetQuotaConfiguration) {
    val slots = configuration.slots.filter { it.isComplete }.take(WIDGET_MAX_FIELDS)
    for (index in 0 until WIDGET_MAX_FIELDS) {
        val slot = slots.getOrNull(index)
        putOrRemove(WidgetQuotaPreferenceKeys.configSlotProviderId(index), slot?.providerId)
        putOrRemove(WidgetQuotaPreferenceKeys.configSlotAccountId(index), slot?.localAccountId)
        putOrRemove(WidgetQuotaPreferenceKeys.configSlotWindowId(index), slot?.windowId)
    }
}

internal fun MutablePreferences.writeWidgetQuotaState(state: WidgetQuotaState) {
    this[WidgetQuotaPreferenceKeys.status] = state.status.name
    this[WidgetQuotaPreferenceKeys.providerName] = state.providerName
    putOrRemove(WidgetQuotaPreferenceKeys.providerId, state.providerId)
    putOrRemove(WidgetQuotaPreferenceKeys.localAccountId, state.localAccountId)
    putOrRemove(WidgetQuotaPreferenceKeys.accountName, state.accountName)
    this[WidgetQuotaPreferenceKeys.tone] = state.tone.name
    this[WidgetQuotaPreferenceKeys.clickTarget] = state.clickTarget.name
    this[WidgetQuotaPreferenceKeys.isUnconfigured] = state.isUnconfigured
    this[WidgetQuotaPreferenceKeys.hasAccounts] = state.hasAccounts
    putOrRemove(WidgetQuotaPreferenceKeys.lastUpdatedAt, state.lastUpdatedAt?.toString())
    writeFields(state.fields)
}

internal fun MutablePreferences.clearWidgetQuotaStateIfAccountMatches(
    providerId: String,
    localAccountId: String,
): Boolean {
    val currentState = toWidgetQuotaState()
    val currentConfiguration = toWidgetQuotaConfiguration()
    val stateMatches = currentState.providerId == providerId && currentState.localAccountId == localAccountId
    val configurationMatches = currentConfiguration.accountMatches(providerId, localAccountId)
    if (!stateMatches && !configurationMatches) {
        return false
    }
    val remainingFields = currentState.fields.filterNot {
        it.providerId == providerId && it.localAccountId == localAccountId
    }
    if (stateMatches || remainingFields.size != currentState.fields.size) {
        writeWidgetQuotaState(
            if (remainingFields.isEmpty()) currentState.copy(
                status = WidgetQuotaStatus.NoAccount,
                providerId = null,
                localAccountId = null,
                accountName = null,
                tone = WidgetQuotaTone.Neutral,
                clickTarget = WidgetClickTarget.Home,
                fields = emptyList(),
                lastUpdatedAt = null,
                isUnconfigured = true,
                refreshAccountIds = emptyList(),
            ) else currentState.copy(
                providerName = DEFAULT_PROVIDER_NAME,
                providerId = null,
                localAccountId = null,
                accountName = null,
                fields = remainingFields,
                refreshAccountIds = remainingFields.mapNotNull { it.localAccountId }.distinct(),
            ),
        )
    }
    if (configurationMatches) {
        writeWidgetQuotaConfiguration(
            WidgetQuotaConfiguration(
                currentConfiguration.slots.filterNot { it.accountMatches(providerId, localAccountId) },
            ),
        )
    }
    return true
}

private fun Preferences.readFields(): List<WidgetField> {
    val count = (this[WidgetQuotaPreferenceKeys.fieldCount] ?: 0).coerceIn(0, WIDGET_MAX_FIELDS)
    return (0 until count).mapNotNull { i ->
        val windowId = this[WidgetQuotaPreferenceKeys.fieldWindowId(i)] ?: return@mapNotNull null
        WidgetField(
            windowId = windowId,
            isBalance = this[WidgetQuotaPreferenceKeys.fieldIsBalance(i)] ?: false,
            percent = this[WidgetQuotaPreferenceKeys.fieldPercent(i)],
            balanceAmount = this[WidgetQuotaPreferenceKeys.fieldBalanceAmount(i)],
            balanceCurrency = this[WidgetQuotaPreferenceKeys.fieldBalanceCurrency(i)],
            resetAt = this[WidgetQuotaPreferenceKeys.fieldResetAt(i)]?.toInstantOrNull(),
            tone = enumValue(WidgetQuotaPreferenceKeys.fieldTone(i), WidgetQuotaTone.Neutral),
            providerName = this[WidgetQuotaPreferenceKeys.fieldProviderName(i)],
            providerId = this[WidgetQuotaPreferenceKeys.fieldProviderId(i)],
            localAccountId = this[WidgetQuotaPreferenceKeys.fieldLocalAccountId(i)],
            accountName = this[WidgetQuotaPreferenceKeys.fieldAccountName(i)],
        )
    }
}

private fun MutablePreferences.writeFields(fields: List<WidgetField>) {
    val capped = fields.take(WIDGET_MAX_FIELDS)
    this[WidgetQuotaPreferenceKeys.fieldCount] = capped.size
    for (i in 0 until WIDGET_MAX_FIELDS) {
        val field = capped.getOrNull(i)
        if (field == null) {
            remove(WidgetQuotaPreferenceKeys.fieldWindowId(i))
            remove(WidgetQuotaPreferenceKeys.fieldIsBalance(i))
            remove(WidgetQuotaPreferenceKeys.fieldPercent(i))
            remove(WidgetQuotaPreferenceKeys.fieldBalanceAmount(i))
            remove(WidgetQuotaPreferenceKeys.fieldBalanceCurrency(i))
            remove(WidgetQuotaPreferenceKeys.fieldResetAt(i))
            remove(WidgetQuotaPreferenceKeys.fieldTone(i))
            remove(WidgetQuotaPreferenceKeys.fieldProviderName(i))
            remove(WidgetQuotaPreferenceKeys.fieldProviderId(i))
            remove(WidgetQuotaPreferenceKeys.fieldLocalAccountId(i))
            remove(WidgetQuotaPreferenceKeys.fieldAccountName(i))
        } else {
            this[WidgetQuotaPreferenceKeys.fieldWindowId(i)] = field.windowId
            this[WidgetQuotaPreferenceKeys.fieldIsBalance(i)] = field.isBalance
            putOrRemove(WidgetQuotaPreferenceKeys.fieldPercent(i), field.percent)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldBalanceAmount(i), field.balanceAmount)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldBalanceCurrency(i), field.balanceCurrency)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldResetAt(i), field.resetAt?.toString())
            this[WidgetQuotaPreferenceKeys.fieldTone(i)] = field.tone.name
            putOrRemove(WidgetQuotaPreferenceKeys.fieldProviderName(i), field.providerName)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldProviderId(i), field.providerId)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldLocalAccountId(i), field.localAccountId)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldAccountName(i), field.accountName)
        }
    }
}

private inline fun <reified T : Enum<T>> Preferences.enumValue(
    key: Preferences.Key<String>,
    defaultValue: T,
): T =
    this[key]?.let { rawValue -> runCatching { enumValueOf<T>(rawValue) }.getOrNull() } ?: defaultValue

private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value == null) remove(key) else this[key] = value
}

private fun MutablePreferences.putOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) remove(key) else this[key] = value
}

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

internal object WidgetQuotaPreferenceKeys {
    val status = stringPreferencesKey("widget_quota_status")
    val providerName = stringPreferencesKey("widget_provider_name")
    val providerId = stringPreferencesKey("widget_provider_id")
    val localAccountId = stringPreferencesKey("widget_local_account_id")
    val accountName = stringPreferencesKey("widget_account_name")
    val tone = stringPreferencesKey("widget_tone")
    val clickTarget = stringPreferencesKey("widget_click_target")
    val isUnconfigured = booleanPreferencesKey("widget_is_unconfigured")
    val hasAccounts = booleanPreferencesKey("widget_has_accounts")
    val lastUpdatedAt = stringPreferencesKey("widget_last_updated_at")
    val fieldCount = intPreferencesKey("widget_field_count")
    fun configSlotProviderId(i: Int) = stringPreferencesKey("widget_config_slot_${i}_provider_id")
    fun configSlotAccountId(i: Int) = stringPreferencesKey("widget_config_slot_${i}_account_id")
    fun configSlotWindowId(i: Int) = stringPreferencesKey("widget_config_slot_${i}_window_id")

    fun fieldWindowId(i: Int) = stringPreferencesKey("widget_field_${i}_window_id")
    fun fieldIsBalance(i: Int) = booleanPreferencesKey("widget_field_${i}_is_balance")
    fun fieldPercent(i: Int) = intPreferencesKey("widget_field_${i}_percent")
    fun fieldBalanceAmount(i: Int) = stringPreferencesKey("widget_field_${i}_balance_amount")
    fun fieldBalanceCurrency(i: Int) = stringPreferencesKey("widget_field_${i}_balance_currency")
    fun fieldResetAt(i: Int) = stringPreferencesKey("widget_field_${i}_reset_at")
    fun fieldTone(i: Int) = stringPreferencesKey("widget_field_${i}_tone")
    fun fieldProviderName(i: Int) = stringPreferencesKey("widget_field_${i}_provider_name")
    fun fieldProviderId(i: Int) = stringPreferencesKey("widget_field_${i}_provider_id")
    fun fieldLocalAccountId(i: Int) = stringPreferencesKey("widget_field_${i}_account_id")
    fun fieldAccountName(i: Int) = stringPreferencesKey("widget_field_${i}_account_name")
}

private const val DEFAULT_PROVIDER_NAME = "QuotaTrail"
