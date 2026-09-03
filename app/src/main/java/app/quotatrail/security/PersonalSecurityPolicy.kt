package app.quotatrail.security

/**
 * Immutable security boundary for the private Claude + Codex build.
 *
 * Keeping this policy in production code makes least privilege reviewable and testable. The app
 * deliberately does not self-update: a new APK must be built and signed with the same private key.
 */
object PersonalSecurityPolicy {
    val ENABLED_PROVIDER_IDS: Set<String> = setOf("claude", "codex")

    // GET /api/oauth/usage requires user:profile. Inference and API-key creation are unnecessary.
    const val CLAUDE_OAUTH_SCOPE: String = "user:profile"

    const val UPSTREAM_UPDATES_ENABLED: Boolean = false

    val API_HOST_ALLOWLIST: Set<String> = setOf(
        "auth.openai.com",
        "chatgpt.com",
        "platform.claude.com",
        "api.anthropic.com",
    )
}
