package app.quotatrail.providers

import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshot

class SessionImportRouter(
    private val importers: Map<ProviderId, SessionImporter>,
) {
    suspend fun importApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String? = null,
    ): Result<QuotaSnapshot> =
        resolve(account.providerId).importFromApiKey(apiKey, account, apiBaseUrl)

    suspend fun importCookie(cookieJson: String, account: ProviderAccount): Result<QuotaSnapshot> =
        resolve(account.providerId).importFromCookie(cookieJson, account)

    suspend fun importOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        resolve(account.providerId).importFromOAuthPkce(code, verifier, redirectUri, account)

    private fun resolve(providerId: ProviderId): SessionImporter =
        importers[providerId] ?: throw NoSuchElementException("No SessionImporter for $providerId")
}
