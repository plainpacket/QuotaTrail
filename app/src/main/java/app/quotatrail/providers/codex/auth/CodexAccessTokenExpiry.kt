package app.quotatrail.providers.codex.auth

import app.quotatrail.providers.codex.session.CodexSessionPayload
import java.time.Instant

/** Resolves Codex access-token lifetime without depending on one token-response shape. */
internal object CodexAccessTokenExpiry {
    private const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 60L * 60L
    private const val PROACTIVE_REFRESH_MARGIN_SECONDS = 5L * 60L

    fun fromTokenResponse(
        accessToken: String,
        receivedAt: Instant,
        expiresInSeconds: Long?,
    ): Instant =
        expiresInSeconds
            ?.takeIf { it > 0 }
            ?.let(receivedAt::plusSeconds)
            ?: CodexJwtClaims.expiresAt(accessToken)
            ?: receivedAt.plusSeconds(DEFAULT_ACCESS_TOKEN_TTL_SECONDS)

    fun resolvedFor(session: CodexSessionPayload): Instant? =
        session.accessTokenExpiresAt
            ?: CodexJwtClaims.expiresAt(session.accessToken)
            ?: session.lastRefresh?.plusSeconds(DEFAULT_ACCESS_TOKEN_TTL_SECONDS)

    fun isRefreshDue(session: CodexSessionPayload, now: Instant): Boolean =
        resolvedFor(session)
            ?.let { !it.isAfter(now.plusSeconds(PROACTIVE_REFRESH_MARGIN_SECONDS)) }
            // Legacy/corrupt metadata is refreshed once; the replacement persists an expiry.
            ?: true
}
