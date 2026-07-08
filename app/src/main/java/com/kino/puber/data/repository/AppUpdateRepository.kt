package com.kino.puber.data.repository

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.GitHubReleaseAssetResponse
import com.kino.puber.data.api.models.GitHubReleaseResponse

internal class AppUpdateRepository(
    private val apiClient: KinoPubApiClient,
) : IAppUpdateRepository {

    override suspend fun getAvailableUpdate(currentVersionName: String): Result<AvailableUpdate?> {
        val currentVersion = AppVersion.parse(currentVersionName)
            ?: return Result.success(null).also {
                log("App update: ignoring malformed current version '$currentVersionName'")
            }

        return apiClient.getLatestGitHubRelease(GITHUB_OWNER, GITHUB_REPO).mapCatching { release ->
            release.toAvailableUpdate(currentVersion)
        }
    }

    private fun GitHubReleaseResponse.toAvailableUpdate(currentVersion: AppVersion): AvailableUpdate? {
        if (draft || prerelease) {
            return null
        }

        val latestVersion = AppVersion.parse(tagName)
            ?: return null.also {
                log("App update: ignoring malformed latest release tag '$tagName'")
            }

        if (latestVersion <= currentVersion) {
            return null
        }

        val apkAsset = selectApkAsset(assets) ?: return null
        val checksumAsset = assets.firstOrNull { asset ->
            asset.name == "${apkAsset.name}.sha256" && asset.browserDownloadUrl.isNotBlank()
        }

        return AvailableUpdate(
            version = latestVersion,
            tagName = tagName,
            title = name?.takeIf { it.isNotBlank() } ?: tagName,
            releaseNotes = body,
            apkAssetName = apkAsset.name,
            apkDownloadUrl = apkAsset.browserDownloadUrl,
            apkSizeBytes = apkAsset.size,
            checksumDownloadUrl = checksumAsset?.browserDownloadUrl,
            releasePageUrl = htmlUrl,
        )
    }

    private fun selectApkAsset(assets: List<GitHubReleaseAssetResponse>): GitHubReleaseAssetResponse? {
        val apkAssets = assets.filter { asset ->
            asset.name.endsWith(APK_EXTENSION, ignoreCase = true) && asset.browserDownloadUrl.isNotBlank()
        }

        return apkAssets.firstOrNull { asset ->
            asset.contentType.equals(APK_CONTENT_TYPE, ignoreCase = true)
        } ?: apkAssets.firstOrNull()
    }

    private companion object {
        const val GITHUB_OWNER = "rovkinmax"
        const val GITHUB_REPO = "Puber"
        const val APK_EXTENSION = ".apk"
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
    }
}
