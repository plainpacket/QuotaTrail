package app.quotatrail.surfaces.widget

import android.content.Context
import app.quotatrail.domain.model.LocalAccountId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetRefreshReceiverTest {
    @Test
    fun `receiver handler acknowledges tap before dispatching selected account`() = runTest {
        val events = mutableListOf<WidgetRefreshFeedback>()
        val accounts = mutableListOf<LocalAccountId>()
        val handler = WidgetRefreshReceiverHandler(
            dispatcher = WidgetRefreshDispatcher { _, localAccountIds -> accounts += localAccountIds },
            feedbackPresenter = WidgetRefreshFeedbackPresenter { _, event -> events += event },
        )

        handler.handle(
            RuntimeEnvironment.getApplication(),
            listOf(LocalAccountId("claude-local"), LocalAccountId("codex-local")),
        )

        assertEquals(listOf(WidgetRefreshFeedback.Queued), events)
        assertEquals(listOf(LocalAccountId("claude-local"), LocalAccountId("codex-local")), accounts)
    }

    @Test
    fun `receiver handler reports queue failure instead of staying silent`() = runTest {
        val events = mutableListOf<WidgetRefreshFeedback>()
        val handler = WidgetRefreshReceiverHandler(
            dispatcher = WidgetRefreshDispatcher { _, _ -> error("queue unavailable") },
            feedbackPresenter = WidgetRefreshFeedbackPresenter { _, event -> events += event },
        )

        handler.handle(RuntimeEnvironment.getApplication(), listOf(LocalAccountId("codex-local")))

        assertEquals(
            listOf(WidgetRefreshFeedback.Queued, WidgetRefreshFeedback.Failed),
            events,
        )
    }

    @Test
    fun `widget button is wired to explicit non-exported receiver`() {
        val widgetSource = sourceFile("src/main/java/app/quotatrail/surfaces/widget/QuotaTrailWidget.kt").readText()
        val receiverSource = sourceFile("src/main/java/app/quotatrail/surfaces/widget/WidgetRefreshReceiver.kt").readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(widgetSource.contains("actionSendBroadcast"))
        assertTrue(widgetSource.contains("WidgetRefreshReceiver.refreshIntent"))
        assertTrue(receiverSource.contains("Intent(context, WidgetRefreshReceiver::class.java)"))
        assertFalse(widgetSource.contains("actionRunCallback"))
        assertTrue(manifest.contains(".surfaces.widget.WidgetRefreshReceiver"))
        assertTrue(
            Regex("WidgetRefreshReceiver[\\s\\S]*?android:exported=\\\"false\\\"").containsMatchIn(manifest),
        )
        assertTrue(manifest.contains(".surfaces.widget.WidgetPackageUpdateReceiver"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        return File("app", relativePath)
    }
}
