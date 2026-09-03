package app.quotatrail.surfaces.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.quotatrail.R
import app.quotatrail.sync.SyncWorkScheduler

/** Queues a durable, connected-network refresh of every manually refreshable account. */
class StatusNotificationRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_ALL_QUOTA) return
        SyncWorkScheduler.from(context.applicationContext).scheduleImmediateRefresh()
        Toast.makeText(
            context,
            context.getString(R.string.notification_status_refresh_queued),
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        const val ACTION_REFRESH_ALL_QUOTA = "app.quotatrail.action.REFRESH_ALL_QUOTA"
    }
}
