package app.quotatrail.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.providers.ProviderAuthKind
import app.quotatrail.providers.ProviderConfig
import app.quotatrail.providers.ProviderRegistry
import app.quotatrail.presentation.components.QuotaTrailBackdrop
import app.quotatrail.presentation.theme.QuotaTrailTheme
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.TrailSpacing
import app.quotatrail.presentation.theme.QuotaTrailTypography

/**
 * Lists every registered provider and lets the user pick one to add an account. On selection the
 * caller branches on the provider's auth type. Styled per DESIGN.md (Air Glass backdrop, restrained
 * surface cards, brand-icon tiles) and fully resource-backed for zh-rCN + en.
 */
@Composable
fun ProviderSelectionScreen(
    onProviderSelected: (ProviderId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val providers = ProviderRegistry.all

    Box(modifier = modifier.fillMaxSize()) {
        QuotaTrailBackdrop(modifier = Modifier.fillMaxSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = TrailSpacing.xl,
                end = TrailSpacing.xl,
                top = TrailSpacing.xxl,
                bottom = TrailSpacing.bottomNavigationClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
        ) {
            item {
                ProviderSelectionHeader(onBack = onBack)
                Spacer(modifier = Modifier.height(TrailSpacing.sm))
            }
            items(providers, key = { it.providerId.value }) { provider ->
                ProviderListItem(
                    provider = provider,
                    onClick = { onProviderSelected(provider.providerId) },
                )
            }
        }
    }
}

@Composable
private fun ProviderSelectionHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.add_provider_title),
                style = QuotaTrailTypography.current.display,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.add_provider_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.auth_back))
        }
    }
}

@Composable
internal fun ProviderListItem(
    provider: ProviderConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuotaTrailShapes.lg)
            .background(
                Brush.linearGradient(
                    listOf(
                        QuotaTrailTheme.colors.surface,
                        QuotaTrailTheme.colors.surfaceSoft,
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(TrailSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(QuotaTrailTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(provider.iconResId),
                contentDescription = null,
                tint = QuotaTrailTheme.colors.primary,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = authTypeLabel(provider.authKind),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = QuotaTrailTheme.colors.tertiary,
            modifier = Modifier
                .size(20.dp)
                .rotate(-90f),
        )
    }
}

@Composable
private fun authTypeLabel(authKind: ProviderAuthKind): String = stringResource(
    when (authKind) {
        ProviderAuthKind.OAuthWebView -> R.string.auth_method_oauth
        ProviderAuthKind.ApiKeyImport -> R.string.auth_method_api_key
        ProviderAuthKind.CookieAuth -> R.string.auth_method_cookie
        ProviderAuthKind.OAuthPkceLogin -> R.string.auth_method_oauth_pkce
    },
)
