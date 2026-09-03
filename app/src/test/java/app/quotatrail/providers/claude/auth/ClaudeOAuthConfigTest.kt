package app.quotatrail.providers.claude.auth

import app.quotatrail.security.PersonalSecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeOAuthConfigTest {
    @Test
    fun `uses current Claude Code authorization callback and token endpoints`() {
        assertEquals("https://claude.com/cai/oauth/authorize", ClaudeOAuthConfig.AUTHORIZATION_ENDPOINT)
        assertEquals("https://platform.claude.com/oauth/code/callback", ClaudeOAuthConfig.REDIRECT_URI)
        assertEquals("https://platform.claude.com/v1/oauth/token", ClaudeOAuthConfig.TOKEN_ENDPOINT)
        assertEquals("claude-cli/2.1.0 (external, cli)", ClaudeOAuthConfig.USER_AGENT)
    }

    @Test
    fun `authorization URL preserves least privilege usage scope`() {
        val url = ClaudeOAuthConfig.authorizationUrl(
            codeChallenge = "challenge",
            state = "state",
        )

        assertTrue(url.startsWith(ClaudeOAuthConfig.AUTHORIZATION_ENDPOINT))
        assertTrue(url.contains("scope=user%3Aprofile"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback"))
        assertEquals("user:profile", PersonalSecurityPolicy.CLAUDE_OAUTH_SCOPE)
    }
}
