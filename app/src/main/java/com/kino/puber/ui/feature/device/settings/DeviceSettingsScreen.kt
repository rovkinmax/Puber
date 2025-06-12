package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.api.models.SettingList
import com.kino.puber.data.api.models.SettingValue
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState
import com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVM
import kotlinx.parcelize.Parcelize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class DeviceSettingsScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::DeviceSettingsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val viewModel = koinViewModel<DeviceSettingsVM>()
        val state by viewModel.state.collectAsState()

        DeviceSettingsContent(
            state = state,
            onAction = viewModel::onAction,
        )
    }
}

@Composable
private fun DeviceSettingsContent(
    state: DeviceSettingsViewState,
    onAction: (DeviceSettingsActions) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> LoadingView()

            state.error != null -> ErrorView(state.error, onAction)

            else -> {
                state.currentDevice?.let { deviceResponse ->
                    DeviceSettingsList(deviceResponse, onAction)
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
    onAction: (DeviceSettingsActions) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onAction(DeviceSettingsActions.Retry) }) {
            Text("Повторить")
        }
    }
}

@Composable
private fun DeviceSettingsList(
    deviceResponse: DeviceResponse,
    onAction: (DeviceSettingsActions) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Текущее устройство",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            DeviceInfoCard(deviceResponse)
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }

        item {
            Text(
                text = "Настройки устройства",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val settingsList = buildList {
            add(deviceResponse.device.settings.supportSsl)
            add(deviceResponse.device.settings.supportHevc)
            add(deviceResponse.device.settings.supportHdr)
            add(deviceResponse.device.settings.support4k)
            add(deviceResponse.device.settings.mixedPlaylist)
            add(deviceResponse.device.settings.serverLocation)
            add(deviceResponse.device.settings.streamingType)
        }

        items(settingsList) { setting ->
            when (setting) {
                is SettingValue -> SettingSwitchItem(setting, onAction)
                is SettingList -> SettingListItem(setting, onAction)
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(deviceResponse: DeviceResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Название: ${deviceResponse.device.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "Оборудование: ${deviceResponse.device.hardware}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "Программное обеспечение: ${deviceResponse.device.software}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SettingSwitchItem(
    setting: SettingValue,
    onAction: (DeviceSettingsActions) -> Unit
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
                onAction(DeviceSettingsActions.ChangeSettingValue(setting))
            }
        )
    }
}

@Composable
private fun SettingListItem(
    setting: SettingList,
    onAction: (DeviceSettingsActions) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onAction.invoke(DeviceSettingsActions.ChangeSettingList(setting)) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = setting.value.find { it.selected == 1 }?.label.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Preview(device = Devices.TV_1080p, showBackground = true)
@Composable
private fun DeviceSettingsContentPreview() {
    DeviceSettingsContent(
        state = DeviceSettingsViewState(
            isLoading = false,
            error = null,
            currentDevice = null,
        ),
        onAction = {}
    )
}
