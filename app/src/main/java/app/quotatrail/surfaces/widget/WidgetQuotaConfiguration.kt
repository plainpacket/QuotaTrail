package app.quotatrail.surfaces.widget

/** Maximum independently configured fields, matching the 4×2 widget capacity. */
const val WIDGET_MAX_FIELDS = 4

data class WidgetSlotConfiguration(
    val providerId: String,
    val localAccountId: String,
    val windowId: String,
) {
    val isComplete: Boolean
        get() = providerId.isNotBlank() && localAccountId.isNotBlank() && windowId.isNotBlank()

    fun accountMatches(providerId: String, localAccountId: String): Boolean =
        this.providerId == providerId && this.localAccountId == localAccountId
}

data class WidgetQuotaConfiguration(
    val slots: List<WidgetSlotConfiguration> = emptyList(),
) {
    val isDefault: Boolean get() = slots.isEmpty()

    val refreshAccountIds: List<String>
        get() = slots.map { it.localAccountId }.filter(String::isNotBlank).distinct()

    fun accountMatches(providerId: String, localAccountId: String): Boolean =
        slots.any { it.accountMatches(providerId, localAccountId) }
}

fun interface WidgetQuotaStateLoader {
    suspend fun loadWidgetQuotaState(configuration: WidgetQuotaConfiguration): WidgetQuotaState
}
