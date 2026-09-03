package app.quotatrail.security

import app.quotatrail.providers.ProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PersonalSecurityPolicyTest {
    @Test
    fun `personal build exposes only Claude and Codex`() {
        assertEquals(setOf("claude", "codex"), ProviderRegistry.all.map { it.providerId.value }.toSet())
    }

    @Test
    fun `Claude OAuth uses only the profile scope required by the usage endpoint`() {
        assertEquals("user:profile", PersonalSecurityPolicy.CLAUDE_OAUTH_SCOPE)
    }

    @Test
    fun `background self updates are disabled`() {
        assertFalse(PersonalSecurityPolicy.UPSTREAM_UPDATES_ENABLED)
    }

    @Test
    fun `API traffic is restricted to OpenAI and Anthropic hosts`() {
        assertEquals(
            setOf("auth.openai.com", "chatgpt.com", "platform.claude.com", "api.anthropic.com"),
            PersonalSecurityPolicy.API_HOST_ALLOWLIST,
        )
    }
}
