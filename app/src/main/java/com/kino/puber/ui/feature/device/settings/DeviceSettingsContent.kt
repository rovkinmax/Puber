package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi

@Composable
internal fun DeviceSettingsContent(
    state: DeviceSettingsState,
    onValueSettingUpdate: (DeviceSettingUIModel.TypeValue) -> Unit,
    onListSettingUpdate: (DeviceSettingUIModel.TypeList) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.Companion.fillMaxSize(),
        contentAlignment = Alignment.Companion.Center
    ) {
        when (state) {
            is DeviceSettingsState.Error -> ErrorView(state.error, onRetry)
            is DeviceSettingsState.Loading -> LoadingView()
            is DeviceSettingsState.Success -> DeviceSettingsList(
                settings = state.settings,
                device = state.device,
                onValueSettingsUpdate = onValueSettingUpdate,
                onListSettingsUpdate = onListSettingUpdate,
            )
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
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Companion.Center,
        )
        Spacer(modifier = Modifier.Companion.height(16.dp))
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
            .focusGroup()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (device != null) {
            item {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    Text(
                        text = stringResource(R.string.device_settings_current_device),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.Companion.height(8.dp))
                    DeviceInfoCard(
                        title = device.title,
                        hardware = device.hardware,
                        software = device.software,
                    )

                }

            }
        }

        item {
            Spacer(modifier = Modifier.Companion.height(16.dp))
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
            .selectable(true, interactionSource = null, indication = null) {}
            .focusable(false)) {
        Column(
            modifier = Modifier.Companion.padding(8.dp),
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
internal fun SettingSwitchItem(
    setting: DeviceSettingUIModel.TypeValue,
    onSettingUpdate: (DeviceSettingUIModel.TypeValue) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val checked = setting.value == 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .background(if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val newValue = if (checked) 0 else 1
                    onSettingUpdate(setting.copy(value = newValue))
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = true,
        )
    }
}

@Composable
private fun SettingListItem(
    setting: DeviceSettingUIModel.TypeList,
    onSettingUpdate: (DeviceSettingUIModel.TypeList) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSettingUpdate(setting) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
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


@Preview(device = TV_1080p)
@Composable
internal fun DeviceSettingsContentPreview() {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = DeviceSettingsListUi(
                listOf(
                    DeviceSettingUIModel.TypeValue(
                        label = "Setting 1",
                        value = 1,
                    ),
                    DeviceSettingUIModel.TypeList(
                        type = "Type",
                        label = "Setting 2",
                        values = listOf(
                            SettingOptionUi(1, "Option 1", "", selected = 1),
                            SettingOptionUi(2, "Option 2", "", selected = 0),
                        )
                    )
                )
            ),
            device = DeviceUi(
                title = "Device Title",
                hardware = "Hardware Version",
                software = "Software Version",
            )
        ),
        onValueSettingUpdate = {},
        onListSettingUpdate = {},
        onRetry = {}
    )
}

