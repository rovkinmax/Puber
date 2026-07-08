package com.kino.puber.domain.interactor.update

import com.kino.puber.data.repository.AppUpdateDownload
import com.kino.puber.data.repository.AppUpdateDownloader
import com.kino.puber.data.repository.AppUpdateInstaller
import com.kino.puber.data.repository.AppUpdatePreferencesRepository
import com.kino.puber.data.repository.AppVersion
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.data.repository.IAppUpdateRepository
import com.kino.puber.data.repository.InstallLaunchResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class AppUpdateInteractorTest {

    private val updateRepository = mockk<IAppUpdateRepository>()
    private val preferencesRepository = mockk<AppUpdatePreferencesRepository>()
    private val updateDownloader = mockk<AppUpdateDownloader>()
    private val updateInstaller = mockk<AppUpdateInstaller>()
    private val interactor = AppUpdateInteractor(
        updateRepository = updateRepository,
        preferencesRepository = preferencesRepository,
        updateDownloader = updateDownloader,
        updateInstaller = updateInstaller,
    )

    @Test
    fun checkForUpdate_returnsNullAndSkipsRepository_whenAutoCheckDisabled() = runTest {
        every { preferencesRepository.autoUpdateCheckEnabled } returns false

        val result = interactor.checkForUpdate(currentVersionName = "1.4.0")

        assertNull(result.getOrThrow())
        coVerify(exactly = 0) { updateRepository.getAvailableUpdate(any()) }
    }

    @Test
    fun checkForUpdate_callsRepository_whenAutoCheckEnabled() = runTest {
        val update = availableUpdate()
        every { preferencesRepository.autoUpdateCheckEnabled } returns true
        coEvery { updateRepository.getAvailableUpdate("1.4.0") } returns Result.success(update)

        val result = interactor.checkForUpdate(currentVersionName = "1.4.0")

        assertEquals(update, result.getOrThrow())
        coVerify(exactly = 1) { updateRepository.getAvailableUpdate("1.4.0") }
    }

    @Test
    fun setAutoCheckEnabled_persistsPreference() {
        every { preferencesRepository.autoUpdateCheckEnabled = false } just Runs

        interactor.setAutoCheckEnabled(enabled = false)

        verify(exactly = 1) { preferencesRepository.autoUpdateCheckEnabled = false }
    }

    @Test
    fun downloadUpdate_delegatesToDownloader() = runTest {
        val update = availableUpdate()
        val completed = AppUpdateDownload.Completed(File("update.apk"))
        coEvery { updateDownloader.download(update, any()) } returns completed

        val result = interactor.downloadUpdate(update) { }

        assertEquals(completed, result)
        coVerify(exactly = 1) { updateDownloader.download(update, any()) }
    }

    @Test
    fun launchInstaller_delegatesToInstaller() {
        val file = File("update.apk")
        every { updateInstaller.launchInstaller(file) } returns InstallLaunchResult.PermissionRequired

        val result = interactor.launchInstaller(file)

        assertEquals(InstallLaunchResult.PermissionRequired, result)
    }

    private fun availableUpdate() = AvailableUpdate(
        version = AppVersion(major = 1, minor = 5, patch = 0),
        tagName = "v1.5.0",
        title = "Puber 1.5.0",
        releaseNotes = "Release notes",
        apkAssetName = "puber-v1.5.0.apk",
        apkDownloadUrl = "https://example.com/puber-v1.5.0.apk",
        apkSizeBytes = 42_000_000,
        checksumDownloadUrl = "https://example.com/puber-v1.5.0.apk.sha256",
        releasePageUrl = "https://github.com/rovkinmax/Puber/releases/tag/v1.5.0",
    )
}
