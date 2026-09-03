package app.quotatrail.providers.cursor.auth

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.providers.SessionImporter
import app.quotatrail.providers.cursor.mapper.CursorUsageMapper
import app.quotatrail.providers.cursor.network.CursorUsageClient
import app.quotatrail.providers.cursor.session.CursorSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class CursorSessionImporter(
    private val usageClient: CursorUsageClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> {
        val usageResult = usageClient.fetchUsage(cookieJson)

        return when (usageResult) {
            is CursorUsageClient.Result.Failure ->
                Result.failure(
                    RuntimeException(
                        "Cursor usage fetch failed: ${usageResult.error.safeMessageKey}",
                    ),
                )

            is CursorUsageClient.Result.Success -> {
                val now = clock.instant()
                val payload = CursorSessionPayload(cookieJson)
                val jsonBytes = json.encodeToString(
                    CursorSessionPayload.serializer(),
                    payload,
                ).encodeToByteArray()

                val encrypted = payloadCipher.encrypt(jsonBytes)
                val envelope = ProviderSessionEnvelope(
                    providerId = CURSOR_PROVIDER_ID.value,
                    localAccountId = account.localAccountId.value,
                    providerAccountId = account.providerAccountId?.value,
                    schemaVersion = 1,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                )

                sessionStore.save(envelope)

                val snapshot = CursorUsageMapper.map(
                    dto = usageResult.dto,
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
        Result.failure(
            UnsupportedOperationException("Cursor does not support API key auth"),
        )

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(
            UnsupportedOperationException("Cursor does not support OAuth PKCE auth"),
        )

    private companion object {
        val CURSOR_PROVIDER_ID = ProviderId("cursor")
    }
}
