package app.quotatrail.providers.claude.auth

import app.quotatrail.security.PersonalSecurityPolicy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Current Claude Code OAuth endpoints and least-privilege authorization request. */
object ClaudeOAuthConfig {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val AUTHORIZATION_ENDPOINT = "https://claude.com/cai/oauth/authorize"
    const val REDIRECT_URI = "https://platform.claude.com/oauth/code/callback"
    const val TOKEN_ENDPOINT = "https://platform.claude.com/v1/oauth/token"
    const val USER_AGENT = "claude-cli/2.1.0 (external, cli)"

    fun authorizationUrl(codeChallenge: String, state: String): String =
        "$AUTHORIZATION_ENDPOINT?" + listOf(
            "code" to "true",
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to PersonalSecurityPolicy.CLAUDE_OAUTH_SCOPE,
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
        ).joinToString("&") { (key, value) -> "$key=${value.urlEncoded()}" }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
