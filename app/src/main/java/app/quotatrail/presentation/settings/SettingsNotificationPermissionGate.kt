package app.quotatrail.presentation.settings

import android.os.Build

object SettingsNotificationPermissionGate {
    fun shouldRequestPermission(
        sdkInt: Int,
        notificationPermissionGranted: Boolean,
        requestedEnabled: Boolean,
    ): Boolean =
        requestedEnabled &&
            sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted
}
