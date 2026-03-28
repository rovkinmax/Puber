package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceCapabilities
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState
import com.kino.puber.data.repository.PlayerPreferencesRepository

internal class DeviceSettingsVM(
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    private val deviceUiSettingsMapper: DeviceUiSettingsMapper,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<DeviceSettingsViewState>(router) {

    private val capabilities by lazy {
        DeviceCapabilities(
            hevcSupported = deviceInfoInteractor.isHevcSupported(),
            hdrSupported = deviceInfoInteractor.isHdrSupported(),
            is4kSupported = deviceInfoInteractor.is4kSupported(),
        )
    }

    override val initialViewState: DeviceSettingsViewState
        get() = DeviceSettingsViewState()

    override fun onStart() {
        loadDeviceSettings()
    }

    private fun loadDeviceSettings() {
        launch {
            updateViewState(stateValue.copy(state = DeviceSettingsState.Loading))
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
                if (currentDevice.isSuccess) {
                    val device = currentDevice.getOrThrow()
                    updateViewState(
                        stateValue.copy(
                            state = DeviceSettingsState.Success(
                                settings = deviceUiSettingsMapper.mapSettings(device.device.settings, capabilities),
                                device = deviceUiSettingsMapper.mapDevice(device.device),
                                skipIntroEnabled = playerPreferencesRepository.skipIntroEnabled,
                                skipRecapEnabled = playerPreferencesRepository.skipRecapEnabled,
                                skipCreditsEnabled = playerPreferencesRepository.skipCreditsEnabled,
                                debugOverlayEnabled = playerPreferencesRepository.debugOverlayEnabled,
                            )
                        )
                    )
                } else throw IllegalStateException(currentDevice.exceptionOrNull())
            }
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is DeviceSettingsActions.ChangeSettingValue -> onChangeSettingValue(action.setting)
            is DeviceSettingsActions.ToggleListExpand -> onToggleListExpand(action.setting)
            is DeviceSettingsActions.SelectOption -> onSelectOption(action.type, action.optionId)
            DeviceSettingsActions.UnlinkDevice -> onUnlinkDevice()
            DeviceSettingsActions.ToggleSkipIntro -> toggleSkipPref { it.copy(skipIntroEnabled = !it.skipIntroEnabled) }
            DeviceSettingsActions.ToggleSkipRecap -> toggleSkipPref { it.copy(skipRecapEnabled = !it.skipRecapEnabled) }
            DeviceSettingsActions.ToggleSkipCredits -> toggleSkipPref { it.copy(skipCreditsEnabled = !it.skipCreditsEnabled) }
            DeviceSettingsActions.ToggleDebugOverlay -> toggleDebugOverlay()
            CommonAction.RetryClicked -> onRetry()
            else -> super.onAction(action)
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        val currentState = stateValue.state
        if (currentState is DeviceSettingsState.Success) {
            updateViewState(
                stateValue.copy(state = currentState.copy(savingOptionId = null, savingToggleType = null))
            )
        }
        showMessage(error.message)
    }

    private fun onChangeSettingValue(setting: DeviceSettingUIModel.TypeValue) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (currentState.savingToggleType != null) return

        // Optimistic update + show progress
        updateViewState(
            stateValue.copy(
                state = applyToggle(currentState, setting).copy(savingToggleType = setting.type)
            )
        )

        launch {
            try {
                val apiValue = if (setting.value) 1 else 0
                deviceSettingInteractor.updateDeviceSetting(setting.type, apiValue)
                // Clear progress on success
                val successState = stateValue.state
                if (successState is DeviceSettingsState.Success) {
                    updateViewState(stateValue.copy(state = successState.copy(savingToggleType = null)))
                }
            } catch (e: Exception) {
                // Revert on error + clear progress
                val revertedSetting = setting.copy(value = !setting.value)
                val revertState = stateValue.state
                if (revertState is DeviceSettingsState.Success) {
                    updateViewState(
                        stateValue.copy(
                            state = applyToggle(revertState, revertedSetting).copy(savingToggleType = null)
                        )
                    )
                }
                throw e // re-throw for dispatchError → showMessage
            }
        }
    }

    private fun applyToggle(
        currentState: DeviceSettingsState.Success,
        setting: DeviceSettingUIModel.TypeValue,
    ): DeviceSettingsState.Success {
        val updatedList = currentState.settings.settingsList.map { item ->
            if (item is DeviceSettingUIModel.TypeValue && item.type == setting.type) {
                item.copy(value = setting.value)
            } else {
                item
            }
        }
        return currentState.copy(settings = DeviceSettingsListUi(updatedList))
    }

    private fun onToggleListExpand(setting: DeviceSettingUIModel.TypeList) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return

        val newExpanded = if (currentState.expandedType == setting.type) null else setting.type
        updateViewState(
            stateValue.copy(state = currentState.copy(expandedType = newExpanded))
        )
    }

    private fun onSelectOption(type: DeviceSettingType, optionId: Int) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (currentState.savingOptionId != null) return

        updateViewState(
            stateValue.copy(state = currentState.copy(savingOptionId = optionId))
        )

        launch {
            deviceSettingInteractor.updateDeviceSetting(type, optionId)
            applyListSettingLocally(type, optionId)
        }
    }

    private fun applyListSettingLocally(type: DeviceSettingType, selectedOptionId: Int) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return

        val updatedList = currentState.settings.settingsList.map { item ->
            if (item is DeviceSettingUIModel.TypeList && item.type == type) {
                item.copy(
                    values = item.values.map { option ->
                        option.copy(selected = option.id == selectedOptionId)
                    }
                )
            } else {
                item
            }
        }
        updateViewState(
            stateValue.copy(
                state = currentState.copy(
                    settings = DeviceSettingsListUi(updatedList),
                    expandedType = null,
                    savingOptionId = null,
                )
            )
        )
    }

    private fun toggleSkipPref(update: (DeviceSettingsState.Success) -> DeviceSettingsState.Success) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        val newState = update(currentState)
        playerPreferencesRepository.skipIntroEnabled = newState.skipIntroEnabled
        playerPreferencesRepository.skipRecapEnabled = newState.skipRecapEnabled
        playerPreferencesRepository.skipCreditsEnabled = newState.skipCreditsEnabled
        updateViewState(stateValue.copy(state = newState))
    }

    private fun toggleDebugOverlay() {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        val newValue = !currentState.debugOverlayEnabled
        playerPreferencesRepository.debugOverlayEnabled = newValue
        updateViewState(stateValue.copy(state = currentState.copy(debugOverlayEnabled = newValue)))
    }

    private fun onUnlinkDevice() {
        //todo
    }

    private fun onRetry() {
        loadDeviceSettings()
    }
}
