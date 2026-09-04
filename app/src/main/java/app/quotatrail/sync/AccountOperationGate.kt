package app.quotatrail.sync

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by refresh and deletion; locks remain stable for the lifetime of the coordinator. */
internal class AccountOperationGate {
    private val registryMutex = Mutex()
    private val locks = mutableMapOf<Pair<ProviderId, LocalAccountId>, Mutex>()

    suspend fun <T> withAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        action: suspend () -> T,
    ): T {
        val lock = registryMutex.withLock { locks.getOrPut(providerId to localAccountId) { Mutex() } }
        return lock.withLock { action() }
    }
}
