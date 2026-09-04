package app.quotatrail.surfaces.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import app.quotatrail.R
import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.sync.SyncWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal enum class WidgetRefreshFeedback(@get:StringRes val messageRes: Int) {
    Queued(R.string.widget_refresh_queued),
    Refreshing(R.string.widget_refresh_started),
    Complete(R.string.widget_refresh_complete),
    Retrying(R.string.widget_refresh_retrying),
    Failed(R.string.widget_refresh_failed),
}

internal fun interface WidgetRefreshFeedbackPresenter {
    fun show(context: Context, feedback: WidgetRefreshFeedback)
}

private object AndroidWidgetRefreshFeedbackPresenter : WidgetRefreshFeedbackPresenter {
    override fun show(context: Context, feedback: WidgetRefreshFeedback) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, feedback.messageRes, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun interface WidgetRefreshDispatcher {
    suspend fun dispatch(context: Context, localAccountIds: List<LocalAccountId>)
}

private object WorkManagerWidgetRefreshDispatcher : WidgetRefreshDispatcher {
    override suspend fun dispatch(context: Context, localAccountIds: List<LocalAccountId>) {
        SyncWorkScheduler.from(context.applicationContext)
            .scheduleImmediateRefreshAndAwait(localAccountIds)
    }
}

internal class WidgetRefreshReceiverHandler(
    private val dispatcher: WidgetRefreshDispatcher = WorkManagerWidgetRefreshDispatcher,
    private val feedbackPresenter: WidgetRefreshFeedbackPresenter = AndroidWidgetRefreshFeedbackPresenter,
) {
    suspend fun handle(context: Context, localAccountIds: List<LocalAccountId>) {
        feedbackPresenter.show(context, WidgetRefreshFeedback.Queued)
        try {
            dispatcher.dispatch(context, localAccountIds)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            feedbackPresenter.show(context, WidgetRefreshFeedback.Failed)
        }
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> enqueueRefresh(context, intent)
            ACTION_FEEDBACK -> showCompletionFeedback(context, intent)
        }
    }

    private fun enqueueRefresh(context: Context, intent: Intent) {
        val localAccountIds = intent.getStringArrayExtra(EXTRA_LOCAL_ACCOUNT_IDS)
            .orEmpty()
            .filter(String::isNotBlank)
            .distinct()
            .map(::LocalAccountId)
        if (localAccountIds.isEmpty()) {
            AndroidWidgetRefreshFeedbackPresenter.show(context, WidgetRefreshFeedback.Failed)
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRefreshReceiverHandler().handle(context.applicationContext, localAccountIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showCompletionFeedback(context: Context, intent: Intent) {
        val feedback = intent.getStringExtra(EXTRA_FEEDBACK)
            ?.let { encoded -> WidgetRefreshFeedback.entries.firstOrNull { it.name == encoded } }
            ?: WidgetRefreshFeedback.Failed
        AndroidWidgetRefreshFeedbackPresenter.show(context, feedback)
    }

    companion object {
        const val ACTION_REFRESH = "app.quotatrail.action.REFRESH_WIDGET"
        const val ACTION_FEEDBACK = "app.quotatrail.action.WIDGET_REFRESH_FEEDBACK"
        const val EXTRA_LOCAL_ACCOUNT_IDS = "local_account_ids"
        private const val EXTRA_FEEDBACK = "feedback"

        internal fun refreshIntent(context: Context, localAccountIds: List<String>): Intent =
            Intent(context, WidgetRefreshReceiver::class.java).apply {
                action = ACTION_REFRESH
                putExtra(EXTRA_LOCAL_ACCOUNT_IDS, localAccountIds.distinct().toTypedArray())
            }

        internal fun feedbackIntent(context: Context, feedback: WidgetRefreshFeedback): Intent =
            Intent(context, WidgetRefreshReceiver::class.java).apply {
                action = ACTION_FEEDBACK
                putExtra(EXTRA_FEEDBACK, feedback.name)
            }
    }
}
