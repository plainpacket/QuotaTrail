package app.quotatrail.presentation.home

import app.quotatrail.domain.model.AccountStatus
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.CurrentQuotaFreshness
import app.quotatrail.domain.quota.CurrentQuotaState
import app.quotatrail.domain.quota.CurrentQuotaStatus
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAccountPagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `home loads every saved account and starts on the persisted account`() = runTest {
        val viewModel = HomeViewModel(
            accountQuotaStatesLoader = HomeAccountQuotaStatesLoader {
                HomeAccountQuotaStates(
                    selectedAccountId = LocalAccountId("codex-local"),
                    states = listOf(
                        state("claude", "claude-local", "Claude"),
                        state("codex", "codex-local", "Codex"),
                    ),
                )
            },
        )

        viewModel.loadCurrentState()
        advanceUntilIdle()

        val pager = viewModel.pagerUiState.value
        assertEquals(listOf("Claude", "Codex"), pager.pages.map { it.providerName })
        assertEquals(1, pager.selectedPageIndex)
        assertEquals("Codex", viewModel.uiState.value.account?.displayName)
    }

    @Test
    fun `swiping home selects the visible account without refreshing it`() = runTest {
        val selected = mutableListOf<Pair<ProviderId, LocalAccountId>>()
        var refreshCalls = 0
        val viewModel = HomeViewModel(
            accountQuotaStatesLoader = HomeAccountQuotaStatesLoader {
                HomeAccountQuotaStates(
                    selectedAccountId = LocalAccountId("codex-local"),
                    states = listOf(
                        state("codex", "codex-local", "Codex"),
                        state("claude", "claude-local", "Claude"),
                    ),
                )
            },
            accountSelectionUseCase = HomeAccountSelectionUseCase { providerId, localAccountId ->
                selected += providerId to localAccountId
                true
            },
            refreshUseCase = HomeRefreshUseCase {
                refreshCalls += 1
                state("claude", "claude-local", "Claude")
            },
        )

        viewModel.loadCurrentState()
        advanceUntilIdle()
        viewModel.selectAccountPage(1)
        advanceUntilIdle()

        assertEquals("Claude", viewModel.uiState.value.account?.displayName)
        assertEquals(listOf(ProviderId("claude") to LocalAccountId("claude-local")), selected)
        assertEquals(0, refreshCalls)
        assertTrue(viewModel.pagerUiState.value.pages.size == 2)
    }

    private fun state(providerId: String, localAccountId: String, displayName: String): CurrentQuotaState =
        CurrentQuotaState(
            status = CurrentQuotaStatus.NoData,
            freshness = CurrentQuotaFreshness.Unknown,
            account = ProviderAccount.createNew(
                localAccountId = LocalAccountId(localAccountId),
                providerId = ProviderId(providerId),
                providerAccountId = ProviderAccountId("acct-$localAccountId"),
                displayName = displayName,
                now = Instant.parse("2026-08-25T00:00:00Z"),
            ).copy(status = AccountStatus.Active),
            snapshot = null,
            latestAttempt = null,
            primaryWindow = null,
            secondaryWindows = emptyList(),
            primaryWindowCanAlert = false,
            error = null,
        )
}
