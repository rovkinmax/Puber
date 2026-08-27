package com.kino.puber.profile

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import com.kino.puber.PuberApp
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.ui.feature.main.model.TabType
import org.koin.core.module.Module
import org.koin.dsl.module

internal class BaselineProfileApp : PuberApp() {

    override fun shouldInitializeRuntime(): Boolean = !isControlProcess()

    override fun configureRuntime() {
        BaselineInstrumentationEnvironment.configure(this)
    }

    override fun runtimeModules(): List<Module> = listOf(
        module {
            single<ICryptoPreferenceRepository> { SyntheticAuthPreferences }
            single<IAppUpdateInteractor> { NoOpAppUpdateInteractor }
            single {
                NavigationPreferencesRepository(
                    navigationMode = NavigationMode.TopTabs,
                    visibleTabs = BASELINE_TABS,
                    contentPreferences = ContentPreferences(
                        showCartoonsTab = false,
                        showAnimeTab = false,
                        showAnime = false,
                    ),
                )
            }
        }
    )

    private fun isControlProcess(): Boolean =
        currentProcessName()?.endsWith(CONTROL_PROCESS_SUFFIX) == true

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return getSystemService(ActivityManager::class.java)
            .runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
    }

    private companion object {
        const val CONTROL_PROCESS_SUFFIX = ":baseline_profile_control"

        val BASELINE_TABS = listOf(
            TabType.Home,
            TabType.Movies,
            TabType.Series,
            TabType.Collections,
            TabType.History,
        )
    }
}

private object SyntheticAuthPreferences : ICryptoPreferenceRepository {
    override fun saveAccessToken(token: String) = Unit

    override fun getAccessToken(): String = "baseline-access-token"

    override fun clearAccessToken() = Unit

    override fun saveRefreshToken(token: String) = Unit

    override fun getRefreshToken(): String = "baseline-refresh-token"

    override fun clearRefreshToken() = Unit

    override fun saveUsername(userName: String) = Unit

    override fun getUsername(): String = "baseline-profile"

    override fun clearUsername() = Unit

    override fun getAndroidId(): String = "baseline-profile-device"

    override fun saveApiDomain(domain: String?) = Unit

    override fun getApiDomain(): String? = null
}

private object NoOpAppUpdateInteractor : IAppUpdateInteractor {
    override suspend fun checkForUpdate(currentVersionName: String) = Result.success(null)

    override fun isAutoCheckEnabled(): Boolean = false

    override fun setAutoCheckEnabled(enabled: Boolean) = Unit

    override suspend fun downloadUpdate(
        update: com.kino.puber.data.repository.AvailableUpdate,
        onProgress: (com.kino.puber.data.repository.AppUpdateDownload.Progress) -> Unit,
    ): com.kino.puber.data.repository.AppUpdateDownload =
        com.kino.puber.data.repository.AppUpdateDownload.Error.StorageUnavailable

    override fun canRequestPackageInstalls(): Boolean = false

    override fun openInstallPermissionSettings() = Unit

    override fun launchInstaller(file: java.io.File) =
        com.kino.puber.data.repository.InstallLaunchResult.PermissionRequired
}
