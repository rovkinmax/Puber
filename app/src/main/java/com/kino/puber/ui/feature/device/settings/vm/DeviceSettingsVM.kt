package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
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
            updateViewState(stateValue.copy(state = DeviceSettingsState.Loading))
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
                if (currentDevice.isSuccess) {
                    updateViewState(
                        stateValue.copy(
                            state = DeviceSettingsState.Success(
                                deviceUiSettingsMapper.mapSettings(currentDevice.getOrThrow().device.settings),
                                deviceUiSettingsMapper.mapDevice(currentDevice.getOrThrow().device)
                            )
                        )
                    )
                } else throw IllegalStateException(currentDevice.exceptionOrNull())
            }
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is DeviceSettingsActions.ChangeSettingValue -> {}
            is DeviceSettingsActions.ToggleListExpand -> onToggleListExpand(action.setting)
            is DeviceSettingsActions.SelectOption -> onSelectOption(action.type, action.optionId)
            DeviceSettingsActions.UnlinkDevice -> onUnlinkDevice()
            CommonAction.RetryClicked -> onRetry()
            else -> super.onAction(action)
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        val currentState = stateValue.state
        if (currentState is DeviceSettingsState.Success) {
            updateViewState(
                stateValue.copy(state = currentState.copy(savingOptionId = null))
            )
        }
        showMessage(error.message)
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
            applySettingLocally(type, optionId)
        }
    }

    private fun applySettingLocally(type: DeviceSettingType, selectedOptionId: Int) {
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

    private fun onUnlinkDevice() {
        //todo
    }

    private fun onRetry() {
        loadDeviceSettings()
    }
}