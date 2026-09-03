package app.quotatrail.surfaces.notification

import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.currency.CurrencyPreferences
import app.quotatrail.domain.currency.ExchangeRates
import app.quotatrail.domain.currency.withConvertedBalance
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.sync.CurrentQuotaStatePublisher
import java.time.Clock
import java.time.Instant

class UsageStatusPublisher(
    private val notificationSink: NotificationSink,
    private val alertStateStore: NotificationAlertStateStore,
    private val optionsReader: NotificationRequestOptionsReader,
    private val alertThresholdsReader: AlertThresholdsReader,
    private val orchestrator: NotificationCoordinator = NotificationCoordinator(),
    private val alertPolicy: AlertPolicy = AlertPolicy(),
    private val alertWindowPreferenceReader: QuotaAlertWindowPreferenceReader =
        PrimaryQuotaAlertWindowPreferenceReader,
    private val statusNotificationStatesLoader: StatusNotificationStatesLoader =
        PassthroughStatusNotificationStatesLoader,
    private val accountErrorEventReader: AccountErrorEventReader = NoopAccountErrorEventReader,
    private val clock: Clock,
    private val currencyPreferenceReader: CurrencyPreferenceReader = NoopCurrencyPreferenceReader,
    private val exchangeRateReader: ExchangeRateReader = NoopExchangeRateReader,
) : CurrentQuotaStatePublisher {
    override suspend fun publish(state: CurrentQuotaState) {
        val options = optionsReader.currentOptions()
        if (!options.notificationPermissionAvailable || !options.statusNotificationEnabled) {
            notificationSink.cancel(NotificationCoordinator.STATUS_NOTIFICATION_ID)
        }
        val thresholds = alertThresholdsReader.currentThresholds()
        val convertedState = run {
            val currency = currencyPreferenceReader.currencyPreferences()
            val rates = exchangeRateReader.currentRates()
            if (rates != null && state.snapshot != null) {
                state.copy(
                    snapshot = state.snapshot.copy(
                        windows = state.snapshot.windows.map {
                            it.withConvertedBalance(currency.targetCurrency, rates)
                        },
                    ),
                    primaryWindow = state.primaryWindow
                        ?.withConvertedBalance(currency.targetCurrency, rates),
                )
            } else {
                state
            }
        }
        val alertEvents = if (options.notificationPermissionAvailable && options.quotaAlertsEnabled) {
            alertPolicy.evaluate(
                state = convertedState,
                thresholds = thresholds,
                enabledWindowIds = alertWindowPreferenceReader.enabledWindowIds(convertedState),
            ).filterNot { alertStateStore.hasNotified(it.key) }
        } else {
            emptyList()
        }
        val statusStates = if (options.notificationPermissionAvailable && options.statusNotificationEnabled) {
            statusNotificationStatesLoader.loadStatusNotificationStates(refreshedState = state)
        } else {
            listOf(state)
        }
        val statusRequests = orchestrator.buildRequests(
            state = statusStates.firstOrNull() ?: state,
            statusStates = statusStates,
            options = options.copy(
                quotaAlertsEnabled = false,
                accountErrorsEnabled = false,
            ),
        )
        val alertRequests = alertEvents.mapNotNull { event ->
            val request = orchestrator.buildRequests(
                state = state,
                alertEvents = listOf(event),
                options = options.copy(
                    statusNotificationEnabled = false,
                    accountErrorsEnabled = false,
                ),
            ).firstOrNull { it.channelId == NotificationChannels.QUOTA_ALERTS_CHANNEL_ID }
            request?.let { it to event }
        }
        val accountErrorEvent = if (options.notificationPermissionAvailable && options.accountErrorsEnabled) {
            accountErrorEventReader.accountErrorEvent(state)
        } else {
            null
        }
        val accountErrorRequests = orchestrator.buildRequests(
            state = state,
            options = options.copy(
                statusNotificationEnabled = false,
                quotaAlertsEnabled = false,
            ),
            accountErrorEvent = accountErrorEvent,
        )

        statusRequests.forEach(notificationSink::post)

        if (options.notificationPermissionAvailable && options.quotaAlertsEnabled) {
            val notifiedAt = clock.instant()
            alertRequests.forEach { (request, event) ->
                notificationSink.post(request)
                alertStateStore.markNotified(event = event, notifiedAt = notifiedAt)
            }
        }
        accountErrorRequests.forEach(notificationSink::post)
    }
}

fun interface NotificationSink {
    fun post(request: NotificationRequest)

    fun cancel(notificationId: Int) = Unit
}

fun interface NotificationRequestOptionsReader {
    suspend fun currentOptions(): NotificationRequestOptions
}

class StaticNotificationRequestOptionsReader(
    private val options: NotificationRequestOptions,
) : NotificationRequestOptionsReader {
    override suspend fun currentOptions(): NotificationRequestOptions = options
}

fun interface AlertThresholdsReader {
    suspend fun currentThresholds(): AlertThresholds
}

class StaticAlertThresholdsReader(
    private val thresholds: AlertThresholds,
) : AlertThresholdsReader {
    override suspend fun currentThresholds(): AlertThresholds = thresholds
}

interface NotificationAlertStateStore {
    suspend fun hasNotified(key: AlertDedupeKey): Boolean

    suspend fun markNotified(event: QuotaAlertEvent, notifiedAt: Instant)
}

fun interface QuotaAlertWindowPreferenceReader {
    suspend fun enabledWindowIds(state: CurrentQuotaState): Set<QuotaWindowId>
}

object PrimaryQuotaAlertWindowPreferenceReader : QuotaAlertWindowPreferenceReader {
    override suspend fun enabledWindowIds(state: CurrentQuotaState): Set<QuotaWindowId> =
        state.primaryWindow?.windowId?.let(::setOf).orEmpty()
}

class NotificationPreferenceQuotaAlertWindowReader(
    private val notificationPreferenceReader: NotificationPreferenceReader,
) : QuotaAlertWindowPreferenceReader {
    override suspend fun enabledWindowIds(state: CurrentQuotaState): Set<QuotaWindowId> {
        val account = state.account ?: return emptySet()
        val preferences = notificationPreferenceReader.notificationPreferences()
        return state.snapshot?.windows.orEmpty()
            .map { it.windowId }
            .filter { windowId ->
                preferences.isQuotaAlertEnabled(
                    providerId = account.providerId,
                    localAccountId = account.localAccountId,
                    windowId = windowId,
                )
            }
            .toSet()
    }
}

fun interface StatusNotificationStatesLoader {
    suspend fun loadStatusNotificationStates(refreshedState: CurrentQuotaState): List<CurrentQuotaState>
}

object PassthroughStatusNotificationStatesLoader : StatusNotificationStatesLoader {
    override suspend fun loadStatusNotificationStates(
        refreshedState: CurrentQuotaState,
    ): List<CurrentQuotaState> = listOf(refreshedState)
}

fun interface AccountErrorEventReader {
    suspend fun accountErrorEvent(state: CurrentQuotaState): AccountErrorNotificationEvent?
}

object NoopAccountErrorEventReader : AccountErrorEventReader {
    override suspend fun accountErrorEvent(state: CurrentQuotaState): AccountErrorNotificationEvent? = null
}

private object NoopCurrencyPreferenceReader : CurrencyPreferenceReader {
    override suspend fun currencyPreferences(): CurrencyPreferences = CurrencyPreferences()
}

private object NoopExchangeRateReader : ExchangeRateReader {
    override suspend fun currentRates(): ExchangeRates? = null
}
