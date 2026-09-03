package app.quotatrail.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.avatarColor
import app.quotatrail.presentation.theme.avatarInitialColor

@Composable
internal fun AccountLabel(account: HomeAccountUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuotaTrailShapes.lg)
            .background(QuotaTrailTheme.colors.surface)
            .padding(TrailSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
    ) {
        val iconRes = account.providerIconResId
        if (iconRes != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(QuotaTrailTheme.colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = QuotaTrailTheme.colors.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(avatarColor(account.avatarColorKey)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = account.avatarInitial,
                    style = MaterialTheme.typography.labelLarge,
                    color = avatarInitialColor(),
                    maxLines = 1,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.home_current_account),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
