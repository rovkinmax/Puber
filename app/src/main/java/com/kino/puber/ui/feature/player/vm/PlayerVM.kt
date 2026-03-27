package com.kino.puber.ui.feature.player.vm

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.kino.puber.R
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SeekIndicatorState
import com.kino.puber.ui.feature.player.model.SoundModeUIState
import com.kino.puber.ui.feature.player.model.SubtitleSize
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@UnstableApi
internal class PlayerVM(
    router: AppRouter,
    private val context: Context,
    private val params: PlayerScreenParams,
    private val mapper: PlayerUIMapper,
    private val interactor: PlayerInteractor,
) : PuberVM<PlayerViewState>(router) {

    override val initialViewState: PlayerViewState = PlayerViewState.Loading

    private var exoPlayer: ExoPlayer? = null
    private var currentItem: Item? = null
    private var currentFiles: List<VideoFile>? = null
    private var currentAudios: List<Audio>? = null
    private var currentSubtitles: List<SubtitleLink>? = null
    private var currentVideoId: Int? = null
    private var currentSeasonNumber: Int? = null
    private var currentEpisodeNumber: Int? = null
    private var currentDuration: Int? = null

    private var controlsHideJob: Job? = null
    private var seekIndicatorHideJob: Job? = null
    private var progressSyncJob: Job? = null
    private var countdownJob: Job? = null

    private var lastSeekTime = 0L
    private var seekStepIndex = 0
    private val seekSteps = intArrayOf(10, 10, 20, 30, 60, 60)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> onPlaybackEnded()
                Player.STATE_READY -> {
                    updatePlaybackState()
                    updateTracksFromPlayer()
                }
                Player.STATE_BUFFERING -> updatePlaybackState()
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            updateViewState(PlayerViewState.Error(error.localizedMessage ?: context.getString(R.string.player_error_playback)))
        }
    }

    override fun onStart() {
        loadContent()
    }

    private fun loadContent() {
        launch {
            val item = interactor.getItemDetails(params.itemId)
            currentItem = item
            val isSeries = mapper.isSeriesType(item.type)

            var seasonNumber = params.seasonNumber
            var episodeNumber = params.episodeNumber

            if (isSeries && seasonNumber == null) {
                val firstUnwatched = mapper.findFirstUnwatchedEpisode(item)
                seasonNumber = firstUnwatched?.first
                episodeNumber = firstUnwatched?.second
            }

            currentSeasonNumber = seasonNumber
            currentEpisodeNumber = episodeNumber

            // For series: data comes from Episode; for movies: from Video
            val files: List<VideoFile>?
            val audios: List<Audio>?
            val subtitles: List<SubtitleLink>?
            val watchingTime: Int?
            val duration: Int?
            val videoId: Int?
            val episodeTitle: String?

            if (isSeries) {
                val episode = mapper.findEpisode(item, seasonNumber ?: 1, episodeNumber ?: 1)
                files = episode?.files
                audios = episode?.audios
                subtitles = episode?.subtitles
                watchingTime = episode?.watching?.time
                duration = episode?.duration
                videoId = episode?.id
                episodeTitle = episode?.title
            } else {
                val video = mapper.findVideoForMovie(item)
                files = video?.files
                audios = video?.audios
                subtitles = video?.subtitles
                watchingTime = video?.watching?.time
                duration = video?.duration
                videoId = video?.id
                episodeTitle = null
            }

            currentFiles = files
            currentAudios = audios
            currentSubtitles = subtitles
            currentVideoId = videoId
            currentDuration = duration

            val savedPosition = watchingTime?.toLong()?.times(1000) ?: 0L
            val resumeDialog = if (savedPosition > 0) {
                ResumeDialogState(
                    savedPosition = savedPosition,
                    formattedTime = mapper.formatTime(savedPosition),
                )
            } else null

            val hasNext = if (isSeries && seasonNumber != null && episodeNumber != null) {
                mapper.findNextEpisode(item, seasonNumber, episodeNumber) != null
            } else false

            val subtitleSize = interactor.getSubtitleSize()

            val contentState = PlayerContentState(
                title = mapper.buildTitle(item, seasonNumber, episodeNumber),
                subtitle = mapper.buildSubtitle(item, seasonNumber, episodeNumber, episodeTitle),
                isPlaying = false,
                currentPosition = 0L,
                duration = duration?.toLong()?.times(1000) ?: 0L,
                bufferedPosition = 0L,
                controlsVisible = resumeDialog == null,
                controlsFocusTarget = if (resumeDialog == null) FocusTarget.Buttons else null,
                activePanel = ActivePanel.None,
                seekIndicator = null,
                audioTracks = mapper.mapAudioTracks(audios),
                selectedAudioTrackIndex = 0,
                subtitleTracks = mapper.mapSubtitleTracks(subtitles),
                selectedSubtitleIndex = 0,
                soundModes = listOf(SoundModeUIState(0, "Стерео 2.0")),
                selectedSoundModeIndex = 0,
                subtitleSize = subtitleSize,
                qualities = mapper.mapQualities(files),
                selectedQualityIndex = 0,
                speeds = PlayerUIMapper.SPEEDS,
                selectedSpeedIndex = PlayerUIMapper.DEFAULT_SPEED_INDEX,
                aspectRatios = PlayerUIMapper.ASPECT_RATIOS,
                selectedAspectRatioIndex = PlayerUIMapper.DEFAULT_ASPECT_RATIO_INDEX,
                isMovie = !isSeries,
                hasNextEpisode = hasNext,
                nextEpisodeCountdown = null,
                resumeDialog = resumeDialog,
                episodes = if (isSeries) mapper.mapEpisodes(item) else null,
                currentEpisodeId = videoId,
            )

            updateViewState(PlayerViewState.Content(contentState))

            initializePlayer(savedPosition = if (resumeDialog != null) null else 0L)
            startProgressSync()
            if (resumeDialog == null) {
                scheduleControlsHide()
            }

            restoreTrackPreferences()
        }
    }

    private fun initializePlayer(savedPosition: Long?) {
        val qualityIndex = (stateValue as? PlayerViewState.Content)?.content?.selectedQualityIndex ?: 0
        val streamUrl = mapper.selectStreamUrl(currentFiles, qualityIndex) ?: return

        val player = ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
        exoPlayer = player

        val mediaItem = buildMediaItem(streamUrl)
        if (streamUrl.contains(".m3u8") || streamUrl.contains("hls")) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource)
        } else {
            player.setMediaItem(mediaItem)
        }

        player.prepare()
        if (savedPosition != null) {
            if (savedPosition > 0) {
                player.seekTo(savedPosition)
            }
            player.playWhenReady = true
        }
        // When savedPosition is null, player is prepared but paused (resume dialog shown)
    }

    private fun buildMediaItem(streamUrl: String): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl)
        val subtitles = currentSubtitles
        if (!subtitles.isNullOrEmpty()) {
            val subtitleConfigs = subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(sub.lang)
                    .setLabel(sub.lang)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }
        return builder.build()
    }

    private fun switchStreamUrl(qualityIndex: Int) {
        val player = exoPlayer ?: return
        val streamUrl = mapper.selectStreamUrl(currentFiles, qualityIndex) ?: return
        val savedPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        player.stop()

        val mediaItem = buildMediaItem(streamUrl)
        if (streamUrl.contains(".m3u8") || streamUrl.contains("hls")) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource)
        } else {
            player.setMediaItem(mediaItem)
        }

        player.prepare()
        player.seekTo(savedPosition)
        player.playWhenReady = wasPlaying
    }

    private fun restoreTrackPreferences() {
        val audioTrackId = interactor.getPreferredAudioTrackId(params.itemId)
        val subtitleLang = interactor.getPreferredSubtitleLang(params.itemId)

        if (audioTrackId != null) {
            val audioIndex = (stateValue as? PlayerViewState.Content)?.content?.audioTracks
                ?.indexOfFirst { it.index == audioTrackId } ?: -1
            if (audioIndex >= 0) {
                applyAudioTrackSelection(audioIndex)
            }
        }
        if (subtitleLang != null) {
            val subIndex = (stateValue as? PlayerViewState.Content)?.content?.subtitleTracks
                ?.indexOfFirst { it.language == subtitleLang } ?: -1
            if (subIndex >= 0) {
                applySubtitleSelection(subIndex)
            }
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is PlayerAction.TogglePlayPause -> togglePlayPause()
            is PlayerAction.SeekForward -> seekForward()
            is PlayerAction.SeekBackward -> seekBackward()
            is PlayerAction.ShowControls -> showControls(action.focusTarget)
            is PlayerAction.HideControls -> hideControls()
            is PlayerAction.ResetControlsTimer -> scheduleControlsHide()
            is PlayerAction.OpenAudioSubtitlesPanel -> openPanel(ActivePanel.AudioSubtitles)
            is PlayerAction.OpenVideoSettingsPanel -> openPanel(ActivePanel.VideoSettings)
            is PlayerAction.OpenEpisodesPanel -> openPanel(ActivePanel.Episodes)
            is PlayerAction.ClosePanel -> closePanel()
            is PlayerAction.SelectAudioTrack -> applyAudioTrackSelection(action.index)
            is PlayerAction.SelectSubtitle -> applySubtitleSelection(action.index)
            is PlayerAction.SelectSoundMode -> selectSoundMode(action.index)
            is PlayerAction.CycleSubtitleSize -> cycleSubtitleSize()
            is PlayerAction.SelectQuality -> selectQuality(action.index)
            is PlayerAction.SelectSpeed -> selectSpeed(action.index)
            is PlayerAction.SelectAspectRatio -> selectAspectRatio(action.index)
            is PlayerAction.SelectEpisode -> switchEpisode(action.seasonNumber, action.episodeNumber)
            is PlayerAction.SelectEpisodeById -> selectEpisodeById(action.episodeId)
            is PlayerAction.NextEpisode -> playNextEpisode()
            is PlayerAction.CancelNextEpisodeCountdown -> cancelCountdown()
            is PlayerAction.ResumeFromPosition -> resumeFromSavedPosition()
            is PlayerAction.StartFromBeginning -> startFromBeginning()
            is PlayerAction.RetryPlayback -> retryPlayback()
            is PlayerAction.OnBackPressed -> handleBack()
            else -> super.onAction(action)
        }
    }

    private fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            saveCurrentPosition()
        } else {
            player.play()
        }
    }

    private fun seekForward() {
        val player = exoPlayer ?: return
        val step = getSeekStep()
        val newPosition = (player.currentPosition + step * 1000).coerceAtMost(player.duration)
        player.seekTo(newPosition)
        showSeekIndicator(isForward = true, stepSeconds = step, targetPosition = newPosition)
        updatePlaybackState()
    }

    private fun seekBackward() {
        val player = exoPlayer ?: return
        val step = getSeekStep()
        val newPosition = (player.currentPosition - step * 1000).coerceAtLeast(0)
        player.seekTo(newPosition)
        showSeekIndicator(isForward = false, stepSeconds = step, targetPosition = newPosition)
        updatePlaybackState()
    }

    private fun getSeekStep(): Int {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime > SEEK_RESET_TIMEOUT_MS) {
            seekStepIndex = 0
        } else if (seekStepIndex < seekSteps.size - 1) {
            seekStepIndex++
        }
        lastSeekTime = now
        return seekSteps[seekStepIndex]
    }

    private fun showSeekIndicator(isForward: Boolean, stepSeconds: Int, targetPosition: Long) {
        val offsetText = if (isForward) {
            context.getString(R.string.player_seek_forward, stepSeconds)
        } else {
            context.getString(R.string.player_seek_backward, stepSeconds)
        }
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(
                content.copy(
                    seekIndicator = SeekIndicatorState(
                        isForward = isForward,
                        offsetText = offsetText,
                        targetTimeText = mapper.formatTime(targetPosition),
                    )
                )
            )
        }
        seekIndicatorHideJob?.cancel()
        seekIndicatorHideJob = launch {
            delay(SEEK_INDICATOR_HIDE_DELAY_MS)
            updateViewState<PlayerViewState.Content> {
                PlayerViewState.Content(content.copy(seekIndicator = null))
            }
        }
    }

    private fun showControls(focusTarget: FocusTarget) {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(controlsVisible = true, controlsFocusTarget = focusTarget))
        }
        scheduleControlsHide()
    }

    private fun hideControls() {
        controlsHideJob?.cancel()
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(controlsVisible = false, controlsFocusTarget = null))
        }
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = launch {
            delay(CONTROLS_HIDE_DELAY_MS)
            val state = stateValue
            if (state is PlayerViewState.Content && state.content.activePanel == ActivePanel.None) {
                updateViewState(PlayerViewState.Content(state.content.copy(controlsVisible = false)))
            }
        }
    }

    private var lastPanelOpener: FocusTarget = FocusTarget.Buttons
    private var wasPlayingBeforePanel = false

    private fun openPanel(panel: ActivePanel) {
        controlsHideJob?.cancel()

        // Track which button opened the panel for focus return
        lastPanelOpener = when (panel) {
            ActivePanel.Episodes -> FocusTarget.EpisodesButton
            ActivePanel.AudioSubtitles -> FocusTarget.AudioSubtitlesButton
            ActivePanel.VideoSettings -> FocusTarget.VideoSettingsButton
            ActivePanel.None -> FocusTarget.Buttons
        }

        // Pause playback when opening episodes panel
        if (panel == ActivePanel.Episodes) {
            wasPlayingBeforePanel = exoPlayer?.isPlaying == true
            exoPlayer?.pause()
        }

        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(activePanel = panel, controlsVisible = false))
        }
    }

    private fun closePanel() {
        val closingPanel = (stateValue as? PlayerViewState.Content)?.content?.activePanel

        // Resume playback if was playing before episodes panel
        if (closingPanel == ActivePanel.Episodes && wasPlayingBeforePanel) {
            exoPlayer?.play()
        }

        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(
                activePanel = ActivePanel.None,
                controlsVisible = true,
                controlsFocusTarget = lastPanelOpener,
            ))
        }
        scheduleControlsHide()
    }

    private fun updateTracksFromPlayer() {
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        if (audioGroups.isNotEmpty()) {
            val audioTracks = audioGroups.mapIndexed { index, group ->
                val format = group.getTrackFormat(0)
                val label = format.label ?: format.language ?: "Track ${index + 1}"
                AudioTrackUIState(
                    index = index,
                    label = label,
                    language = format.language ?: "",
                )
            }
            // Find currently selected audio group
            val selectedIndex = audioGroups.indexOfFirst { it.isSelected }.coerceAtLeast(0)

            updateViewState<PlayerViewState.Content> {
                PlayerViewState.Content(content.copy(
                    audioTracks = audioTracks,
                    selectedAudioTrackIndex = selectedIndex,
                ))
            }
        }
    }

    private fun applyAudioTrackSelection(index: Int) {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(selectedAudioTrackIndex = index))
        }
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val targetGroup = audioGroups.getOrNull(index) ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(targetGroup.mediaTrackGroup, 0)
            )
            .build()
        saveTrackPreferences()
    }

    private fun applySubtitleSelection(index: Int) {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(selectedSubtitleIndex = index))
        }
        val player = exoPlayer ?: return
        if (index == 0) {
            // "Off" — disable all text tracks
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            // Select specific subtitle track (index-1 because 0 = "Off")
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            val targetGroup = textGroups.getOrNull(index - 1)

            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .apply {
                    if (targetGroup != null) {
                        setOverrideForType(
                            TrackSelectionOverride(targetGroup.mediaTrackGroup, 0)
                        )
                    }
                }
                .build()
        }
        saveTrackPreferences()
    }

    private fun selectSoundMode(index: Int) {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(selectedSoundModeIndex = index))
        }
    }

    private fun cycleSubtitleSize() {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        val newSize = when (currentState.subtitleSize) {
            SubtitleSize.SMALL -> SubtitleSize.MEDIUM
            SubtitleSize.MEDIUM -> SubtitleSize.LARGE
            SubtitleSize.LARGE -> SubtitleSize.SMALL
        }
        interactor.saveSubtitleSize(newSize)
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(subtitleSize = newSize))
        }
    }

    private fun selectQuality(index: Int) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (currentState.selectedQualityIndex == index) return

        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(selectedQualityIndex = index))
        }
        switchStreamUrl(index)
    }

    private fun selectSpeed(index: Int) {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(selectedSpeedIndex = index))
        }
        val speed = PlayerUIMapper.SPEEDS.getOrNull(index)?.speed ?: 1.0f
        exoPlayer?.setPlaybackSpeed(speed)
    }

    private fun selectAspectRatio(index: Int) {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(selectedAspectRatioIndex = index))
        }
        // Aspect ratio is applied in PlayerScreenContent via PlayerView.resizeMode
    }

    private fun selectEpisodeById(episodeId: Int) {
        val item = currentItem ?: return
        val seasons = item.seasons ?: return
        for (season in seasons) {
            val episode = season.episodes?.find { it.id == episodeId }
            if (episode != null) {
                closePanel()
                switchEpisode(season.number, episode.number)
                return
            }
        }
    }

    private fun switchEpisode(seasonNumber: Int, episodeNumber: Int) {
        saveCurrentPosition()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        countdownJob?.cancel()
        markedWatched = false

        currentSeasonNumber = seasonNumber
        currentEpisodeNumber = episodeNumber

        updateViewState(PlayerViewState.Loading)
        launch {
            val item = interactor.getItemDetails(params.itemId)
            currentItem = item

            val episode = mapper.findEpisode(item, seasonNumber, episodeNumber)
            currentFiles = episode?.files
            currentAudios = episode?.audios
            currentSubtitles = episode?.subtitles
            currentVideoId = episode?.id
            currentDuration = episode?.duration

            val hasNext = mapper.findNextEpisode(item, seasonNumber, episodeNumber) != null
            val subtitleSize = interactor.getSubtitleSize()

            val contentState = PlayerContentState(
                title = mapper.buildTitle(item, seasonNumber, episodeNumber),
                subtitle = mapper.buildSubtitle(item, seasonNumber, episodeNumber, episode?.title),
                isPlaying = false,
                currentPosition = 0L,
                duration = episode?.duration?.toLong()?.times(1000) ?: 0L,
                bufferedPosition = 0L,
                controlsVisible = false,
                controlsFocusTarget = null,
                activePanel = ActivePanel.None,
                seekIndicator = null,
                audioTracks = mapper.mapAudioTracks(episode?.audios),
                selectedAudioTrackIndex = 0,
                subtitleTracks = mapper.mapSubtitleTracks(episode?.subtitles),
                selectedSubtitleIndex = 0,
                soundModes = listOf(SoundModeUIState(0, "Стерео 2.0")),
                selectedSoundModeIndex = 0,
                subtitleSize = subtitleSize,
                qualities = mapper.mapQualities(episode?.files),
                selectedQualityIndex = 0,
                speeds = PlayerUIMapper.SPEEDS,
                selectedSpeedIndex = PlayerUIMapper.DEFAULT_SPEED_INDEX,
                aspectRatios = PlayerUIMapper.ASPECT_RATIOS,
                selectedAspectRatioIndex = PlayerUIMapper.DEFAULT_ASPECT_RATIO_INDEX,
                isMovie = false,
                hasNextEpisode = hasNext,
                nextEpisodeCountdown = null,
                resumeDialog = null,
                episodes = mapper.mapEpisodes(item),
                currentEpisodeId = episode?.id,
            )
            updateViewState(PlayerViewState.Content(contentState))
            initializePlayer(savedPosition = 0L)
            startProgressSync()
        }
    }

    private fun playNextEpisode() {
        val item = currentItem ?: return
        val season = currentSeasonNumber ?: return
        val episode = currentEpisodeNumber ?: return
        val next = mapper.findNextEpisode(item, season, episode) ?: return
        switchEpisode(next.first, next.second)
    }

    private fun onPlaybackEnded() {
        val state = stateValue as? PlayerViewState.Content ?: return
        if (!state.content.isMovie && state.content.hasNextEpisode) {
            markCurrentAsWatched()
            startNextEpisodeCountdown()
        } else if (!state.content.isMovie) {
            markCurrentAsWatched()
            updateViewState(PlayerViewState.Content(state.content.copy(controlsVisible = true)))
        } else {
            markCurrentAsWatched()
        }
    }

    private fun startNextEpisodeCountdown() {
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(nextEpisodeCountdown = NEXT_EPISODE_COUNTDOWN_SEC))
        }
        countdownJob?.cancel()
        countdownJob = launch {
            for (i in NEXT_EPISODE_COUNTDOWN_SEC downTo 0) {
                updateViewState<PlayerViewState.Content> {
                    PlayerViewState.Content(content.copy(nextEpisodeCountdown = i))
                }
                if (i == 0) {
                    playNextEpisode()
                    return@launch
                }
                delay(1000)
            }
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(nextEpisodeCountdown = null))
        }
    }

    private fun resumeFromSavedPosition() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val position = state.resumeDialog?.savedPosition ?: 0L
        exoPlayer?.seekTo(position)
        exoPlayer?.playWhenReady = true
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(resumeDialog = null))
        }
        scheduleControlsHide()
    }

    private fun startFromBeginning() {
        exoPlayer?.seekTo(0)
        exoPlayer?.playWhenReady = true
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(content.copy(resumeDialog = null))
        }
        scheduleControlsHide()
    }

    private fun retryPlayback() {
        updateViewState(PlayerViewState.Loading)
        exoPlayer?.release()
        exoPlayer = null
        loadContent()
    }

    private fun handleBack() {
        handleBackAndCheckExit()
    }

    override fun onBackPressed() {
        val navigatedAway = handleBackAndCheckExit()
        if (!navigatedAway) {
            // Re-register: dispatchBackPressed() removes us from the stack,
            // but we need to stay registered for subsequent BACK presses
            // (closing panels, hiding controls, etc.)
            router.addBackDispatcher(this)
        }
    }

    private fun handleBackAndCheckExit(): Boolean {
        val state = stateValue as? PlayerViewState.Content ?: run {
            saveCurrentPosition()
            router.back()
            return true
        }
        when {
            state.content.nextEpisodeCountdown != null -> cancelCountdown()
            state.content.activePanel != ActivePanel.None -> closePanel()
            state.content.controlsVisible -> hideControls()
            else -> {
                saveCurrentPosition()
                router.back()
                return true
            }
        }
        return false
    }

    private fun updatePlaybackState() {
        val player = exoPlayer ?: return
        updateViewState<PlayerViewState.Content> {
            PlayerViewState.Content(
                content.copy(
                    isPlaying = player.isPlaying,
                    currentPosition = player.currentPosition,
                    duration = player.duration.coerceAtLeast(0),
                    bufferedPosition = player.bufferedPosition,
                )
            )
        }
        checkAutoMarkWatched()
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = launch {
            while (isActive) {
                delay(PROGRESS_SYNC_INTERVAL_MS)
                val player = exoPlayer ?: continue
                if (player.isPlaying) {
                    updatePlaybackState()
                    saveCurrentPosition()
                }
            }
        }
    }

    private fun saveCurrentPosition() {
        val player = exoPlayer ?: return
        val videoId = currentVideoId ?: return
        val timeSeconds = (player.currentPosition / 1000).toInt()
        launch {
            interactor.saveWatchingTime(
                id = params.itemId,
                videoId = videoId,
                time = timeSeconds,
                season = currentSeasonNumber,
            )
        }
    }

    private fun checkAutoMarkWatched() {
        val player = exoPlayer ?: return
        if (player.duration <= 0) return
        val remaining = player.duration - player.currentPosition
        val threshold = player.duration * AUTO_MARK_WATCHED_THRESHOLD
        if (remaining < threshold) {
            markCurrentAsWatched()
        }
    }

    private var markedWatched = false

    private fun markCurrentAsWatched() {
        if (markedWatched) return
        markedWatched = true
        val videoId = currentVideoId
        launch {
            interactor.markAsWatched(
                id = params.itemId,
                season = currentSeasonNumber,
                videoId = videoId,
            )
        }
    }

    private fun saveTrackPreferences() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val audioTrack = state.audioTracks.getOrNull(state.selectedAudioTrackIndex)
        val subtitle = state.subtitleTracks.getOrNull(state.selectedSubtitleIndex)
        interactor.saveTrackPreferences(
            itemId = params.itemId,
            audioTrackId = audioTrack?.index,
            subtitleLang = subtitle?.language?.takeIf { it.isNotEmpty() },
        )
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    override fun onCleared() {
        saveCurrentPosition()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        controlsHideJob?.cancel()
        seekIndicatorHideJob?.cancel()
        progressSyncJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val CONTROLS_HIDE_DELAY_MS = 3000L
        const val SEEK_INDICATOR_HIDE_DELAY_MS = 1500L
        const val SEEK_RESET_TIMEOUT_MS = 1500L
        const val PROGRESS_SYNC_INTERVAL_MS = 30_000L
        const val NEXT_EPISODE_COUNTDOWN_SEC = 7
        const val AUTO_MARK_WATCHED_THRESHOLD = 0.05
    }
}
