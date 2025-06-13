package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState

internal class DeviceSettingsVM(
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    private val deviceUiSettingsMapper: DeviceUiSettingsMapper,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<DeviceSettingsViewState>(router) {

    override val initialViewState: DeviceSettingsViewState
        get() = DeviceSettingsViewState()

    override fun onStart() {
        loadDeviceSettings()
    }

    private fun loadDeviceSettings() {
        launch {
            updateViewState(stateValue.copy(isLoading = true, error = null))
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
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
            }
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is DeviceSettingsActions.ChangeSettingList -> TODO()
            is DeviceSettingsActions.ChangeSettingValue -> TODO()
            DeviceSettingsActions.UnlinkDevice -> onUnlinkDevice()
            CommonAction.RetryClicked -> onRetry()
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        updateViewState(
            stateValue.copy(
                isLoading = false,
                error = error.message
            )
        )
    }

    private fun onUnlinkDevice() {
        //todo
    }

    private fun onRetry() {
        loadDeviceSettings()
    }
} 