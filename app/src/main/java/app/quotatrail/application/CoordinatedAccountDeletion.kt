package app.quotatrail.application

import app.quotatrail.domain.account.AccountDeleteUseCase
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.sync.UsageSyncCoordinator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Deletion succeeds only after any earlier refresh has finished writing. */
class CoordinatedAccountDeletion(
    private val coordinator: UsageSyncCoordinator,
    private val delegate: AccountDeleteUseCase,
    private val onDeleted: suspend () -> Unit,
) : AccountDeleteUseCase {
    override suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId) {
        coordinator.withAccountMutation(providerId, localAccountId) {
            // Once deletion starts, navigating away must not leave a partially removed account.
            withContext(NonCancellable) {
                delegate.deleteAccount(providerId, localAccountId)
                // A surface failure must not turn a committed deletion into a UI deletion failure.
                runCatching { onDeleted() }
            }
        }
    }
}
