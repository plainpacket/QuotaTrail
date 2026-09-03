package app.quotatrail.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.quotatrail.presentation.theme.QuotaTrailTheme

/**
 * Compact Material 3 pull-to-refresh indicator for the Field Instrument theme.
 *
 * Delegates drop-down positioning and entrance animation to [PullToRefreshDefaults.IndicatorBox],
 * which moves the disc into view as the user pulls and animates it back when refreshing completes.
 * The disc scales/fades in proportion to [PullToRefreshState.distanceFraction] while pulling,
 * giving tactile feedback before the threshold is reached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaPullToRefreshIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
) {
    // IndicatorBox handles the vertical translation during pull and the enter/exit animation so
    // the disc descends from above the screen, matching standard M3 indicator behaviour.
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
    ) {
        val fraction = state.distanceFraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    // Scale and alpha track pull progress for responsive feel.
                    val scale = 0.6f + 0.4f * fraction
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.3f + 0.7f * fraction
                }
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = QuotaTrailTheme.colors.shadow.copy(alpha = 0.22f),
                    spotColor = QuotaTrailTheme.colors.shadow.copy(alpha = 0.30f),
                )
                .background(QuotaTrailTheme.colors.surfaceRaised, CircleShape)
                .border(1.dp, QuotaTrailTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = QuotaTrailTheme.colors.signalBlue,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
