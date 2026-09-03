package app.quotatrail.storage.repository

import app.quotatrail.foundation.network.ProviderHttpClient
import app.quotatrail.domain.update.AppUpdateCheckResult
import app.quotatrail.domain.update.AppUpdateCheckUseCase
import app.quotatrail.domain.update.AppUpdateInfo
import app.quotatrail.domain.update.AppVersionComparator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GitHubReleaseAppUpdateChecker(
    private val httpClient: ProviderHttpClient,
    private val latestReleaseApiUrl: String = DEFAULT_LATEST_RELEASE_API_URL,
    private val releasesPageUrl: String = DEFAULT_RELEASES_PAGE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AppUpdateCheckUseCase {
    override suspend fun checkForUpdate(currentVersionName: String): AppUpdateCheckResult {
        val response = runCatching {
            httpClient.get(
                url = latestReleaseApiUrl,
                headers = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to "QuotaTrail-Android",
                ),
            )
        }.getOrElse {
            return AppUpdateCheckResult.Failure("github_release_request_failed")
        }

        if (response.statusCode == HTTP_NOT_FOUND) return AppUpdateCheckResult.NoRelease
        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            return AppUpdateCheckResult.Failure("github_release_http_${response.statusCode}")
        }

        val release = runCatching {
            json.decodeFromString(GitHubLatestReleaseDto.serializer(), response.body)
        }.getOrElse {
            return AppUpdateCheckResult.Failure("github_release_parse_failed")
        }
        val versionName = release.tagName?.takeIf { it.isNotBlank() }
            ?: release.name?.takeIf { it.isNotBlank() }
            ?: return AppUpdateCheckResult.NoRelease

        if (!AppVersionComparator.isRemoteVersionNewer(currentVersionName, versionName)) {
            return AppUpdateCheckResult.UpToDate
        }

        val apkAsset = release.assets.firstOrNull { asset ->
            asset.name.endsWith(APK_SUFFIX, ignoreCase = true) && asset.browserDownloadUrl.isNotBlank()
        } ?: return AppUpdateCheckResult.NoApkAsset

        return AppUpdateCheckResult.UpdateAvailable(
            AppUpdateInfo(
                versionName = versionName,
                releasePageUrl = release.htmlUrl?.takeIf { it.isNotBlank() } ?: releasesPageUrl,
                apkDownloadUrl = apkAsset.browserDownloadUrl,
                apkFileName = apkAsset.name,
                releaseNotes = release.body?.takeIf { it.isNotBlank() },
            ),
        )
    }

    @Serializable
    private data class GitHubLatestReleaseDto(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val assets: List<GitHubReleaseAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    private companion object {
        const val DEFAULT_LATEST_RELEASE_API_URL = "https://api.github.com/repos/plainpacket/QuotaTrail/releases/latest"
        const val DEFAULT_RELEASES_PAGE_URL = "https://github.com/plainpacket/QuotaTrail/releases"
        const val HTTP_NOT_FOUND = 404
        val HTTP_SUCCESS_RANGE = 200..299
        const val APK_SUFFIX = ".apk"
    }
}
