package app.quotatrail.surfaces.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPrivacyTest {
    @Test
    fun `widget title never exposes account alias`() {
        val state = WidgetQuotaState(
            status = WidgetQuotaStatus.Fresh,
            accountName = "private-account-alias",
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "local-1",
            providerIconRes = null,
            tone = WidgetQuotaTone.Success,
            clickTarget = WidgetClickTarget.Home,
            fields = emptyList(),
        )

        assertEquals("Codex", state.privacySafeTitle())
    }
}
