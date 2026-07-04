package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.EpisodeContextMenuDialog
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState

@Composable
internal fun PlayerSettingsPanels(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
) {
    var episodeContextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }

    AudioSubtitlesPanel(
        visible = content.activePanel == ActivePanel.AudioSubtitles,
        soundModes = content.soundModes,
        selectedSoundModeIndex = content.selectedSoundModeIndex,
        audioTracks = content.audioTracks,
        selectedAudioTrackIndex = content.selectedAudioTrackIndex,
        subtitleTracks = content.subtitleTracks,
        selectedSubtitleIndex = content.selectedSubtitleIndex,
        onSoundModeSelected = rememberIndexedAction(onAction, PlayerAction::SelectSoundMode),
        onAudioTrackSelected = rememberIndexedAction(onAction, PlayerAction::SelectAudioTrack),
        onSubtitleSelected = rememberIndexedAction(onAction, PlayerAction::SelectSubtitle),
        onSubtitleSizeClick = rememberAction(onAction, PlayerAction.CycleSubtitleSize),
        onBackPressed = rememberAction(onAction, PlayerAction.ClosePanel),
    )

    VideoSettingsPanel(
        visible = content.activePanel == ActivePanel.VideoSettings,
        qualities = content.qualities,
        selectedQualityIndex = content.selectedQualityIndex,
        speeds = content.speeds,
        selectedSpeedIndex = content.selectedSpeedIndex,
        aspectRatios = content.aspectRatios,
        selectedAspectRatioIndex = content.selectedAspectRatioIndex,
        bufferPresets = content.bufferPresets,
        selectedBufferPresetIndex = content.selectedBufferPresetIndex,
        onQualitySelected = rememberIndexedAction(onAction, PlayerAction::SelectQuality),
        onSpeedSelected = rememberIndexedAction(onAction, PlayerAction::SelectSpeed),
        onAspectRatioSelected = rememberIndexedAction(onAction, PlayerAction::SelectAspectRatio),
        onBufferPresetSelected = rememberIndexedAction(onAction, PlayerAction::SelectBufferPreset),
        fastDnsEnabled = content.fastDnsEnabled,
        onToggleFastDns = rememberAction(onAction, PlayerAction.ToggleFastDns),
        onBackPressed = rememberAction(onAction, PlayerAction.ClosePanel),
    )

    EpisodesPanel(
        visible = content.activePanel == ActivePanel.Episodes,
        episodes = content.episodes,
        onEpisodeSelected = { item -> onAction(PlayerAction.SelectEpisodeById(item.id)) },
        onEpisodeContextMenu = { episodeContextMenuItem = it },
        onBackPressed = rememberAction(onAction, PlayerAction.ClosePanel),
        allowFocusExit = episodeContextMenuItem != null,
    )

    EpisodeContextMenuDialog(
        episode = episodeContextMenuItem,
        onDismiss = { episodeContextMenuItem = null },
        onPlay = { onAction(PlayerAction.SelectEpisodeById(it.id)) },
        onMarkEpisodeWatched = { item, watched -> onAction(PlayerAction.EpisodeWatchedChanged(item, watched)) },
        onMarkSeasonWatched = { item, watched -> onAction(PlayerAction.SeasonWatchedChanged(item, watched)) },
    )
}

@Composable
private fun rememberIndexedAction(
    onAction: (UIAction) -> Unit,
    actionFactory: (Int) -> UIAction,
): (Int) -> Unit {
    return remember(onAction, actionFactory) {
        { index -> onAction(actionFactory(index)) }
    }
}
