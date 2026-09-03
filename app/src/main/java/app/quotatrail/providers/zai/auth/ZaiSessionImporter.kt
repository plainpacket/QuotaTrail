package app.quotatrail.providers.zai.auth

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.providers.SessionImporter
import app.quotatrail.providers.zai.mapper.ZaiQuotaMapper
import app.quotatrail.providers.zai.network.ZaiQuotaClient
import app.quotatrail.providers.zai.session.ZaiSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class ZaiSessionImporter(
    private val client: ZaiQuotaClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> {
        val baseUrl = apiBaseUrl ?: ZaiQuotaClient.DEFAULT_BASE_URL
        return when (val result = client.fetchQuota(apiKey, baseUrl)) {
            is ZaiQuotaClient.Result.Failure ->
                Result.failure(
                    RuntimeException(
                        "Zai API key import failed: ${result.error.diagnosticsDigest}",
                    ),
                )
            is ZaiQuotaClient.Result.Success -> {
                val now = clock.instant()
                val nowStr = now.toString()

                // Persist the chosen region base URL so later refreshes hit the same platform.
                val payload = ZaiSessionPayload(apiKey = apiKey, apiBaseUrl = baseUrl)
                val jsonBytes = json.encodeToString(ZaiSessionPayload.serializer(), payload)
                    .encodeToByteArray()

                // Create ProviderSessionEnvelope
                val encrypted = payloadCipher.encrypt(jsonBytes)
                val envelope = ProviderSessionEnvelope(
                    providerId = account.providerId.value,
                    localAccountId = account.localAccountId.value,
                    providerAccountId = account.providerAccountId?.value,
                    schemaVersion = 1,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = nowStr,
                    updatedAt = nowStr,
                )

                // Save to session store
                sessionStore.save(envelope)

                // Map response via ZaiQuotaMapper with source=ApiKeyImport
                val snapshot = ZaiQuotaMapper.map(
                    dto = result.dto,
                    localAccountId = account.localAccountId,
                    providerAccountId = account.providerAccountId,
                    fetchedAt = now,
                    source = QuotaSnapshotSource.ApiKeyImport,
                )

                Result.success(snapshot)
            }
        }
    }

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Zai does not support cookie auth"))

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Zai does not support OAuth PKCE auth"))
}
