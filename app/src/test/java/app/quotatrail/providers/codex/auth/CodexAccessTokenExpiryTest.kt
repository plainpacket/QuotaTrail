package app.quotatrail.providers.codex.auth

import app.quotatrail.providers.codex.session.CodexSessionPayload
import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexAccessTokenExpiryTest {
    private val now = Instant.parse("2026-08-24T00:00:00Z")

    @Test
    fun `explicit expires in takes priority over jwt claim`() {
        val token = jwtWithExpiry(now.plusSeconds(7200))

        val resolved = CodexAccessTokenExpiry.fromTokenResponse(token, now, expiresInSeconds = 3600)

        assertEquals(now.plusSeconds(3600), resolved)
    }

    @Test
    fun `jwt expiry is used when token response omits expires in`() {
        val expected = now.plusSeconds(1800)

        val resolved = CodexAccessTokenExpiry.fromTokenResponse(
            accessToken = jwtWithExpiry(expected),
            receivedAt = now,
            expiresInSeconds = null,
        )

        assertEquals(expected, resolved)
    }

    @Test
    fun `legacy opaque session uses last refresh plus conservative lifetime`() {
        val session = session(
            accessToken = "opaque-access-token",
            lastRefresh = now,
            accessTokenExpiresAt = null,
        )

        assertFalse(CodexAccessTokenExpiry.isRefreshDue(session, now.plusSeconds(50 * 60)))
        assertTrue(CodexAccessTokenExpiry.isRefreshDue(session, now.plusSeconds(55 * 60)))
    }

    private fun session(
        accessToken: String,
        lastRefresh: Instant,
        accessTokenExpiresAt: Instant?,
    ) = CodexSessionPayload(
        accessToken = accessToken,
        refreshToken = "refresh-token",
        idToken = null,
        accountId = "acct-1",
        lastRefresh = lastRefresh,
        accessTokenExpiresAt = accessTokenExpiresAt,
    )

    private fun jwtWithExpiry(expiry: Instant): String {
        val header = base64Url("{}")
        val payload = base64Url("""{"exp":${expiry.epochSecond}}""")
        return "$header.$payload.signature"
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
