package app.quotatrail.surfaces.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetQuotaStatePreferencesTest {
    @Test
    fun `state round trips fields in order`() {
        val state = WidgetQuotaState(
            status = WidgetQuotaStatus.Fresh,
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "acc-1",
            accountName = "个人号",
            tone = WidgetQuotaTone.Success,
            clickTarget = WidgetClickTarget.Home,
            fields = listOf(
                WidgetField("five_hour", false, 87, null, null, Instant.parse("2026-06-02T14:30:00Z"), WidgetQuotaTone.Success),
                WidgetField("balance", true, null, "8.50", "USD", null, WidgetQuotaTone.Neutral),
            ),
            lastUpdatedAt = Instant.parse("2026-06-02T10:00:00Z"),
            isUnconfigured = false,
            hasAccounts = true,
            providerIconRes = null,
        )
        val prefs = mutablePreferencesOf().apply { writeWidgetQuotaState(state) }
        val restored = prefs.toWidgetQuotaState()

        assertEquals(2, restored.fields.size)
        assertEquals("five_hour", restored.fields[0].windowId)
        assertEquals(87, restored.fields[0].percent)
        assertEquals(Instant.parse("2026-06-02T14:30:00Z"), restored.fields[0].resetAt)
        assertTrue(restored.fields[1].isBalance)
        assertEquals("8.50", restored.fields[1].balanceAmount)
        assertEquals("USD", restored.fields[1].balanceCurrency)
        assertEquals(Instant.parse("2026-06-02T10:00:00Z"), restored.lastUpdatedAt)
    }

    @Test
    fun `unconfigured flag round trips`() {
        val state = WidgetQuotaState(
            status = WidgetQuotaStatus.NoAccount,
            providerName = "QuotaTrail",
            providerId = null,
            localAccountId = null,
            accountName = null,
            tone = WidgetQuotaTone.Neutral,
            clickTarget = WidgetClickTarget.Home,
            fields = emptyList(),
            isUnconfigured = true,
            hasAccounts = false,
        )
        val prefs = mutablePreferencesOf().apply { writeWidgetQuotaState(state) }
        val restored = prefs.toWidgetQuotaState()
        assertTrue(restored.isUnconfigured)
        assertFalse(restored.hasAccounts)
        assertTrue(restored.fields.isEmpty())
    }

    @Test
    fun `empty preferences default to the unconfigured guide state`() {
        // A freshly-placed widget has no persisted state. It must render the same unconfigured guide
        // card the app writes once it computes state, not a half-configured data layout.
        val restored = mutablePreferencesOf().toWidgetQuotaState()

        assertTrue(restored.isUnconfigured)
        assertFalse(restored.hasAccounts)
        assertEquals(WidgetQuotaStatus.NoAccount, restored.status)
        assertTrue(restored.fields.isEmpty())
    }

    @Test
    fun `configuration round trips independent slots in order`() {
        val config = WidgetQuotaConfiguration(
            slots = listOf(
                WidgetSlotConfiguration("claude", "claude-1", "claude_5h_window"),
                WidgetSlotConfiguration("codex", "codex-1", "weekly"),
            ),
        )
        val prefs = mutablePreferencesOf().apply { writeWidgetQuotaConfiguration(config) }
        val restored = prefs.toWidgetQuotaConfiguration()
        assertEquals(config.slots, restored.slots)
        assertEquals(listOf("claude-1", "codex-1"), restored.refreshAccountIds)
    }

    @Test
    fun `clearing matching account resets to unconfigured`() {
        val state = WidgetQuotaState(
            status = WidgetQuotaStatus.Fresh,
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "acc-1",
            accountName = "个人号",
            tone = WidgetQuotaTone.Success,
            clickTarget = WidgetClickTarget.Home,
            fields = listOf(
                WidgetField(
                    "five_hour", false, 87, null, null, null, WidgetQuotaTone.Success,
                    providerName = "Codex", providerId = "codex", localAccountId = "acc-1",
                ),
            ),
        )
        val prefs = mutablePreferencesOf().apply {
            writeWidgetQuotaState(state)
            writeWidgetQuotaConfiguration(
                WidgetQuotaConfiguration(listOf(WidgetSlotConfiguration("codex", "acc-1", "five_hour"))),
            )
        }
        val cleared = prefs.clearWidgetQuotaStateIfAccountMatches("codex", "acc-1")
        assertTrue(cleared)
        val restored = prefs.toWidgetQuotaState()
        assertTrue(restored.isUnconfigured)
        assertTrue(restored.fields.isEmpty())
        assertTrue(prefs.toWidgetQuotaConfiguration().slots.isEmpty())
    }
}
