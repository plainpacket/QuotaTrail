package app.quotatrail.presentation.home

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.CurrentQuotaState

data class HomeAccountQuotaStates(
    val selectedAccountId: LocalAccountId?,
    val states: List<CurrentQuotaState>,
)

fun interface HomeAccountQuotaStatesLoader {
    suspend fun loadAccountStates(): HomeAccountQuotaStates
}

fun interface HomeAccountSelectionUseCase {
    suspend fun selectAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean
}

internal object NoopHomeAccountSelectionUseCase : HomeAccountSelectionUseCase {
    override suspend fun selectAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean = false
}

data class HomeAccountPageUi(
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
    val providerName: String,
    val content: HomeUiState,
)

data class HomePagerUiState(
    val pages: List<HomeAccountPageUi>,
    val selectedPageIndex: Int,
) {
    companion object {
        val Empty = HomePagerUiState(pages = emptyList(), selectedPageIndex = 0)
    }
}
