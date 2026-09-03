package app.quotatrail.surfaces.widget

import app.quotatrail.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetQuotaStatusLabelTest {
    @Test
    fun `fresh widget label follows header tone`() {
        assertEquals(
            R.string.home_quota_status_normal,
            freshState(tone = WidgetQuotaTone.Success).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_caution,
            freshState(tone = WidgetQuotaTone.Warning).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_warning,
            freshState(tone = WidgetQuotaTone.Danger).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_unavailable,
            freshState(tone = WidgetQuotaTone.Neutral).statusLabelResId(),
        )
    }

    @Test
    fun `non-fresh statuses map to dedicated labels`() {
        assertEquals(R.string.widget_connect_codex, stateWith(WidgetQuotaStatus.NoAccount).statusLabelResId())
        assertEquals(R.string.widget_status_possibly_stale, stateWith(WidgetQuotaStatus.PossiblyStale).statusLabelResId())
        assertEquals(R.string.widget_status_expired, stateWith(WidgetQuotaStatus.Expired).statusLabelResId())
        assertEquals(R.string.widget_status_auth_required, stateWith(WidgetQuotaStatus.AuthRequired).statusLabelResId())
        assertEquals(R.string.widget_status_refresh_failed, stateWith(WidgetQuotaStatus.ErrorWithLastKnownGood).statusLabelResId())
        assertEquals(R.string.widget_status_no_data, stateWith(WidgetQuotaStatus.NoData).statusLabelResId())
    }

    @Test
    fun `compact status rail uses field instrument semantic accents`() {
        assertEquals(0xFF3154D5.toInt(), WidgetQuotaTone.Neutral.statusAccentArgb())
        assertEquals(0xFF1F7A63.toInt(), WidgetQuotaTone.Success.statusAccentArgb())
        assertEquals(0xFFE7852F.toInt(), WidgetQuotaTone.Warning.statusAccentArgb())
        assertEquals(0xFFB54444.toInt(), WidgetQuotaTone.Danger.statusAccentArgb())
    }

    private fun freshState(tone: WidgetQuotaTone): WidgetQuotaState =
        stateWith(status = WidgetQuotaStatus.Fresh, tone = tone)

    private fun stateWith(
        status: WidgetQuotaStatus,
        tone: WidgetQuotaTone = WidgetQuotaTone.Neutral,
    ): WidgetQuotaState =
        WidgetQuotaState(
            status = status,
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "local-1",
            accountName = "Codex Main",
            tone = tone,
            clickTarget = WidgetClickTarget.Home,
        )
}
