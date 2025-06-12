package com.kino.puber.ui.feature.device.settings.vm

import androidx.lifecycle.viewModelScope
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceSettingsVM(
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    router: AppRouter,
) : PuberVM<DeviceSettingsViewState>(router) {

    private val _state = MutableStateFlow(initialViewState)
    val state = _state.asStateFlow()

    init {
        loadDeviceSettings()
    }

    private fun loadDeviceSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
                try {

                    if (currentDevice.isSuccess) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                currentDevice = currentDevice.getOrThrow()
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = currentDevice.exceptionOrNull()?.message
                            )
                        }
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message
                        )
                    }
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