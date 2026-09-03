package app.quotatrail.providers.kimi.auth

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.providers.SessionImporter
import app.quotatrail.providers.kimi.mapper.KimiQuotaMapper
import app.quotatrail.providers.kimi.network.KimiQuotaClient
import app.quotatrail.providers.kimi.session.KimiSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class KimiSessionImporter(
    private val client: KimiQuotaClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> {
        return when (val result = client.fetchQuota(cookieJson)) {
            is KimiQuotaClient.Result.Failure ->
                Result.failure(
                    RuntimeException(
                        "Kimi cookie import failed: ${result.error.diagnosticsDigest}",
                    ),
                )
            is KimiQuotaClient.Result.Success -> {
                val now = clock.instant()
                val nowStr = now.toString()

                // Serialize KimiSessionPayload(cookieValue) to JSON bytes
                val payload = KimiSessionPayload(cookieValue = cookieJson)
                val jsonBytes = json.encodeToString(KimiSessionPayload.serializer(), payload)
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

                // Map response via KimiQuotaMapper with source=CookieAuth
                val snapshot = KimiQuotaMapper.map(
                    dto = result.dto,
                    localAccountId = account.localAccountId,
                    providerAccountId = account.providerAccountId,
                    fetchedAt = now,
                    source = QuotaSnapshotSource.CookieAuth,
                )

                Result.success(snapshot)
            }
        }
    }

    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Kimi does not support API key auth"))

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Kimi does not support OAuth PKCE auth"))
}
