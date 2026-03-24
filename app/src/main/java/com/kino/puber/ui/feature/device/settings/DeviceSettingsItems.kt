package com.kino.puber.ui.feature.device.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.theme.highlightOnFocus
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi

@Composable
internal fun SettingSwitchItem(
    setting: DeviceSettingUIModel.TypeValue,
    isSaving: Boolean = false,
    onToggle: () -> Unit,
) {
    if (!setting.supported) {
        UnsupportedSettingItem(label = setting.label)
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box {
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
                enabled = !isSaving,
            )
        }

        if (isSaving) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
internal fun SettingListItem(
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

@Composable
private fun UnsupportedSettingItem(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.device_settings_not_supported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
