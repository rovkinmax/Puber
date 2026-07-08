package com.kino.puber.domain.interactor.update

import com.kino.puber.data.repository.AppUpdateDownload
import com.kino.puber.data.repository.AppUpdateDownloader
import com.kino.puber.data.repository.AppUpdateInstaller
import com.kino.puber.data.repository.AppUpdatePreferencesRepository
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.data.repository.IAppUpdateRepository
import com.kino.puber.data.repository.InstallLaunchResult
import java.io.File

internal class AppUpdateInteractor(
    private val updateRepository: IAppUpdateRepository,
    private val preferencesRepository: AppUpdatePreferencesRepository,
    private val updateDownloader: AppUpdateDownloader,
    private val updateInstaller: AppUpdateInstaller,
) : IAppUpdateInteractor {

    override suspend fun checkForUpdate(currentVersionName: String): Result<AvailableUpdate?> {
        if (!preferencesRepository.autoUpdateCheckEnabled) {
            return Result.success(null)
        }

        return updateRepository.getAvailableUpdate(currentVersionName)
    }

    override fun isAutoCheckEnabled(): Boolean {
        return preferencesRepository.autoUpdateCheckEnabled
    }

    override fun setAutoCheckEnabled(enabled: Boolean) {
        preferencesRepository.autoUpdateCheckEnabled = enabled
    }

    override suspend fun downloadUpdate(
        update: AvailableUpdate,
        onProgress: (AppUpdateDownload.Progress) -> Unit,
    ): AppUpdateDownload {
        return updateDownloader.download(update, onProgress)
    }

    override fun canRequestPackageInstalls(): Boolean {
        return updateInstaller.canRequestPackageInstalls()
    }

    override fun openInstallPermissionSettings() {
        updateInstaller.openInstallPermissionSettings()
    }

    override fun launchInstaller(file: File): InstallLaunchResult {
        return updateInstaller.launchInstaller(file)
    }
}
