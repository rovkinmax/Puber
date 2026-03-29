package com.kino.puber.ui.feature.player.vm

import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kino.puber.core.logger.log
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayPauseIndicatorState
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SeekIndicatorState
import com.kino.puber.domain.model.SubtitleSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal class PlayerVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val params: PlayerScreenParams,
    private val mapper: PlayerUIMapper,
    private val interactor: PlayerInteractor,
    private val skipSegmentInteractor: SkipSegmentInteractor,
    private val contentStateFactory: ContentStateFactory,
    private val playbackController: PlaybackController,
) : PuberVM<PlayerViewState>(router) {

    override val initialViewState: PlayerViewState = PlayerViewState.Loading

    override fun dispatchError(error: ErrorEntity) {
        updateViewState(PlayerViewState.Error(error.message))
    }

    private inline fun updateContent(crossinline update: PlayerContentState.() -> PlayerContentState) {
        updateViewState<PlayerViewState.Content> { PlayerViewState.Content(content.update()) }
    }

    private data class CurrentMedia(
        val item: Item,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val videoNumber: Int?,
        val files: List<VideoFile>?,
        val subtitles: List<SubtitleLink>?,
    )

    private var currentMedia: CurrentMedia? = null

    private var controlsHideJob: Job? = null
    private var seekIndicatorHideJob: Job? = null
    private var playPauseHideJob: Job? = null
    private var countdownJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var skipCountdownJob: Job? = null

    private var segments: List<SkipSegment> = emptyList()
    private var creditsSegment: SkipSegment? = null
    private var dismissedSegmentType: SkipSegmentType? = null
    private var lastPositionMs: Long = 0L

    private val seekHandler = SeekHandler()
    private val controlsStateMachine = ControlsStateMachine()
    private val progressTracker = ProgressTracker()
    private val debugOverlayEnabled = interactor.isDebugOverlayEnabled()

    private val playbackCallback = object : PlaybackController.Callback {
        override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean, position: Long, duration: Long, buffered: Long) {
            val wasBuffering = (stateValue as? PlayerViewState.Content)?.content?.isBuffering == true
            updateContent {
                copy(isPlaying = isPlaying, isBuffering = isBuffering)
            }
            if (isBuffering && !wasBuffering) {
                controlsHideJob?.cancel()
            } else if (!isBuffering && wasBuffering) {
                val controlsVisible = (stateValue as? PlayerViewState.Content)?.content?.controlsVisible == true
                if (controlsVisible) scheduleControlsHide()
            }
        }

        override fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int) {
            updateContent {
                copy(
                    audioTracks = audioTracks,
                    selectedAudioTrackIndex = selectedIndex,
                )
            }
        }

        override fun onPlaybackEnded() {
            this@PlayerVM.onPlaybackEnded()
        }

        override fun onError(message: String) {
            updateViewState(PlayerViewState.Error(message))
        }
    }

    override fun onStart() {
        playbackController.setCallback(playbackCallback)
        loadContent()
    }

    private fun loadContent() {
        launch { preparePlayback(params.seasonNumber, params.episodeNumber) }
    }

    private suspend fun preparePlayback(seasonNumber: Int?, episodeNumber: Int?, forceFromBeginning: Boolean = false) {
        val item = interactor.getItemDetails(params.itemId)
        val resolved = interactor.resolveMedia(item, seasonNumber, episodeNumber)

        currentMedia = CurrentMedia(
            item = item,
            seasonNumber = resolved.seasonNumber,
            episodeNumber = resolved.episodeNumber,
            videoNumber = resolved.videoNumber,
            files = resolved.files,
            subtitles = resolved.subtitles,
        )

        val resumeDialog = if (!forceFromBeginning) buildResumeDialog(resolved.watchingTime) else null
        val contentState = contentStateFactory.build(item, resolved, resumeDialog, interactor.getSubtitleSize())

        updateViewState(PlayerViewState.Content(contentState))
        initializePlayer(savedPosition = if (resumeDialog != null) null else 0L)
        startProgressSync()
        if (resumeDialog == null) scheduleControlsHide()
        restoreTrackPreferences()
        loadSkipSegments(item, resolved.seasonNumber, resolved.episodeNumber)
    }

    private fun buildResumeDialog(watchingTime: Int?): ResumeDialogState? {
        val savedPosition = watchingTime?.toLong()?.times(1000) ?: 0L
        if (savedPosition <= 0) return null
        val media = currentMedia
        val episodeInfo = if (media?.seasonNumber != null && media.episodeNumber != null) {
            mapper.buildSubtitle(media.item, media.seasonNumber, media.episodeNumber, null)
        } else null
        return ResumeDialogState(
            savedPosition = savedPosition,
            formattedTime = mapper.formatTime(savedPosition),
            episodeInfo = episodeInfo,
        )
    }

    private fun initializePlayer(savedPosition: Long?) {
        val media = currentMedia ?: return
        val qualityIndex = (stateValue as? PlayerViewState.Content)?.content?.selectedQualityIndex ?: 0
        val streamUrl = interactor.selectStreamUrl(media.files, qualityIndex) ?: return
        playbackController.prepare(streamUrl, media.subtitles, savedPosition)
    }

    private fun switchStreamUrl(qualityIndex: Int) {
        val media = currentMedia ?: return
        val streamUrl = interactor.selectStreamUrl(media.files, qualityIndex) ?: return
        playbackController.switchStream(streamUrl, media.subtitles)
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
            is PlayerAction.SkipSegmentClicked -> performSkipSegment()
            is PlayerAction.CancelSkipSegment -> cancelSkipSegment()
            is PlayerAction.SkipSegmentCountdownFinished -> performSkipSegment()
            is PlayerAction.ResumeFromPosition -> resumeFromSavedPosition()
            is PlayerAction.StartFromBeginning -> startFromBeginning()
            is PlayerAction.RetryPlayback -> retryPlayback()
            is PlayerAction.OnBackground -> pauseForBackground()
            is PlayerAction.OnBackPressed -> handleBackAndCheckExit()
            else -> super.onAction(action)
        }
    }

    private fun togglePlayPause() {
        if (playbackController.isPlaying) {
            playbackController.pause()
            saveCurrentPosition()
            updateContent { copy(isPlaying = false) }
            showPlayPauseIndicator(isPlaying = false)
        } else {
            playbackController.play()
            updateContent { copy(isPlaying = true) }
            showPlayPauseIndicator(isPlaying = true)
        }
    }

    private fun showPlayPauseIndicator(isPlaying: Boolean) {
        updateContent {
            copy(playPauseIndicator = PlayPauseIndicatorState(isPlaying = isPlaying))
        }
        playPauseHideJob?.cancel()
        playPauseHideJob = launch {
            delay(PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS)
            updateContent { copy(playPauseIndicator = null) }
        }
    }

    private fun seekForward() {
        val step = seekHandler.nextStep()
        val newPosition = (playbackController.currentPosition + step * 1000L).coerceAtMost(playbackController.duration)
        playbackController.seekTo(newPosition)
        showSeekIndicator(isForward = true, stepSeconds = step, targetPosition = newPosition)
    }

    private fun seekBackward() {
        val step = seekHandler.nextStep()
        val newPosition = (playbackController.currentPosition - step * 1000L).coerceAtLeast(0)
        playbackController.seekTo(newPosition)
        showSeekIndicator(isForward = false, stepSeconds = step, targetPosition = newPosition)
    }

    private fun showSeekIndicator(isForward: Boolean, stepSeconds: Int, targetPosition: Long) {
        val offsetText = mapper.formatSeekOffset(isForward, stepSeconds)
        updateContent {
            copy(
                seekIndicator = SeekIndicatorState(
                    isForward = isForward,
                    offsetText = offsetText,
                    targetTimeText = mapper.formatTime(targetPosition),
                )
            )
        }
        seekIndicatorHideJob?.cancel()
        seekIndicatorHideJob = launch {
            delay(SEEK_INDICATOR_HIDE_DELAY_MS)
            updateContent { copy(seekIndicator = null) }
        }
    }

    private fun showControls(focusTarget: FocusTarget) {
        val effects = controlsStateMachine.showControls(focusTarget)
        applyControlsState()
        processEffects(effects)
    }

    private fun hideControls() {
        val effects = controlsStateMachine.hideControls()
        applyControlsState()
        processEffects(effects)
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = launch {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsStateMachine.applyControlsVisibility(false)
            applyControlsState()
        }
    }

    private fun openPanel(panel: ActivePanel) {
        val effects = controlsStateMachine.openPanel(panel, playbackController.isPlaying)
        applyControlsState()
        processEffects(effects)
    }

    private fun closePanel() {
        val effects = controlsStateMachine.closePanel()
        applyControlsState()
        processEffects(effects)
    }

    private fun applyControlsState() {
        val cs = controlsStateMachine.state
        updateContent {
            copy(
                controlsVisible = cs.controlsVisible,
                controlsFocusTarget = cs.focusTarget,
                activePanel = cs.activePanel,
            )
        }
    }

    private fun processEffects(effects: List<ControlsStateMachine.Effect>) {
        for (effect in effects) {
            when (effect) {
                is ControlsStateMachine.Effect.ScheduleHide -> scheduleControlsHide()
                is ControlsStateMachine.Effect.CancelHide -> controlsHideJob?.cancel()
                is ControlsStateMachine.Effect.PausePlayback -> playbackController.pause()
                is ControlsStateMachine.Effect.ResumePlayback -> playbackController.play()
                is ControlsStateMachine.Effect.SaveAndExit -> {
                    saveCurrentPosition()
                    router.back()
                }
            }
        }
    }

    private fun applyAudioTrackSelection(index: Int) {
        updateContent {
            copy(selectedAudioTrackIndex = index)
        }
        playbackController.selectAudioTrack(index)
        saveTrackPreferences()
    }

    private fun applySubtitleSelection(index: Int) {
        updateContent {
            copy(selectedSubtitleIndex = index)
        }
        playbackController.selectSubtitle(index)
        saveTrackPreferences()
    }

    private fun selectSoundMode(index: Int) {
        updateContent {
            copy(selectedSoundModeIndex = index)
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
        updateContent {
            copy(subtitleSize = newSize)
        }
    }

    private fun selectQuality(index: Int) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (currentState.selectedQualityIndex == index) return

        updateContent {
            copy(selectedQualityIndex = index)
        }
        switchStreamUrl(index)
    }

    private fun selectSpeed(index: Int) {
        updateContent { copy(selectedSpeedIndex = index) }
        val speed = (stateValue as? PlayerViewState.Content)?.content?.speeds?.getOrNull(index)?.speed ?: 1.0f
        playbackController.setSpeed(speed)
    }

    private fun selectAspectRatio(index: Int) {
        updateContent {
            copy(selectedAspectRatioIndex = index)
        }
        // Aspect ratio is applied in PlayerScreenContent via PlayerView.resizeMode
    }

    private fun selectEpisodeById(episodeId: Int) {
        val item = currentMedia?.item ?: return
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
        playbackController.release()
        countdownJob?.cancel()
        skipCountdownJob?.cancel()
        seekIndicatorHideJob?.cancel()
        positionUpdateJob?.cancel()
        progressTracker.reset()
        segments = emptyList()
        creditsSegment = null
        dismissedSegmentType = null

        updateViewState(PlayerViewState.Loading)
        launch { preparePlayback(seasonNumber, episodeNumber, forceFromBeginning = true) }
    }

    private fun playNextEpisode() {
        val media = currentMedia ?: return
        val season = media.seasonNumber ?: return
        val episode = media.episodeNumber ?: return
        val next = interactor.findNextEpisode(media.item, season, episode) ?: return
        switchEpisode(next.first, next.second)
    }

    private fun onPlaybackEnded() {
        markCurrentAsWatched()
        val state = stateValue as? PlayerViewState.Content ?: return
        val content = state.content
        when {
            !content.isMovie && content.hasNextEpisode && content.nextEpisodeCountdown == null -> startNextEpisodeCountdown()
            !content.isMovie -> updateContent { copy(controlsVisible = true) }
        }
    }

    private fun startNextEpisodeCountdown() {
        updateContent {
            copy(nextEpisodeCountdown = NEXT_EPISODE_COUNTDOWN_SEC)
        }
        countdownJob?.cancel()
        countdownJob = launch {
            for (i in NEXT_EPISODE_COUNTDOWN_SEC downTo 0) {
                updateContent { copy(nextEpisodeCountdown = i) }
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
        updateContent {
            copy(nextEpisodeCountdown = null)
        }
    }

    private fun resumeFromSavedPosition() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val position = state.resumeDialog?.savedPosition ?: 0L
        playbackController.seekTo(position)
        playbackController.play()
        updateContent {
            copy(resumeDialog = null, isPlaying = true)
        }
        scheduleControlsHide()
    }

    private fun startFromBeginning() {
        playbackController.seekTo(0)
        playbackController.play()
        updateContent {
            copy(resumeDialog = null, isPlaying = true)
        }
        scheduleControlsHide()
    }

    private fun pauseForBackground() {
        if (playbackController.isPlaying) {
            playbackController.pause()
            saveCurrentPosition()
            updateContent { copy(isPlaying = false) }
        }
    }

    private fun retryPlayback() {
        updateViewState(PlayerViewState.Loading)
        playbackController.release()
        loadContent()
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
        if (state.content.activeSkipSegment != null) {
            cancelSkipSegment()
            return false
        }
        if (state.content.nextEpisodeCountdown != null) {
            cancelCountdown()
            return false
        }
        val effects = controlsStateMachine.handleBack()
        if (effects.any { it is ControlsStateMachine.Effect.SaveAndExit }) {
            processEffects(effects)
            return true
        }
        applyControlsState()
        processEffects(effects)
        return false
    }

    private fun startProgressSync() {
        progressTracker.startSync(
            scope = viewModelScope,
            intervalMs = PROGRESS_SYNC_INTERVAL_MS,
            isPlaying = { playbackController.isPlaying },
            onSave = { saveCurrentPosition() },
        )
        startPositionUpdates()
    }

    private var lastBufferedPosition: Long = 0L

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        lastBufferedPosition = 0L
        positionUpdateJob = launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                val isPlaying = playbackController.isPlaying
                val isBuffering = (stateValue as? PlayerViewState.Content)?.content?.isBuffering == true
                if (isPlaying || isBuffering) {
                    val currentBuffered = playbackController.bufferedPosition
                    val speedBps = if (isBuffering) {
                        val deltaBytes = (currentBuffered - lastBufferedPosition).coerceAtLeast(0)
                        // Convert ms delta to bytes/sec estimate (rough: 1ms buffered ≈ bitrate-dependent)
                        // Use buffered position delta in ms, scale to approximate bytes/sec
                        (deltaBytes * 1000 / POSITION_UPDATE_INTERVAL_MS)
                    } else 0L
                    lastBufferedPosition = currentBuffered

                    val debugInfo = if (debugOverlayEnabled) playbackController.getDebugInfo() else null
                    updateContent {
                        copy(
                            currentPosition = playbackController.currentPosition,
                            duration = playbackController.duration,
                            bufferedPosition = currentBuffered,
                            bufferingSpeedBps = speedBps,
                            debugInfo = debugInfo,
                        )
                    }
                    if (isPlaying) {
                        checkAutoMarkWatched()
                        checkEarlyNextEpisode()
                        checkSkipSegment()
                    }
                }
            }
        }
    }

    private fun checkEarlyNextEpisode() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (state.isMovie || !state.hasNextEpisode) return
        if (state.nextEpisodeCountdown != null) return
        val duration = playbackController.duration
        if (duration <= 0) return

        val creditsStart = creditsSegment?.startMs
        if (creditsStart != null) {
            if (playbackController.currentPosition >= creditsStart) {
                startNextEpisodeCountdown()
            }
        } else {
            val remaining = duration - playbackController.currentPosition
            if (remaining < duration * EARLY_NEXT_EPISODE_THRESHOLD) {
                startNextEpisodeCountdown()
            }
        }
    }

    private fun loadSkipSegments(item: Item, season: Int?, episode: Int?) {
        log("loadSkipSegments called: title='${item.title}', imdb=${item.imdb}, s=$season, e=$episode")
        launch {
            segments = skipSegmentInteractor.loadSegments(item, season, episode)
            creditsSegment = skipSegmentInteractor.findCreditsSegment(segments)
            dismissedSegmentType = null
            // Late arrival check: if credits segment exists and position already past it
            if (creditsSegment != null) {
                val state = (stateValue as? PlayerViewState.Content)?.content ?: return@launch
                if (!state.isMovie && state.hasNextEpisode && state.nextEpisodeCountdown == null) {
                    if (playbackController.currentPosition >= creditsSegment!!.startMs) {
                        startNextEpisodeCountdown()
                    }
                }
            }
        }
    }

    private fun checkSkipSegment() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (state.nextEpisodeCountdown != null || state.resumeDialog != null) return

        val currentPos = playbackController.currentPosition
        // Detect seek: if position jumped more than 2s in one 500ms tick, skip detection
        val positionDelta = kotlin.math.abs(currentPos - lastPositionMs)
        lastPositionMs = currentPos
        if (positionDelta > 2000) {
            // Position jumped — clear dismissed state, don't show overlay this tick
            dismissedSegmentType = null
            updateContent { copy(activeSkipSegment = null) }
            skipCountdownJob?.cancel()
            skipCountdownJob = null
            return
        }

        val activeSegment = skipSegmentInteractor.findActiveSegment(segments, currentPos)
        if (activeSegment == null) {
            // Left segment zone — clear dismissed state
            if (state.activeSkipSegment != null) {
                updateContent { copy(activeSkipSegment = null) }
                skipCountdownJob?.cancel()
                skipCountdownJob = null
            }
            dismissedSegmentType = null
            return
        }

        // Don't show for credits when it's a series with next episode (NextEpisodeOverlay handles it)
        if (activeSegment.type == SkipSegmentType.CREDITS && !state.isMovie && state.hasNextEpisode) return

        // Don't re-show dismissed segment
        if (activeSegment.type == dismissedSegmentType) return

        // Already showing this segment
        if (state.activeSkipSegment?.type == activeSegment.type) return

        startSkipSegmentCountdown(activeSegment)
    }

    private fun startSkipSegmentCountdown(segment: SkipSegment) {
        skipCountdownJob?.cancel()
        val uiState = SkipSegmentUIState(
            label = mapper.mapSkipSegmentLabel(segment.type),
            targetPositionMs = segment.endMs ?: playbackController.duration,
            type = segment.type,
            countdown = SKIP_COUNTDOWN_SEC,
        )
        updateContent { copy(activeSkipSegment = uiState) }
        skipCountdownJob = launch {
            for (i in SKIP_COUNTDOWN_SEC - 1 downTo 0) {
                delay(1000)
                updateContent { copy(activeSkipSegment = activeSkipSegment?.copy(countdown = i)) }
            }
            performSkipSegment()
        }
    }

    private fun performSkipSegment() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        val skipState = state.activeSkipSegment ?: return

        if (skipState.type == SkipSegmentType.CREDITS && !state.isMovie && state.hasNextEpisode) {
            markCurrentAsWatched()
            playNextEpisode()
        } else {
            playbackController.seekTo(skipState.targetPositionMs)
        }
        updateContent { copy(activeSkipSegment = null) }
        skipCountdownJob = null
    }

    private fun cancelSkipSegment() {
        val currentType = (stateValue as? PlayerViewState.Content)?.content?.activeSkipSegment?.type
        dismissedSegmentType = currentType
        skipCountdownJob?.cancel()
        skipCountdownJob = null
        updateContent { copy(activeSkipSegment = null) }
    }

    private fun saveCurrentPosition() {
        val media = currentMedia ?: return
        val videoNumber = media.videoNumber ?: return
        val timeSeconds = (playbackController.currentPosition / 1000).toInt()
        launch {
            interactor.saveWatchingTime(params.itemId, videoNumber, timeSeconds, media.seasonNumber)
        }
    }

    private fun checkAutoMarkWatched() {
        val duration = playbackController.duration
        if (duration <= 0) return
        val remaining = duration - playbackController.currentPosition
        if (remaining < duration * AUTO_MARK_WATCHED_THRESHOLD) {
            markCurrentAsWatched()
        }
    }

    private fun markCurrentAsWatched() {
        if (progressTracker.isMarkedWatched) return
        progressTracker.markAsWatched()
        launch {
            interactor.markAsWatched(
                id = params.itemId,
                season = currentMedia?.seasonNumber,
                videoNumber = currentMedia?.videoNumber,
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

    fun getExoPlayer(): ExoPlayer? = playbackController.player

    override fun onCleared() {
        saveCurrentPosition()
        playbackController.release()
        progressTracker.stopSync()
        controlsHideJob?.cancel()
        seekIndicatorHideJob?.cancel()
        playPauseHideJob?.cancel()
        countdownJob?.cancel()
        skipCountdownJob?.cancel()
        positionUpdateJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val CONTROLS_HIDE_DELAY_MS = 3000L
        const val SEEK_INDICATOR_HIDE_DELAY_MS = 1500L
        const val PROGRESS_SYNC_INTERVAL_MS = 30_000L
        const val POSITION_UPDATE_INTERVAL_MS = 500L
        const val NEXT_EPISODE_COUNTDOWN_SEC = 7
        const val AUTO_MARK_WATCHED_THRESHOLD = 0.10
        const val PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS = 1500L
        const val EARLY_NEXT_EPISODE_THRESHOLD = 0.05
        const val SKIP_COUNTDOWN_SEC = 7
    }
}