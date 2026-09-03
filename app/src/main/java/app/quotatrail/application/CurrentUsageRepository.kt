package app.quotatrail.application

import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.local.dao.RefreshAttemptDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.currency.CurrencyPreferences
import app.quotatrail.domain.currency.ExchangeRates
import app.quotatrail.domain.currency.withConvertedBalance
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.AccountStatus
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStateFactory
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.settings.DefaultPrimaryQuotaWindowPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferenceReader
import app.quotatrail.domain.settings.NotificationPreferences
import app.quotatrail.domain.settings.PrimaryQuotaWindowPreferenceReader
import app.quotatrail.surfaces.notification.StatusNotificationStatesLoader
import app.quotatrail.presentation.home.HomeCurrentQuotaStateLoader
import app.quotatrail.presentation.home.HomeAccountQuotaStates
import app.quotatrail.presentation.home.HomeAccountQuotaStatesLoader
import app.quotatrail.providers.ProviderRegistry
import java.time.Clock

internal class CurrentUsageRepository(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val primaryQuotaWindowPreferenceReader: PrimaryQuotaWindowPreferenceReader =
        DefaultPrimaryQuotaWindowPreferenceReader,
    private val notificationPreferenceReader: NotificationPreferenceReader =
        DefaultStatusNotificationPreferenceReader,
    private val clock: Clock,
    private val currencyPreferenceReader: CurrencyPreferenceReader = DefaultCurrencyPreferenceReader,
    private val exchangeRateReader: ExchangeRateReader = DefaultExchangeRateReader,
) : HomeCurrentQuotaStateLoader, HomeAccountQuotaStatesLoader, StatusNotificationStatesLoader {
    override suspend fun loadCurrentState(): CurrentQuotaState {
        val selection = currentAccountReader.currentAccountSelection()
            ?: return unauthenticatedState()
        val account = loadAccount(selection)
            ?: return unauthenticatedState()
        return loadState(
            account = account,
            primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId(),
        )
    }

    override suspend fun loadAccountStates(): HomeAccountQuotaStates {
        val enabledProviderIds = ProviderRegistry.all.mapTo(mutableSetOf()) { it.providerId }
        val accounts = providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.status != AccountStatus.Deleted && it.providerId in enabledProviderIds }
        val primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId()
        val states = accounts.map { account ->
            loadState(account = account, primaryWindowId = primaryWindowId)
        }
        val persistedSelection = currentAccountReader.currentAccountSelection()
        val selectedAccountId = persistedSelection
            ?.takeIf { selection ->
                states.any { state ->
                    state.account?.providerId == selection.providerId &&
                        state.account.localAccountId == selection.localAccountId
                }
            }
            ?.localAccountId
            ?: states.firstOrNull()?.account?.localAccountId

        return HomeAccountQuotaStates(
            selectedAccountId = selectedAccountId,
            states = states,
        )
    }

    override suspend fun loadStatusNotificationStates(
        refreshedState: CurrentQuotaState,
    ): List<CurrentQuotaState> {
        val preferences = notificationPreferenceReader.notificationPreferences()
        val configuredAccount = preferences.persistentNotificationAccount
            ?.let { selection ->
                loadAccount(
                    CurrentAccountSelection(
                        providerId = selection.providerId,
                        localAccountId = selection.localAccountId,
                    ),
                )
            }
        val accounts = configuredAccount?.let(::listOf) ?: loadDefaultNotificationAccounts()
        if (accounts.isEmpty()) return listOf(unauthenticatedState())

        val currency = currencyPreferenceReader.currencyPreferences()
        val rates = exchangeRateReader.currentRates()
        return accounts.map { account ->
            val state = loadState(
                account = account,
                primaryWindowId = preferences.persistentNotificationWindowId,
            )
            val resolvedPrimary = resolveNotificationPrimaryWindow(state)
            state.copy(
                primaryWindow = resolvedPrimary?.withConvertedBalance(currency.targetCurrency, rates),
            )
        }
    }

    private suspend fun loadDefaultNotificationAccounts(): List<ProviderAccount> {
        val accounts = providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.status != AccountStatus.Deleted }
        val currentSelection = currentAccountReader.currentAccountSelection()
        return ProviderRegistry.all.mapNotNull { provider ->
            val providerAccounts = accounts.filter { it.providerId == provider.providerId }
            providerAccounts.firstOrNull { account ->
                currentSelection?.providerId == account.providerId &&
                    currentSelection.localAccountId == account.localAccountId
            } ?: providerAccounts.firstOrNull()
        }
    }

    suspend fun loadAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        primaryWindowId: QuotaWindowId,
    ): CurrentQuotaState {
        val account = loadAccount(
            CurrentAccountSelection(
                providerId = providerId,
                localAccountId = localAccountId,
            ),
        ) ?: return unauthenticatedState()
        return loadState(
            account = account,
            primaryWindowId = primaryWindowId,
        )
    }

    private suspend fun loadAccount(selection: CurrentAccountSelection): ProviderAccount? =
        providerAccountDao.getById(selection.localAccountId.value)
            ?.toDomain()
            ?.takeIf { it.providerId == selection.providerId }

    private suspend fun loadState(
        account: ProviderAccount,
        primaryWindowId: QuotaWindowId,
    ): CurrentQuotaState {
        val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()
        val latestAttempt = refreshAttemptDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()

        return currentQuotaStateFactory.create(
            account = account,
            latestSnapshot = latestSnapshot,
            latestAttempt = latestAttempt,
            now = clock.instant(),
            primaryWindowId = primaryWindowId,
        )
    }

    private fun unauthenticatedState(): CurrentQuotaState =
        currentQuotaStateFactory.create(
            account = null,
            latestSnapshot = null,
            latestAttempt = null,
            now = clock.instant(),
        )
}

/**
 * Resolves the primary window for the persistent status notification.
 *
 * When the stored persistentNotificationWindowId belongs to a previous account and is absent from
 * the current account's snapshot, [CurrentQuotaState.primaryWindow] is null. Rather than showing
 * no quota in the notification, fall back to the account's own primary-candidate window, or else
 * the first window in the snapshot.
 */
internal fun resolveNotificationPrimaryWindow(state: CurrentQuotaState): QuotaWindow? =
    state.primaryWindow
        ?: state.snapshot?.windows?.firstOrNull { it.isPrimaryCandidate }
        ?: state.snapshot?.windows?.firstOrNull()

private object DefaultStatusNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

private object DefaultCurrencyPreferenceReader : CurrencyPreferenceReader {
    override suspend fun currencyPreferences() = CurrencyPreferences()
}

private object DefaultExchangeRateReader : ExchangeRateReader {
    override suspend fun currentRates(): ExchangeRates? = null
}
