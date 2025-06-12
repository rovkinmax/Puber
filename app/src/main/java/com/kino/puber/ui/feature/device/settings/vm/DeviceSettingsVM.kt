package com.kino.puber.ui.feature.device.settings.vm

import androidx.lifecycle.viewModelScope
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState
import kotlinx.coroutines.launch

internal class DeviceSettingsVM(
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    private val deviceUiSettingsMapper: DeviceUiSettingsMapper,
    router: AppRouter,
) : PuberVM<DeviceSettingsViewState>(router) {

    override fun onStart() {
        loadDeviceSettings()
    }

    private fun loadDeviceSettings() {
        viewModelScope.launch {
            updateViewState(stateValue.copy(isLoading = true, error = null))
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
                runCatching {
                    if (currentDevice.isSuccess) {
                        updateViewState(
                            stateValue.copy(
                                isLoading = false,
                                error = null,
                                settings = deviceUiSettingsMapper.mapSettings(currentDevice.getOrThrow().device.settings),
                                device = deviceUiSettingsMapper.mapDevice(currentDevice.getOrThrow().device)
                            )
                        )
                    } else throw IllegalStateException(currentDevice.exceptionOrNull())
                }.onFailure {
                    updateViewState(
                        stateValue.copy(
                            isLoading = false,
                            error = it.message.orEmpty()
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