package app.quotatrail.domain.settings

import app.quotatrail.domain.model.QuotaWindowId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationWindowIdPersistenceTest {
    @Test
    fun `accepts a non-supported window id without throwing`() {
        val prefs = NotificationPreferences(persistentNotificationWindowId = QuotaWindowId("balance"))
        assertEquals(QuotaWindowId("balance"), prefs.persistentNotificationWindowId)
    }
}
