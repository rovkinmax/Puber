package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.player.ResolvedMedia
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SoundModeUIState

internal class ContentStateFactory(private val mapper: PlayerUIMapper) {

    fun build(
        item: Item,
        resolved: ResolvedMedia,
        resumeDialog: ResumeDialogState?,
        subtitleSize: SubtitleSize,
    ): PlayerContentState = PlayerContentState(
        title = mapper.buildTitle(item, resolved.seasonNumber, resolved.episodeNumber),
        subtitle = mapper.buildSubtitle(item, resolved.seasonNumber, resolved.episodeNumber, resolved.episodeTitle),
        isPlaying = resumeDialog == null,
        currentPosition = 0L,
        duration = resolved.duration?.toLong()?.times(1000) ?: 0L,
        bufferedPosition = 0L,
        controlsVisible = resumeDialog == null,
        controlsFocusTarget = if (resumeDialog == null) FocusTarget.Buttons else null,
        activePanel = ActivePanel.None,
        seekIndicator = null,
        playPauseIndicator = null,
        audioTracks = mapper.mapAudioTracks(resolved.audios),
        selectedAudioTrackIndex = 0,
        subtitleTracks = mapper.mapSubtitleTracks(resolved.subtitles),
        selectedSubtitleIndex = 0,
        soundModes = listOf(SoundModeUIState(0, mapper.defaultSoundModeLabel())),
        selectedSoundModeIndex = 0,
        subtitleSize = subtitleSize,
        qualities = mapper.mapQualities(resolved.files),
        selectedQualityIndex = 0,
        speeds = PlayerUIMapper.SPEEDS,
        selectedSpeedIndex = PlayerUIMapper.DEFAULT_SPEED_INDEX,
        aspectRatios = PlayerUIMapper.ASPECT_RATIOS,
        selectedAspectRatioIndex = PlayerUIMapper.DEFAULT_ASPECT_RATIO_INDEX,
        isMovie = !resolved.isSeries,
        hasNextEpisode = resolved.hasNext,
        hasPreviousEpisode = resolved.hasPrevious,
        nextEpisodeCountdown = null,
        resumeDialog = resumeDialog,
        episodes = if (resolved.isSeries) mapper.mapEpisodes(item) else null,
        currentEpisodeId = resolved.episodeId,
    )
}
