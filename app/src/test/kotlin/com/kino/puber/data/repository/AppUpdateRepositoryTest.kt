package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.GitHubReleaseAssetResponse
import com.kino.puber.data.api.models.GitHubReleaseResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class AppUpdateRepositoryTest {

    private val apiClient = mockk<KinoPubApiClient>()
    private val repository = AppUpdateRepository(apiClient)

    @Test
    fun getAvailableUpdate_returnsUpdate_whenLatestReleaseIsNewer() = runTest {
        val apkAsset = releaseAsset(
            name = "puber-v1.5.0.apk",
            browserDownloadUrl = "https://github.com/rovkinmax/Puber/releases/download/v1.5.0/puber-v1.5.0.apk",
            size = 42_000_000,
            contentType = "application/vnd.android.package-archive",
        )
        val checksumAsset = releaseAsset(
            name = "puber-v1.5.0.apk.sha256",
            browserDownloadUrl = "https://github.com/rovkinmax/Puber/releases/download/v1.5.0/puber-v1.5.0.apk.sha256",
        )
        coEvery { apiClient.getLatestGitHubRelease("rovkinmax", "Puber") } returns Result.success(
            release(
                tagName = "v1.5.0",
                name = "Puber 1.5.0",
                body = "Release notes",
                htmlUrl = "https://github.com/rovkinmax/Puber/releases/tag/v1.5.0",
                assets = listOf(apkAsset, checksumAsset),
            )
        )

        val result = repository.getAvailableUpdate(currentVersionName = "1.4.0")

        val update = result.getOrThrow()
        assertEquals(AppVersion(major = 1, minor = 5, patch = 0), update?.version)
        assertEquals("v1.5.0", update?.tagName)
        assertEquals("Puber 1.5.0", update?.title)
        assertEquals("Release notes", update?.releaseNotes)
        assertEquals(apkAsset.name, update?.apkAssetName)
        assertEquals(apkAsset.browserDownloadUrl, update?.apkDownloadUrl)
        assertEquals(apkAsset.size, update?.apkSizeBytes)
        assertEquals(checksumAsset.browserDownloadUrl, update?.checksumDownloadUrl)
        assertEquals("https://github.com/rovkinmax/Puber/releases/tag/v1.5.0", update?.releasePageUrl)
    }

    @Test
    fun getAvailableUpdate_prefersApkAssetWithPackageArchiveContentType() = runTest {
        val genericApkAsset = releaseAsset(
            name = "generic.apk",
            browserDownloadUrl = "https://example.com/generic.apk",
            contentType = "application/octet-stream",
        )
        val packageApkAsset = releaseAsset(
            name = "package.apk",
            browserDownloadUrl = "https://example.com/package.apk",
            contentType = "application/vnd.android.package-archive",
        )
        coEvery { apiClient.getLatestGitHubRelease("rovkinmax", "Puber") } returns Result.success(
            release(tagName = "1.5.0", assets = listOf(genericApkAsset, packageApkAsset))
        )

        val result = repository.getAvailableUpdate(currentVersionName = "1.4.0")

        assertEquals("package.apk", result.getOrThrow()?.apkAssetName)
    }

    @Test
    fun getAvailableUpdate_returnsNull_whenLatestReleaseIsNotNewer() = runTest {
        coEvery { apiClient.getLatestGitHubRelease("rovkinmax", "Puber") } returns Result.success(
            release(tagName = "1.4.0", assets = listOf(releaseAsset(name = "puber.apk")))
        )

        val equalResult = repository.getAvailableUpdate(currentVersionName = "1.4.0")

        assertNull(equalResult.getOrThrow())

        coEvery { apiClient.getLatestGitHubRelease("rovkinmax", "Puber") } returns Result.success(
            release(tagName = "1.3.9", assets = listOf(releaseAsset(name = "puber.apk")))
        )

        val olderResult = repository.getAvailableUpdate(currentVersionName = "1.4.0")

        assertNull(olderResult.getOrThrow())
    }

    @Test
    fun getAvailableUpdate_returnsNull_whenReleaseCannotBeUsed() = runTest {
        val unusableReleases = listOf(
            release(tagName = "1.5.0", draft = true, assets = listOf(releaseAsset(name = "puber.apk"))),
            release(tagName = "1.5.0", prerelease = true, assets = listOf(releaseAsset(name = "puber.apk"))),
            release(tagName = "malformed", assets = listOf(releaseAsset(name = "puber.apk"))),
            release(tagName = "1.5.0", assets = emptyList()),
            release(tagName = "1.5.0", assets = listOf(releaseAsset(name = "puber.zip"))),
        )

        unusableReleases.forEach { release ->
            coEvery { apiClient.getLatestGitHubRelease("rovkinmax", "Puber") } returns Result.success(release)

            val result = repository.getAvailableUpdate(currentVersionName = "1.4.0")

            assertNull(result.getOrThrow(), "Expected release '$release' to be ignored")
        }
    }

    @Test
    fun getAvailableUpdate_returnsNullAndSkipsApiCall_whenCurrentVersionIsMalformed() = runTest {
        val result = repository.getAvailableUpdate(currentVersionName = "dev-build")

        assertNull(result.getOrThrow())
        coVerify(exactly = 0) { apiClient.getLatestGitHubRelease(any(), any()) }
    }

    @Test
    fun getAvailableUpdate_returnsFailure_whenApiFails() = runTest {
        val exception = IOException("Network error")
        coEvery { apiClient.getLatestGitHubRelease("rovkinmax", "Puber") } returns Result.failure(exception)

        val result = repository.getAvailableUpdate(currentVersionName = "1.4.0")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    private fun release(
        tagName: String,
        name: String? = null,
        body: String? = null,
        htmlUrl: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<GitHubReleaseAssetResponse> = emptyList(),
    ) = GitHubReleaseResponse(
        tagName = tagName,
        name = name,
        body = body,
        htmlUrl = htmlUrl,
        draft = draft,
        prerelease = prerelease,
        assets = assets,
    )

    private fun releaseAsset(
        name: String,
        browserDownloadUrl: String = "https://example.com/$name",
        size: Long? = null,
        contentType: String? = null,
    ) = GitHubReleaseAssetResponse(
        name = name,
        browserDownloadUrl = browserDownloadUrl,
        size = size,
        contentType = contentType,
    )
}
