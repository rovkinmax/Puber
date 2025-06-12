package com.kino.puber.ui.feature.device.settings.vm

import androidx.lifecycle.viewModelScope
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState
import kotlinx.coroutines.launch

internal class DeviceSettingsVM(
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    router: AppRouter,
) : PuberVM<DeviceSettingsViewState>(router) {

    override fun onStart() {
        loadDeviceSettings()
    }

    private fun loadDeviceSettings() {
        viewModelScope.launch {
            updateViewState(stateValue.copy(isLoading = true, error = null))
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
                try {
                    if (currentDevice.isSuccess) {
                        updateViewState(
                            stateValue.copy(
                                isLoading = false,
                                currentDevice = currentDevice.getOrThrow()
                            )
                        )
                    } else {
                        updateViewState(
                            stateValue.copy(
                                isLoading = false,
                                error = currentDevice.exceptionOrNull()?.message
                            )
                        )
                    }
                } catch (e: Exception) {
                    updateViewState(
                        stateValue.copy(
                            isLoading = false,
                            error = e.message
                        )
                    )
                }
            }
        }
    }

    override fun onAction(action: UIAction) {
        if (action is DeviceSettingsActions) {
            when (action) {
                is DeviceSettingsActions.ChangeSettingList -> TODO()
                is DeviceSettingsActions.ChangeSettingValue -> TODO()
                DeviceSettingsActions.UnlinkDevice -> onUnlinkDevice()
                DeviceSettingsActions.Retry -> onRetry()
            }
        }
    }

    private fun onUnlinkDevice() {
        //todo
    }

    private fun onRetry() {
        loadDeviceSettings()
    }

    override val initialViewState: DeviceSettingsViewState
        get() = DeviceSettingsViewState()
} 