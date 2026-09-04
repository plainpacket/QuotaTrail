package app.quotatrail.sync

import app.quotatrail.surfaces.widget.WidgetRefreshFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RefreshFeedbackTest {
    @Test
    fun `worker acknowledges actual start and each terminal outcome`() = runTest {
        for (outcome in UsageSyncWorkerOutcome.entries) {
            val events = mutableListOf<WidgetRefreshFeedback>()
            val actual = runRefreshWithFeedback(true, { events += it }) {
                assertEquals(listOf(WidgetRefreshFeedback.Refreshing), events)
                outcome
            }
            assertEquals(outcome, actual)
            assertEquals(listOf(WidgetRefreshFeedback.Refreshing, outcome.widgetFeedback()), events)
        }
    }

    @Test
    fun `unexpected storage failure reports retry instead of silence`() = runTest {
        val events = mutableListOf<WidgetRefreshFeedback>()
        val outcome = runRefreshWithFeedback(true, { events += it }) { error("synthetic storage error") }
        assertEquals(UsageSyncWorkerOutcome.Retry, outcome)
        assertEquals(listOf(WidgetRefreshFeedback.Refreshing, WidgetRefreshFeedback.Retrying), events)
    }

    @Test
    fun `periodic refresh is silent`() = runTest {
        val events = mutableListOf<WidgetRefreshFeedback>()
        runRefreshWithFeedback(false, { events += it }) { UsageSyncWorkerOutcome.Success }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `cancellation is propagated without claiming success`() = runTest {
        val events = mutableListOf<WidgetRefreshFeedback>()
        val result = runCatching {
            runRefreshWithFeedback(true, { events += it }) { throw CancellationException("cancelled") }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(listOf(WidgetRefreshFeedback.Refreshing), events)
    }
}
