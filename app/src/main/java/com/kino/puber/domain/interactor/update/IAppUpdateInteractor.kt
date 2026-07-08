package com.kino.puber.domain.interactor.update

import com.kino.puber.data.repository.AppUpdateDownload
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.data.repository.InstallLaunchResult
import java.io.File

internal interface IAppUpdateInteractor {

    suspend fun checkForUpdate(currentVersionName: String): Result<AvailableUpdate?>

    fun isAutoCheckEnabled(): Boolean

    fun setAutoCheckEnabled(enabled: Boolean)

    suspend fun downloadUpdate(
        update: AvailableUpdate,
        onProgress: (AppUpdateDownload.Progress) -> Unit,
    ): AppUpdateDownload

    fun canRequestPackageInstalls(): Boolean

    fun openInstallPermissionSettings()

    fun launchInstaller(file: File): InstallLaunchResult
}
