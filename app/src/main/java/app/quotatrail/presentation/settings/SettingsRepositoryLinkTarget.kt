package app.quotatrail.presentation.settings

import android.content.Intent
import android.net.Uri

internal object SettingsRepositoryLinkTarget {
    private const val REPOSITORY_URL = "https://github.com/plainpacket/QuotaTrail"

    fun openIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))
}
