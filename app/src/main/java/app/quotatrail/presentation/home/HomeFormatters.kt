package app.quotatrail.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import app.quotatrail.presentation.theme.QuotaTrailTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun formattedInstant(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(instant, locale) {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}

@Composable
@ReadOnlyComposable
internal fun toneColor(tone: HomeStatusTone): Color =
    when (tone) {
        HomeStatusTone.Neutral -> QuotaTrailTheme.colors.tertiary
        HomeStatusTone.Success -> QuotaTrailTheme.colors.success
        HomeStatusTone.Warning -> QuotaTrailTheme.colors.warning
        HomeStatusTone.Danger -> QuotaTrailTheme.colors.danger
    }
