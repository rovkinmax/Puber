package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi
import com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVM
import kotlinx.parcelize.Parcelize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class DeviceSettingsScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        singleOf(::DeviceUiSettingsMapper)
        scope(named(scopeId)) {
            viewModelOf(::DeviceSettingsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val viewModel = koinViewModel<DeviceSettingsVM>()
        val state by viewModel.collectViewState()

        DeviceSettingsContent(
            settings = state.settings,
            errorMessage = state.error,
            isLoading = state.isLoading,
            onValueSettingUpdate = { viewModel.onAction(DeviceSettingsActions.ChangeSettingValue(it)) },
            onListSettingUpdate = { viewModel.onAction(DeviceSettingsActions.ChangeSettingList(it)) },
            onRetry = { viewModel.onAction(DeviceSettingsActions.Retry) },
            device = state.device,
        )
    }
}

@Composable
private fun DeviceSettingsContent(
    settings: DeviceSettingsListUi?,
    device: DeviceUi?,
    errorMessage: String?,
    isLoading: Boolean,
    onValueSettingUpdate: (DeviceSettingUIModel.TypeValue) -> Unit,
    onListSettingUpdate: (DeviceSettingUIModel.TypeList) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> LoadingView()

            errorMessage != null -> ErrorView(errorMessage, onRetry)

            else -> {
                settings?.let { deviceSettings ->
                    DeviceSettingsList(
                        settings = deviceSettings,
                        onValueSettingsUpdate = onValueSettingUpdate,
                        onListSettingsUpdate = onListSettingUpdate,
                        device = device,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    CircularProgressIndicator()
}

@Composable
private fun ErrorView(
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.device_settings_retry))
        }
    }
}

@Composable
private fun DeviceSettingsList(
    settings: DeviceSettingsListUi,
    device: DeviceUi?,
    onValueSettingsUpdate: (DeviceSettingUIModel.TypeValue) -> Unit,
    onListSettingsUpdate: (DeviceSettingUIModel.TypeList) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (device != null) {
            item {
                Column(
                    // todo нужно разобраться с акцентом выделения элементов
                    modifier = Modifier.selectable(
                        selected = false,
                        indication = null,
                        interactionSource = null,
                    ) {}) {
                    Text(
                        text = stringResource(R.string.device_settings_current_device),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DeviceInfoCard(
                        title = device.title,
                        hardware = device.hardware,
                        software = device.software,
                    )

                }

            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }

        item {
            Text(
                text = stringResource(R.string.device_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(settings.settingsList) { setting ->
            when (setting) {
                is DeviceSettingUIModel.TypeValue -> SettingSwitchItem(setting, onValueSettingsUpdate)
                is DeviceSettingUIModel.TypeList -> SettingListItem(setting, onListSettingsUpdate)
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    title: String,
    hardware: String,
    software: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(true) {}
            .focusable(false)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.device_settings_name, title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.device_settings_hardware, hardware),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.device_settings_software, software),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SettingSwitchItem(
    setting: DeviceSettingUIModel.TypeValue,
    onSettingUpdate: (DeviceSettingUIModel.TypeValue) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = (setting.value == 1),
            onCheckedChange = {
                onSettingUpdate.invoke(setting)
            }
        )
    }
}

@Composable
private fun SettingListItem(
    setting: DeviceSettingUIModel.TypeList,
    onSettingUpdate: (DeviceSettingUIModel.TypeList) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onSettingUpdate.invoke(setting) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = setting.values.find { it.selected == 1 }?.label.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
@Preview(device = TV_1080p)
private fun DeviceSettingsContentPreview() {
    DeviceSettingsContent(
        settings = DeviceSettingsListUi(
            settingsList = listOf(
                DeviceSettingUIModel.TypeValue(value = 1, label = "Setting 1"),
                DeviceSettingUIModel.TypeList(
                    type = "type",
                    values = listOf(
                        SettingOptionUi(id = 1, label = "Option 1", selected = 1),
                        SettingOptionUi(id = 2, label = "Option 2", selected = 0)
                    ),
                    label = "Setting 2"
                )
            )
        ),
        device = DeviceUi(
            title = "Device Title",
            hardware = "Hardware Version",
            software = "Software Version"
        ),
        errorMessage = null,
        isLoading = false,
        onValueSettingUpdate = {},
        onListSettingUpdate = {},
        onRetry = {}
    )
}



