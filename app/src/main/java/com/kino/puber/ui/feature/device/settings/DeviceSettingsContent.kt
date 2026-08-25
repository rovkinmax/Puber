package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorFocusRestored
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorRestorePending
import com.kino.puber.core.ui.navigation.component.PreserveLazyListAnchorOnRootReturn
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.highlightOnFocus
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import kotlinx.coroutines.delay

@Composable
internal fun DeviceSettingsContent(
    state: DeviceSettingsState,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is DeviceSettingsState.Error -> ErrorView(
                error = state.error,
                onRetry = { onAction(CommonAction.RetryClicked) },
                onConfigureApiDomain = { onAction(DeviceSettingsActions.OpenApiDomainDialog) },
            )
            is DeviceSettingsState.Loading -> LoadingView()
            is DeviceSettingsState.Success -> DeviceSettingsList(
                state = state,
                apiDomain = apiDomain,
                onAction = onAction,
                listState = listState,
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
    onConfigureApiDomain: () -> Unit,
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
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onConfigureApiDomain) {
            Text(stringResource(R.string.api_domain_open_action))
        }
    }
}

@Composable
private fun DeviceSettingsList(
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    listState: LazyListState,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val speedTestLauncherFocusRequester = remember { FocusRequester() }
    val rootAnchorRestorePending = LocalRootAnchorRestorePending.current
    val onRootAnchorFocusRestored = LocalRootAnchorFocusRestored.current
    val rootReturnFocusRestorer = if (rootAnchorRestorePending) {
        Modifier.focusRestorer(speedTestLauncherFocusRequester)
    } else {
        Modifier
    }

    PreserveLazyListAnchorOnRootReturn(listState)

    LaunchedEffect(Unit) {
        if (!rootAnchorRestorePending) {
            delay(100)
            initialFocusRequester.requestFocus()
        }
    }

    DeviceSettingsLazyColumn(
        state = state,
        apiDomain = apiDomain,
        onAction = onAction,
        listState = listState,
        initialFocusRequester = initialFocusRequester,
        rootReturnFocusRestorer = rootReturnFocusRestorer,
        speedTestLauncherModifier = Modifier
            .focusRequester(speedTestLauncherFocusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onRootAnchorFocusRestored()
                }
            }
            .testTag(SPEED_TEST_LAUNCHER_TEST_TAG),
    )
}

@Composable
private fun DeviceSettingsLazyColumn(
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    listState: LazyListState,
    initialFocusRequester: FocusRequester,
    rootReturnFocusRestorer: Modifier,
    speedTestLauncherModifier: Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .focusRequester(initialFocusRequester)
            .then(rootReturnFocusRestorer)
            .focusGroup()
            .fillMaxSize()
            .testTag(DEVICE_SETTINGS_LIST_TEST_TAG)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        deviceSettingsHeaderItems(
            device = state.device,
            apiDomain = apiDomain,
            onAction = onAction,
        )
        deviceSettingItems(state, listState, onAction)
        localPreferencesItems(state, onAction)
        skipSegmentsItems(state, onAction)
        navigationModeItems(state, onAction)
        applicationItems(
            state = state,
            speedTestLauncherModifier = speedTestLauncherModifier,
            onAction = onAction,
        )
    }
}

internal const val DEVICE_SETTINGS_LIST_TEST_TAG = "device_settings_list"
internal const val SPEED_TEST_LAUNCHER_TEST_TAG = "speed_test_launcher"
private const val DEVICE_SETTINGS_HEADER_ITEMS_COUNT = 5

private fun LazyListScope.deviceSettingsHeaderItems(
    device: DeviceUi,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
) {
    item {
        Column(modifier = Modifier.selectableGroup()) {
            Text(
                text = stringResource(R.string.device_settings_current_device),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            DeviceInfoCard(device = device)
        }
    }
    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
    item {
        Column {
            Text(
                text = stringResource(R.string.api_domain_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.api_domain_settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        LocalActionItem(
            label = stringResource(R.string.api_domain_open_action),
            value = apiDomain.currentDomain,
            onClick = { onAction(DeviceSettingsActions.OpenApiDomainDialog) },
        )
    }
    item {
        Text(
            text = stringResource(R.string.device_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun LazyListScope.deviceSettingItems(
    state: DeviceSettingsState.Success,
    listState: LazyListState,
    onAction: (UIAction) -> Unit,
) {
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
                lazyItemIndex = DEVICE_SETTINGS_HEADER_ITEMS_COUNT + index,
            )
        }
    }
}

private fun LazyListScope.skipSegmentsItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
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
}

private fun LazyListScope.navigationModeItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
    item {
        Column {
            Text(
                text = stringResource(R.string.settings_navigation_mode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.settings_navigation_restart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        NavigationModeRadioGroup(
            currentMode = state.navigationMode,
            onModeSelected = { mode ->
                onAction(DeviceSettingsActions.ChangeNavigationMode(mode))
            },
        )
    }
}

private fun LazyListScope.applicationItems(
    state: DeviceSettingsState.Success,
    speedTestLauncherModifier: Modifier,
    onAction: (UIAction) -> Unit,
) {
    item {
        LocalActionItem(
            label = stringResource(R.string.speed_test_launcher),
            modifier = speedTestLauncherModifier,
            onClick = { onAction(DeviceSettingsActions.OpenSpeedTest) },
        )
    }

    // App updates section
    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
    item {
        Text(
            text = stringResource(R.string.settings_updates_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_auto_update_check),
            description = stringResource(R.string.settings_auto_update_check_subtitle),
            checked = state.autoUpdateCheckEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleAutoUpdateCheck) },
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

    item {
        Spacer(modifier = Modifier.height(24.dp))
    }
    item {
        TmdbAttribution()
    }
}

@Composable
private fun TmdbAttribution() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.device_settings_tmdb_attribution_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Image(
            painter = painterResource(R.drawable.tmdb_logo_primary_short_blue),
            contentDescription = stringResource(R.string.device_settings_tmdb_logo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(140.dp)
                .height(60.dp),
        )
        Text(
            text = stringResource(R.string.device_settings_tmdb_attribution_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.localPreferencesItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_prefer_surround_audio),
            checked = state.preferSurroundAudio,
            onToggle = { onAction(DeviceSettingsActions.ToggleSurroundAudio) },
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_watched_indicators),
            checked = state.watchedIndicatorsEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleWatchedIndicators) },
        )
    }
    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
    item {
        Text(
            text = stringResource(R.string.settings_content_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_show_cartoons_tab),
            checked = state.showCartoonsTab,
            onToggle = { onAction(DeviceSettingsActions.ToggleCartoonsTab) },
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_show_anime_tab),
            checked = state.showAnimeTab,
            onToggle = { onAction(DeviceSettingsActions.ToggleAnimeTab) },
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_show_anime),
            description = stringResource(R.string.settings_show_anime_description),
            checked = state.showAnime,
            onToggle = { onAction(DeviceSettingsActions.ToggleShowAnime) },
        )
    }
    media3PlaybackItems(state, onAction)
}

private fun LazyListScope.media3PlaybackItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    // Media3 playback section (local-only preferences)
    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
    item {
        Text(
            text = stringResource(R.string.settings_media3_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_discard_embedded_artwork),
            description = stringResource(R.string.settings_discard_embedded_artwork_description),
            checked = state.discardEmbeddedArtworkMetadata,
            onToggle = {
                onAction(DeviceSettingsActions.ToggleDiscardEmbeddedArtworkMetadata)
            },
        )
    }
    item {
        LocalToggleItem(
            label = stringResource(R.string.settings_hagc_playback),
            description = stringResource(R.string.settings_hagc_playback_description),
            checked = state.hagcPlaybackEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleHagcPlayback) },
        )
    }
}

@Composable
private fun LocalActionItem(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .highlightOnFocus(isFocused)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
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
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalToggleItem(
    label: String,
    description: String? = null,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun NavigationModeRadioGroup(
    currentMode: NavigationMode,
    onModeSelected: (NavigationMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        NavigationMode.entries.forEach { mode ->
            val label = when (mode) {
                NavigationMode.SideDrawer -> stringResource(R.string.settings_navigation_drawer)
                NavigationMode.TopTabs -> stringResource(R.string.settings_navigation_top_tabs)
            }
            val isSelected = mode == currentMode
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .highlightOnFocus(isFocused)
                    .selectable(
                        selected = isSelected,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onModeSelected(mode) },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isSelected,
                    onClick = null,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    device: DeviceUi,
    appVersionName: String = BuildConfig.VERSION_NAME,
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
                text = stringResource(R.string.device_settings_name_with_version, device.title, appVersionName),
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
