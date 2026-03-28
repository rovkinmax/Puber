package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.highlightOnFocus
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi

@Composable
internal fun DeviceSettingsContent(
    state: DeviceSettingsState,
    onAction: (UIAction) -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is DeviceSettingsState.Error -> ErrorView(
                error = state.error,
                onRetry = { onAction(CommonAction.RetryClicked) },
            )
            is DeviceSettingsState.Loading -> LoadingView()
            is DeviceSettingsState.Success -> DeviceSettingsList(
                state = state,
                onAction = onAction,
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
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val headerItemsCount = (if (state.device != null) 1 else 0) + 2

    LazyColumn(
        state = listState,
        modifier = Modifier
            .focusGroup()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.device != null) {
            item {
                Column(modifier = Modifier.selectableGroup()) {
                    Text(
                        text = stringResource(R.string.device_settings_current_device),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceInfoCard(device = state.device)
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

        itemsIndexed(state.settings.settingsList) { index, setting ->
            when (setting) {
                is DeviceSettingUIModel.TypeValue -> SettingSwitchItem(
                    setting = setting,
                    isSaving = state.savingToggleType == setting.type,
                    onToggle = {
                        onAction(DeviceSettingsActions.ChangeSettingValue(setting.copy(value = !setting.value)))
                    },
                )

                is DeviceSettingUIModel.TypeList -> SettingListItem(
                    setting = setting,
                    isExpanded = setting.type == state.expandedType,
                    savingOptionId = if (setting.type == state.expandedType) state.savingOptionId else null,
                    onToggleExpand = { onAction(DeviceSettingsActions.ToggleListExpand(setting)) },
                    onOptionSelect = { optionId ->
                        onAction(DeviceSettingsActions.SelectOption(setting.type, optionId))
                    },
                    listState = listState,
                    lazyItemIndex = headerItemsCount + index,
                )
            }
        }

        // Skip segments section (local-only preferences)
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Column {
                Text(
                    text = stringResource(R.string.settings_skip_segments_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_skip_segments_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            LocalToggleItem(
                label = stringResource(R.string.settings_skip_intro),
                checked = state.skipIntroEnabled,
                onToggle = { onAction(DeviceSettingsActions.ToggleSkipIntro) },
            )
        }
        item {
            LocalToggleItem(
                label = stringResource(R.string.settings_skip_recap),
                checked = state.skipRecapEnabled,
                onToggle = { onAction(DeviceSettingsActions.ToggleSkipRecap) },
            )
        }
        item {
            LocalToggleItem(
                label = stringResource(R.string.settings_skip_credits),
                checked = state.skipCreditsEnabled,
                onToggle = { onAction(DeviceSettingsActions.ToggleSkipCredits) },
            )
        }

        // Debug section
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Text(
                text = stringResource(R.string.settings_debug_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            LocalToggleItem(
                label = stringResource(R.string.settings_debug_overlay),
                checked = state.debugOverlayEnabled,
                onToggle = { onAction(DeviceSettingsActions.ToggleDebugOverlay) },
            )
        }
    }
}

@Composable
private fun LocalToggleItem(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
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
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun DeviceInfoCard(device: DeviceUi) {
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
                text = stringResource(R.string.device_settings_name, device.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.device_settings_hardware, device.hardware),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.device_settings_software, device.software),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
