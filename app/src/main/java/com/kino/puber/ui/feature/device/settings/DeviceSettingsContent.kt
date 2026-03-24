package com.kino.puber.ui.feature.device.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.theme.highlightOnFocus
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi

@Composable
internal fun DeviceSettingsContent(
    state: DeviceSettingsState,
    onValueSettingUpdate: (DeviceSettingUIModel.TypeValue) -> Unit = {},
    onToggleExpand: (DeviceSettingUIModel.TypeList) -> Unit = {},
    onOptionSelect: (DeviceSettingType, Int) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is DeviceSettingsState.Error -> ErrorView(state.error, onRetry)
            is DeviceSettingsState.Loading -> LoadingView()
            is DeviceSettingsState.Success -> DeviceSettingsList(
                settings = state.settings,
                device = state.device,
                expandedType = state.expandedType,
                savingOptionId = state.savingOptionId,
                onValueSettingsUpdate = onValueSettingUpdate,
                onToggleExpand = onToggleExpand,
                onOptionSelect = onOptionSelect,
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
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
    expandedType: DeviceSettingType?,
    savingOptionId: Int?,
    onValueSettingsUpdate: (DeviceSettingUIModel.TypeValue) -> Unit,
    onToggleExpand: (DeviceSettingUIModel.TypeList) -> Unit,
    onOptionSelect: (DeviceSettingType, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // Header items count before settings: device info (if present) + spacer + title
    val headerItemsCount = (if (device != null) 1 else 0) + 2

    LazyColumn(
        state = listState,
        modifier = Modifier
            .focusGroup()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (device != null) {
            item {
                Column(modifier = Modifier.selectableGroup()) {
                    Text(
                        text = stringResource(R.string.device_settings_current_device),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceInfoCard(
                        title = device.title,
                        hardware = device.hardware,
                        software = device.software,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                text = stringResource(R.string.device_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        itemsIndexed(settings.settingsList) { index, setting ->
            when (setting) {
                is DeviceSettingUIModel.TypeValue -> SettingSwitchItem(
                    setting,
                    onValueSettingsUpdate
                )

                is DeviceSettingUIModel.TypeList -> SettingListItem(
                    setting = setting,
                    isExpanded = setting.type == expandedType,
                    savingOptionId = if (setting.type == expandedType) savingOptionId else null,
                    onToggleExpand = { onToggleExpand(setting) },
                    onOptionSelect = { optionId -> onOptionSelect(setting.type, optionId) },
                    listState = listState,
                    lazyItemIndex = headerItemsCount + index,
                )
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
            .focusable(false)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .highlightOnFocus(isFocused)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSettingUpdate(setting.copy(value = !setting.value)) }
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
            checked = setting.value,
            onCheckedChange = null,
            enabled = true,
        )
    }
}

@Composable
private fun SettingListItem(
    setting: DeviceSettingUIModel.TypeList,
    isExpanded: Boolean,
    savingOptionId: Int?,
    onToggleExpand: () -> Unit,
    onOptionSelect: (Int) -> Unit,
    listState: LazyListState? = null,
    lazyItemIndex: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val headerFocusRequester = remember { FocusRequester() }
    val optionsFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .focusRestorer(headerFocusRequester)
            .focusGroup()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(headerFocusRequester)
                .highlightOnFocus(isFocused)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggleExpand,
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
                text = setting.values.find { it.selected }?.label.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(optionsFocusRequester)
                    .onKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            onToggleExpand()
                            true
                        } else false
                    }
                    .focusGroup()
                    .padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                setting.values.forEach { option ->
                    OptionItem(
                        option = option,
                        isSaving = savingOptionId == option.id,
                        onClick = {
                            headerFocusRequester.requestFocus()
                            onOptionSelect(option.id)
                        },
                    )
                }
            }
        }

        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                listState?.animateScrollToItem(lazyItemIndex)
                optionsFocusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun OptionItem(
    option: SettingOptionUi,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .highlightOnFocus(isFocused)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            RadioButton(
                selected = option.selected,
                onClick = onClick,
            )
        }
    }
}

@Preview(device = TV_1080p)
@Composable
private fun DeviceSettingsContentPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = DeviceSettingsListUi(
                listOf(
                    DeviceSettingUIModel.TypeValue(
                        label = "Setting 1",
                        value = true,
                    ),
                    DeviceSettingUIModel.TypeList(
                        type = DeviceSettingType.STREAMING_TYPE,
                        label = "Streaming Type",
                        values = listOf(
                            SettingOptionUi(1, "HLS", "", selected = true),
                            SettingOptionUi(2, "HLS2", "", selected = false),
                            SettingOptionUi(3, "HLS4", "", selected = false),
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
    )
}

@Preview(device = TV_1080p)
@Composable
private fun DeviceSettingsExpandedPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = DeviceSettingsListUi(
                listOf(
                    DeviceSettingUIModel.TypeValue(
                        label = "Setting 1",
                        value = true,
                    ),
                    DeviceSettingUIModel.TypeList(
                        type = DeviceSettingType.STREAMING_TYPE,
                        label = "Streaming Type",
                        values = listOf(
                            SettingOptionUi(1, "HLS", "", selected = true),
                            SettingOptionUi(2, "HLS2", "", selected = false),
                            SettingOptionUi(3, "HLS4", "", selected = false),
                        )
                    )
                )
            ),
            device = DeviceUi(
                title = "Device Title",
                hardware = "Hardware Version",
                software = "Software Version",
            ),
            expandedType = DeviceSettingType.STREAMING_TYPE,
        ),
    )
}

@Preview(device = TV_1080p)
@Composable
private fun DeviceSettingsSavingPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = DeviceSettingsListUi(
                listOf(
                    DeviceSettingUIModel.TypeList(
                        type = DeviceSettingType.STREAMING_TYPE,
                        label = "Streaming Type",
                        values = listOf(
                            SettingOptionUi(1, "HLS", "", selected = true),
                            SettingOptionUi(2, "HLS2", "", selected = false),
                        )
                    )
                )
            ),
            device = DeviceUi(
                title = "Test Device",
                hardware = "Hardware",
                software = "Software",
            ),
            expandedType = DeviceSettingType.STREAMING_TYPE,
            savingOptionId = 2,
        ),
    )
}
