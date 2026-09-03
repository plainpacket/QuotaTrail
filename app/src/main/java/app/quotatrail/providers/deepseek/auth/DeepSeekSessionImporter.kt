package app.quotatrail.providers.deepseek.auth

import app.quotatrail.storage.secure.PayloadCipher
import app.quotatrail.storage.secure.ProviderSessionEnvelope
import app.quotatrail.storage.secure.SecureSessionStore
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.providers.SessionImporter
import app.quotatrail.providers.deepseek.mapper.DeepSeekBalanceMapper
import app.quotatrail.providers.deepseek.network.DeepSeekBalanceClient
import app.quotatrail.providers.deepseek.session.DeepSeekSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class DeepSeekSessionImporter(
    private val balanceClient: DeepSeekBalanceClient,
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
        val balanceResult = balanceClient.fetchBalance(apiKey)

        return when (balanceResult) {
            is DeepSeekBalanceClient.Result.Failure ->
                Result.failure(
                    RuntimeException(
                        "DeepSeek balance fetch failed: ${balanceResult.error.safeMessageKey}",
                    ),
                )

            is DeepSeekBalanceClient.Result.Success -> {
                val now = clock.instant()
                val payload = DeepSeekSessionPayload(apiKey)
                val jsonBytes = json.encodeToString(
                    DeepSeekSessionPayload.serializer(),
                    payload,
                ).encodeToByteArray()

                val encrypted = payloadCipher.encrypt(jsonBytes)
                val envelope = ProviderSessionEnvelope(
                    providerId = DEEPSEEK_PROVIDER_ID.value,
                    localAccountId = account.localAccountId.value,
                    providerAccountId = account.providerAccountId?.value,
                    schemaVersion = 1,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                )

                sessionStore.save(envelope)

                val snapshot = DeepSeekBalanceMapper.map(
                    dto = balanceResult.dto,
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
        Result.failure(
            UnsupportedOperationException("DeepSeek does not support cookie auth"),
        )

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(
            UnsupportedOperationException("DeepSeek does not support OAuth PKCE auth"),
        )

    private companion object {
        val DEEPSEEK_PROVIDER_ID = ProviderId("deepseek")
    }
}
