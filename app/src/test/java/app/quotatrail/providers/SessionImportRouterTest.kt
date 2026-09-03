package app.quotatrail.providers

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccount
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.model.SnapshotId
import app.quotatrail.domain.quota.QuotaSnapshot
import app.quotatrail.domain.quota.QuotaSnapshotSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionImportRouterTest {
    private val testSnapshot = QuotaSnapshot(
        snapshotId = SnapshotId("s1"),
        providerId = ProviderId("deepseek"),
        localAccountId = LocalAccountId("a1"),
        providerAccountId = null,
        fetchedAt = Instant.EPOCH,
        source = QuotaSnapshotSource.ApiKeyImport,
        planType = null,
        windows = emptyList(),
        credits = null,
        responseDigest = null,
    )

    private val deepseekImporter = object : SessionImporter {
        override suspend fun importFromApiKey(
            apiKey: String,
            account: ProviderAccount,
            apiBaseUrl: String?,
        ) = Result.success(testSnapshot)
        override suspend fun importFromCookie(cookieJson: String, account: ProviderAccount) =
            Result.failure<QuotaSnapshot>(UnsupportedOperationException())
        override suspend fun importFromOAuthPkce(
            code: String,
            verifier: String,
            redirectUri: String,
            account: ProviderAccount,
        ) = Result.failure<QuotaSnapshot>(UnsupportedOperationException())
    }

    @Test
    fun routesToCorrectImporter() = runTest {
        val router = SessionImportRouter(mapOf(ProviderId("deepseek") to deepseekImporter))
        val account = ProviderAccount.createNew(
            LocalAccountId("a1"),
            ProviderId("deepseek"),
            null,
            "Test",
            Instant.EPOCH,
        )
        val result = router.importApiKey("sk-test", account)
        assertTrue(result.isSuccess)
    }

    @Test(expected = NoSuchElementException::class)
    fun missingProvider_throws() = runTest {
        val router = SessionImportRouter(emptyMap())
        val account = ProviderAccount.createNew(
            LocalAccountId("a1"),
            ProviderId("codex"),
            null,
            "Test",
            Instant.EPOCH,
        )
        router.importApiKey("sk-test", account)
    }
}
