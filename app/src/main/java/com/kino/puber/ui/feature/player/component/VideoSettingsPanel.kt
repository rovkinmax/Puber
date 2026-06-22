package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.kino.puber.core.ui.uikit.theme.highlightOnFocus
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.AspectRatioUIState
import com.kino.puber.ui.feature.player.model.BufferPresetUIState
import com.kino.puber.ui.feature.player.model.QualityUIState
import com.kino.puber.ui.feature.player.model.SpeedUIState

@Composable
internal fun VideoSettingsPanel(
    visible: Boolean,
    qualities: List<QualityUIState>,
    selectedQualityIndex: Int,
    speeds: List<SpeedUIState>,
    selectedSpeedIndex: Int,
    aspectRatios: List<AspectRatioUIState>,
    selectedAspectRatioIndex: Int,
    bufferPresets: List<BufferPresetUIState>,
    selectedBufferPresetIndex: Int,
    fastDnsEnabled: Boolean,
    onQualitySelected: (Int) -> Unit,
    onSpeedSelected: (Int) -> Unit,
    onAspectRatioSelected: (Int) -> Unit,
    onBufferPresetSelected: (Int) -> Unit,
    onToggleFastDns: () -> Unit,
    onBackPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        VideoSettingsPanelContainer {
            VideoSettingsColumns(
                state = VideoSettingsPanelState(
                    qualities = qualities,
                    selectedQualityIndex = selectedQualityIndex,
                    speeds = speeds,
                    selectedSpeedIndex = selectedSpeedIndex,
                    aspectRatios = aspectRatios,
                    selectedAspectRatioIndex = selectedAspectRatioIndex,
                    bufferPresets = bufferPresets,
                    selectedBufferPresetIndex = selectedBufferPresetIndex,
                ),
                panelFocusRequester = rememberRequestingFocusRequester(),
                actions = VideoSettingsActions(
                    onQualitySelected = onQualitySelected,
                    onSpeedSelected = onSpeedSelected,
                    onAspectRatioSelected = onAspectRatioSelected,
                    onBufferPresetSelected = onBufferPresetSelected,
                ),
            )
            FastDnsToggle(checked = fastDnsEnabled, onToggle = onToggleFastDns)
        }
    }
}

private data class VideoSettingsPanelState(
    val qualities: List<QualityUIState>,
    val selectedQualityIndex: Int,
    val speeds: List<SpeedUIState>,
    val selectedSpeedIndex: Int,
    val aspectRatios: List<AspectRatioUIState>,
    val selectedAspectRatioIndex: Int,
    val bufferPresets: List<BufferPresetUIState>,
    val selectedBufferPresetIndex: Int,
)

private data class VideoSettingsActions(
    val onQualitySelected: (Int) -> Unit,
    val onSpeedSelected: (Int) -> Unit,
    val onAspectRatioSelected: (Int) -> Unit,
    val onBufferPresetSelected: (Int) -> Unit,
)

@Composable
private fun VideoSettingsPanelContainer(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(horizontal = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.VideoSettingsColumns(
    state: VideoSettingsPanelState,
    panelFocusRequester: FocusRequester,
    actions: VideoSettingsActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QualityColumn(state, panelFocusRequester, actions.onQualitySelected)
        SettingsPanelColumn(
            header = stringResource(R.string.player_panel_speed),
            items = remember(state.speeds) { state.speeds.map { it.label } },
            selectedIndex = state.selectedSpeedIndex,
            onItemSelected = actions.onSpeedSelected,
            modifier = Modifier.weight(1f),
        )
        SettingsPanelColumn(
            header = stringResource(R.string.player_panel_aspect_ratio),
            items = remember(state.aspectRatios) { state.aspectRatios.map { it.label } },
            selectedIndex = state.selectedAspectRatioIndex,
            onItemSelected = actions.onAspectRatioSelected,
            modifier = Modifier.weight(1f),
        )
        SettingsPanelColumn(
            header = stringResource(R.string.player_panel_buffer),
            items = remember(state.bufferPresets) { state.bufferPresets.map { it.label } },
            selectedIndex = state.selectedBufferPresetIndex,
            onItemSelected = actions.onBufferPresetSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.QualityColumn(
    state: VideoSettingsPanelState,
    panelFocusRequester: FocusRequester,
    onQualitySelected: (Int) -> Unit,
) {
    if (state.qualities.isEmpty()) return
    SettingsPanelColumn(
        header = stringResource(R.string.player_panel_quality),
        items = remember(state.qualities) { state.qualities.map { it.label } },
        selectedIndex = state.selectedQualityIndex,
        onItemSelected = onQualitySelected,
        modifier = Modifier.weight(1f),
        firstItemFocusRequester = panelFocusRequester,
    )
}

@Composable
private fun FastDnsToggle(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .highlightOnFocus(isFocused)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.player_fast_dns),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.player_fast_dns_hint),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
