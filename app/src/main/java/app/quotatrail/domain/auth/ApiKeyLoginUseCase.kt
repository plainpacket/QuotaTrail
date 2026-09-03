package app.quotatrail.domain.auth

import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.preferences.CurrentAccountPreferences
import app.quotatrail.storage.preferences.CurrentAccountSelection
import app.quotatrail.storage.repository.toEntity
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.providers.SessionImportRouter
import java.time.Clock
import java.util.UUID

class ApiKeyLoginUseCase(
    private val importRouter: SessionImportRouter,
    private val accountDao: ProviderAccountDao,
    private val snapshotDao: QuotaSnapshotDao,
    private val currentAccountStore: CurrentAccountPreferences,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun importApiKey(
        providerId: ProviderId,
        displayName: String,
        apiKey: String,
    ): Result<ProviderAccount> {
        val now = clock.instant()
        val localAccountId = LocalAccountId("${providerId.value}-${UUID.randomUUID()}")
        val account = ProviderAccount.createNew(
            localAccountId = localAccountId,
            providerId = providerId,
            providerAccountId = null,
            displayName = displayName,
            now = now,
        )

        val snapshotResult = importRouter.importApiKey(apiKey, account)
        if (snapshotResult.isFailure) {
            return Result.failure(
                snapshotResult.exceptionOrNull()
                    ?: RuntimeException("Unknown import error"),
            )
        }
        val snapshot = snapshotResult.getOrThrow()

        accountDao.upsert(account.toEntity())
        snapshotDao.insert(snapshot.toEntity())
        currentAccountStore.updateCurrentAccountSelection(
            CurrentAccountSelection(
                providerId = providerId,
                localAccountId = localAccountId,
            ),
        )

        return Result.success(account)
    }
}
