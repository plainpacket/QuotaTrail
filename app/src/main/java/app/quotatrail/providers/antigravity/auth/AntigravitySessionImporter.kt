package app.quotatrail.providers.antigravity.auth

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.providers.SessionImporter
import app.quotatrail.providers.antigravity.mapper.AntigravityQuotaMapper
import app.quotatrail.providers.antigravity.network.AntigravityQuotaClient
import app.quotatrail.providers.antigravity.session.AntigravitySessionPayload
import app.quotatrail.providers.common.auth.OAuthTokenClient
import java.time.Clock
import kotlinx.serialization.json.Json

class AntigravitySessionImporter(
    private val tokenClient: OAuthTokenClient,
    private val client: AntigravityQuotaClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> {
        // Google returns an authorization code; exchange it (with client_secret) for tokens first.
        val tokens = when (val result = tokenClient.exchangeAuthorizationCode(code, verifier, redirectUri)) {
            is OAuthTokenClient.Result.Failure ->
                return Result.failure(RuntimeException("Antigravity OAuth token exchange failed: ${result.error}"))
            is OAuthTokenClient.Result.Success -> result.tokens
        }

        return when (val quotaResult = client.fetchQuota(tokens.accessToken)) {
            is AntigravityQuotaClient.Result.Failure ->
                Result.failure(
                    RuntimeException("Antigravity quota fetch failed: ${quotaResult.error.diagnosticsDigest}"),
                )

            is AntigravityQuotaClient.Result.Success -> {
                val now = clock.instant()
                val payload = AntigravitySessionPayload(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    tokenExpiresAtEpochSeconds = tokens.expiresInSeconds?.let { now.epochSecond + it },
                    planTier = quotaResult.dto.tier,
                )
                val jsonBytes = json.encodeToString(AntigravitySessionPayload.serializer(), payload).encodeToByteArray()
                val encrypted = payloadCipher.encrypt(jsonBytes)
                sessionStore.save(
                    ProviderSessionEnvelope(
                        providerId = ANTIGRAVITY_PROVIDER_ID.value,
                        localAccountId = account.localAccountId.value,
                        providerAccountId = account.providerAccountId?.value,
                        schemaVersion = 1,
                        payloadCiphertext = encrypted.ciphertext,
                        payloadNonce = encrypted.nonce,
                        createdAt = now.toString(),
                        updatedAt = now.toString(),
                    ),
                )
                Result.success(
                    AntigravityQuotaMapper.map(
                        dto = quotaResult.dto,
                        localAccountId = account.localAccountId,
                        providerAccountId = account.providerAccountId,
                        fetchedAt = now,
                        source = QuotaSnapshotSource.OAuthPkceLogin,
                    ),
                )
            }
        }
    }

    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Antigravity does not support API key auth"))

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Antigravity does not support cookie auth"))

    private companion object {
        val ANTIGRAVITY_PROVIDER_ID = ProviderId("antigravity")
    }
}
