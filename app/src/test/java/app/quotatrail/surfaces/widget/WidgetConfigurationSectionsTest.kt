package app.quotatrail.surfaces.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetConfigurationSectionsTest {
    private val emptySlots = List(WIDGET_MAX_FIELDS) { WidgetSlotConfiguration("", "", "") }

    @Test
    fun `each slot keeps its own account and limit selection`() {
        val claude = WidgetSlotConfiguration("claude", "claude-1", "claude_7d_window")
        val codex = WidgetSlotConfiguration("codex", "codex-1", "five_hour")

        val state = WidgetConfigurationScreenState(slots = emptySlots)
            .replaceSlot(1, claude)
            .replaceSlot(3, codex)

        assertNull(state.slot(0))
        assertEquals(claude, state.slot(1))
        assertNull(state.slot(2))
        assertEquals(codex, state.slot(3))
    }

    @Test
    fun `clearing one slot does not change the other slots`() {
        val claude = WidgetSlotConfiguration("claude", "claude-1", "claude_5h_window")
        val codex = WidgetSlotConfiguration("codex", "codex-1", "weekly")
        val state = WidgetConfigurationScreenState(slots = listOf(claude, codex) + emptySlots.take(2))

        val cleared = state.replaceSlot(0, null)

        assertNull(cleared.slot(0))
        assertEquals(codex, cleared.slot(1))
    }

    @Test
    fun `refresh account ids are de duplicated across slots`() {
        val configuration = WidgetQuotaConfiguration(
            listOf(
                WidgetSlotConfiguration("claude", "claude-1", "claude_5h_window"),
                WidgetSlotConfiguration("claude", "claude-1", "claude_7d_window"),
                WidgetSlotConfiguration("codex", "codex-1", "weekly"),
            ),
        )

        assertEquals(listOf("claude-1", "codex-1"), configuration.refreshAccountIds)
    }
}
