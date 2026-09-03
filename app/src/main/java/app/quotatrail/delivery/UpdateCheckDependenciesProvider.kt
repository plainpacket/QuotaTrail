package app.quotatrail.delivery

import app.quotatrail.domain.update.AppUpdateCheckUseCase
import app.quotatrail.domain.update.AppUpdateNotifier
import app.quotatrail.domain.update.UpdatePreferenceStore

/** Implemented by the Application so [UpdateCheckWorker] can reach its dependencies. */
interface UpdateCheckDependenciesProvider {
    val appUpdateCheck: AppUpdateCheckUseCase
    val updatePreferenceStore: UpdatePreferenceStore
    val appUpdateNotifier: AppUpdateNotifier
    val currentVersionName: String
}
