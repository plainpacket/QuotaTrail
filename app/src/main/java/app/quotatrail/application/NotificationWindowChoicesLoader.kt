package app.quotatrail.application

import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.preferences.CurrentAccountReader
import app.quotatrail.storage.repository.toDomain
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.QuotaWindowId

/** A selectable notification window identified by its stable windowId. */
data class NotificationWindowChoice(
    val windowId: QuotaWindowId,
)

fun interface NotificationWindowChoicesLoader {
    /** Latest snapshot windows for [providerId]/[localAccountId]; null args -> current account. */
    suspend fun windowChoices(providerId: ProviderId?, localAccountId: LocalAccountId?): List<NotificationWindowChoice>
}

class DefaultNotificationWindowChoicesLoader(
    private val currentAccountReader: CurrentAccountReader,
    private val quotaSnapshotDao: QuotaSnapshotDao,
) : NotificationWindowChoicesLoader {
    override suspend fun windowChoices(
        providerId: ProviderId?,
        localAccountId: LocalAccountId?,
    ): List<NotificationWindowChoice> {
        val resolvedProvider: ProviderId
        val resolvedLocal: LocalAccountId
        if (providerId != null && localAccountId != null) {
            resolvedProvider = providerId
            resolvedLocal = localAccountId
        } else {
            val selection = currentAccountReader.currentAccountSelection() ?: return emptyList()
            resolvedProvider = selection.providerId
            resolvedLocal = selection.localAccountId
        }
        val snapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = resolvedProvider.value,
            localAccountId = resolvedLocal.value,
        )?.toDomain() ?: return emptyList()
        return snapshot.windows.map { NotificationWindowChoice(it.windowId) }
    }
}
