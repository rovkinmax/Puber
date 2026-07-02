package com.kino.puber.ui.feature.player.vm

import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kino.puber.R
import com.kino.puber.core.logger.log
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
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
import com.kino.puber.ui.feature.player.model.BufferPreset
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
    private val playbackController: PlaybackControl,
    private val resources: ResourceProvider,
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

    private data class CurrentEpisode(
        val media: CurrentMedia,
        val seasonNumber: Int,
        val episodeNumber: Int,
    )

    private var currentMedia: CurrentMedia? = null

    private var controlsHideJob: Job? = null
    private var seekIndicatorHideJob: Job? = null
    private var playPauseHideJob: Job? = null
    private var countdownJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var skipCountdownJob: Job? = null
    private var bufferingDebounceJob: Job? = null

    private var segments: List<SkipSegment> = emptyList()
    private var creditsSegment: SkipSegment? = null
    private var dismissedSegmentType: SkipSegmentType? = null
    private var countdownDismissed = false
    private var tracksRestoredForCurrentMedia = false
    private var episodeSwitchInProgress = false
    private var lastPositionMs: Long = 0L

    private val seekHandler = SeekHandler()
    private val controlsStateMachine = ControlsStateMachine()
    private val progressTracker = ProgressTracker()
    private val audioTrackPreferenceResolver = AudioTrackPreferenceResolver()
    private val debugOverlayEnabled = interactor.isDebugOverlayEnabled()

    private val playbackCallback = object : PlaybackControl.Callback {
        override fun onPlaybackStateChanged(
            isPlaying: Boolean,
            isBuffering: Boolean,
            position: Long,
            duration: Long,
            buffered: Long,
        ) {
            val wasBuffering = (stateValue as? PlayerViewState.Content)?.content?.isBuffering == true
            updateContent {
                copy(isPlaying = isPlaying)
            }
            if (isBuffering && !wasBuffering) {
                // Debounce: only show spinner if buffering lasts > 800ms
                bufferingDebounceJob?.cancel()
                bufferingDebounceJob = launch {
                    delay(BUFFERING_DEBOUNCE_MS)
                    updateContent { copy(isBuffering = true) }
                    controlsHideJob?.cancel()
                }
            } else if (!isBuffering) {
                bufferingDebounceJob?.cancel()
                if (wasBuffering) {
                    updateContent { copy(isBuffering = false) }
                    val controlsVisible = (stateValue as? PlayerViewState.Content)?.content?.controlsVisible == true
                    if (controlsVisible) {
                        scheduleControlsHide()
                    }
                }
            }
        }

        override fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int) {
            updateContent {
                copy(
                    audioTracks = audioTracks,
                    selectedAudioTrackIndex = selectedIndex,
                )
            }
            if (!tracksRestoredForCurrentMedia) {
                tracksRestoredForCurrentMedia = true
                restoreTrackPreferences()
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
        val contentState = contentStateFactory.build(
            item = item,
            resolved = resolved,
            resumeDialog = resumeDialog,
            subtitleSize = interactor.getSubtitleSize(),
            savedBufferPreset = interactor.getBufferPreset(),
            fastDnsEnabled = interactor.isFastDnsEnabled(),
        )

        updateViewState(PlayerViewState.Content(contentState))
        episodeSwitchInProgress = false
        initializePlayer(savedPosition = if (resumeDialog != null) null else 0L)
        startProgressSync()
        if (resumeDialog == null) scheduleControlsHide()
        loadSkipSegments(item, resolved.seasonNumber, resolved.episodeNumber)
    }

    private fun buildResumeDialog(watchingTime: Int?): ResumeDialogState? {
        val savedPosition = watchingTime?.toLong()?.times(1000) ?: 0L
        if (savedPosition <= 0) return null
        val media = currentMedia
        val episodeInfo = if (media?.seasonNumber != null && media.episodeNumber != null) {
            mapper.buildSubtitle(media.item, media.seasonNumber, media.episodeNumber, null)
        } else {
            null
        }
        return ResumeDialogState(
            savedPosition = savedPosition,
            formattedTime = mapper.formatTime(savedPosition),
            episodeInfo = episodeInfo,
        )
    }

    private fun initializePlayer(savedPosition: Long?) {
        val media = currentMedia ?: return
        val content = (stateValue as? PlayerViewState.Content)?.content
        val qualityIndex = content?.selectedQualityIndex ?: 0
        val bufferPreset = content
            ?.bufferPresets
            ?.getOrNull(content.selectedBufferPresetIndex)
            ?.preset
            ?: BufferPreset.AUTO
        val fastDns = content?.fastDnsEnabled ?: true
        val streamUrl = interactor.selectStreamUrl(media.files, qualityIndex) ?: return
        playbackController.prepare(streamUrl, media.subtitles, savedPosition, bufferPreset, fastDns)
    }

    private fun switchStreamUrl(qualityIndex: Int) {
        val media = currentMedia ?: return
        val streamUrl = interactor.selectStreamUrl(media.files, qualityIndex) ?: return
        playbackController.switchStream(streamUrl, media.subtitles)
    }

    private fun restoreTrackPreferences() {
        val preferredLabel = interactor.getPreferredAudioLabel(params.itemId)
        val preferredLang = interactor.getPreferredAudioLang(params.itemId)
        val subtitleLang = interactor.getPreferredSubtitleLang(params.itemId)
        val subtitleUrl = interactor.getPreferredSubtitleUrl(params.itemId)
        val content = (stateValue as? PlayerViewState.Content)?.content ?: return
        val audioIndex = audioTrackPreferenceResolver.findAudioTrackIndex(
            tracks = content.audioTracks,
            preferredLabel = preferredLabel,
            preferredLang = preferredLang,
        )

        if (audioIndex >= 0) {
            applyAudioTrackSelection(audioIndex, persist = false)
        }

        val subtitleIndex = audioTrackPreferenceResolver.findSubtitleTrackIndex(
            tracks = content.subtitleTracks,
            preferredLang = subtitleLang,
            preferredUrl = subtitleUrl,
        )
        if (subtitleIndex >= 0) {
            applySubtitleSelection(subtitleIndex, persist = false)
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
            is PlayerAction.SelectBufferPreset -> selectBufferPreset(action.index)
            is PlayerAction.ToggleFastDns -> toggleFastDns()
            is PlayerAction.SelectEpisode -> switchEpisode(action.seasonNumber, action.episodeNumber)
            is PlayerAction.SelectEpisodeById -> selectEpisodeById(action.episodeId)
            is PlayerAction.EpisodeWatchedChanged -> onEpisodeWatchedChanged(action.item, action.watched)
            is PlayerAction.SeasonWatchedChanged -> onSeasonWatchedChanged(action.item, action.watched)
            is PlayerAction.NextEpisode -> playNextEpisode()
            is PlayerAction.PreviousEpisode -> playPreviousEpisode()
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
        updateContent { copy(currentPosition = newPosition) }
        showSeekIndicator(isForward = true, stepSeconds = step, targetPosition = newPosition)
    }

    private fun seekBackward() {
        val step = seekHandler.nextStep()
        val newPosition = (playbackController.currentPosition - step * 1000L).coerceAtLeast(0)
        playbackController.seekTo(newPosition)
        updateContent { copy(currentPosition = newPosition) }
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

    private fun applyAudioTrackSelection(index: Int, persist: Boolean = true) {
        updateContent {
            copy(selectedAudioTrackIndex = index)
        }
        playbackController.selectAudioTrack(index)
        if (persist) {
            saveTrackPreferences()
        }
    }

    private fun applySubtitleSelection(index: Int, persist: Boolean = true) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        val subtitle = currentState.subtitleTracks.getOrNull(index) ?: return
        updateContent {
            copy(selectedSubtitleIndex = index)
        }
        playbackController.selectSubtitle(subtitle)
        if (persist) {
            saveTrackPreferences()
        }
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

    private fun selectBufferPreset(index: Int) {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (currentState.selectedBufferPresetIndex == index) return

        val preset = currentState.bufferPresets.getOrNull(index)?.preset ?: return
        interactor.saveBufferPreset(preset)

        val position = playbackController.currentPosition
        updateContent { copy(selectedBufferPresetIndex = index) }
        tracksRestoredForCurrentMedia = false
        initializePlayer(savedPosition = position)
    }

    private fun toggleFastDns() {
        val currentState = (stateValue as? PlayerViewState.Content)?.content ?: return
        val newValue = !currentState.fastDnsEnabled
        interactor.setFastDnsEnabled(newValue)

        val position = playbackController.currentPosition
        updateContent { copy(fastDnsEnabled = newValue) }
        tracksRestoredForCurrentMedia = false
        initializePlayer(savedPosition = position)
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
        if (episodeSwitchInProgress) return
        episodeSwitchInProgress = true
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
        countdownDismissed = false
        tracksRestoredForCurrentMedia = false

        updateViewState(PlayerViewState.Loading)
        launch { preparePlayback(seasonNumber, episodeNumber, forceFromBeginning = true) }
    }

    private fun playNextEpisode() {
        currentEpisode()?.let { episode ->
            interactor.findNextEpisode(
                item = episode.media.item,
                currentSeason = episode.seasonNumber,
                currentEpisode = episode.episodeNumber,
            )
        }?.let(::switchEpisode)
    }

    private fun playPreviousEpisode() {
        currentEpisode()?.let { episode ->
            interactor.findPreviousEpisode(
                item = episode.media.item,
                currentSeason = episode.seasonNumber,
                currentEpisode = episode.episodeNumber,
            )
        }?.let(::switchEpisode)
    }

    private fun switchEpisode(episode: Pair<Int, Int>) {
        switchEpisode(episode.first, episode.second)
    }

    private fun onEpisodeWatchedChanged(item: VideoItemUIState, watched: Boolean) {
        val season = item.seasonNumber ?: return
        val episode = item.episodeNumber ?: return
        launch {
            runCatching {
                interactor.setEpisodeWatched(params.itemId, season, episode, watched)
            }.onSuccess { updated ->
                updateEpisodes(updated)
                showMessage(
                    resources.getString(
                        if (watched) {
                            R.string.context_menu_episode_watched
                        } else {
                            R.string.context_menu_episode_unwatched
                        }
                    )
                )
            }.onFailure { throwable ->
                showMessage(errorHandler.map(throwable).message)
            }
        }
    }

    private fun onSeasonWatchedChanged(item: VideoItemUIState, watched: Boolean) {
        val season = item.seasonNumber ?: return
        launch {
            runCatching {
                interactor.setSeasonWatched(params.itemId, season, watched)
            }.onSuccess { updated ->
                updateEpisodes(updated)
                showMessage(
                    resources.getString(
                        if (watched) {
                            R.string.context_menu_season_watched
                        } else {
                            R.string.context_menu_season_unwatched
                        }
                    )
                )
            }.onFailure { throwable ->
                showMessage(errorHandler.map(throwable).message)
            }
        }
    }

    private fun updateEpisodes(item: Item) {
        currentMedia = currentMedia?.copy(item = item)
        updateContent { copy(episodes = mapper.mapEpisodes(item)) }
    }

    private fun currentEpisode(): CurrentEpisode? {
        val media = currentMedia ?: return null
        return if (canUseCurrentEpisode(media)) {
            CurrentEpisode(
                media = media,
                seasonNumber = requireNotNull(media.seasonNumber),
                episodeNumber = requireNotNull(media.episodeNumber),
            )
        } else {
            null
        }
    }

    private fun canUseCurrentEpisode(media: CurrentMedia): Boolean {
        return !episodeSwitchInProgress &&
            media.seasonNumber != null &&
            media.episodeNumber != null
    }

    private fun onPlaybackEnded() {
        markCurrentAsWatched()
        val state = stateValue as? PlayerViewState.Content ?: return
        val content = state.content
        when {
            !content.isMovie &&
                content.hasNextEpisode &&
                content.nextEpisodeCountdown == null -> startNextEpisodeCountdown()
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
        countdownDismissed = true
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
        val state = stateValue as? PlayerViewState.Content
        return when {
            state == null -> {
                saveCurrentPosition()
                router.back()
                true
            }
            state.content.activeSkipSegment != null -> {
                cancelSkipSegment()
                false
            }
            state.content.nextEpisodeCountdown != null -> {
                cancelCountdown()
                false
            }
            else -> handleControlsBack()
        }
    }

    private fun handleControlsBack(): Boolean {
        val effects = controlsStateMachine.handleBack()
        val shouldExit = effects.any { it is ControlsStateMachine.Effect.SaveAndExit }
        if (!shouldExit) {
            applyControlsState()
        }
        processEffects(effects)
        return shouldExit
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

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                val isPlaying = playbackController.isPlaying
                val isBuffering = (stateValue as? PlayerViewState.Content)?.content?.isBuffering == true
                if (isPlaying || isBuffering) {
                    val debugInfo = if (debugOverlayEnabled) {
                        (playbackController as? PlaybackController)?.getDebugInfo()
                    } else {
                        null
                    }
                    updateContent {
                        copy(
                            currentPosition = playbackController.currentPosition,
                            duration = playbackController.duration,
                            bufferedPosition = playbackController.bufferedPosition,
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
        val duration = playbackController.duration
        if (shouldCheckEarlyNextEpisode(state, duration) && isEarlyNextEpisodePosition(duration)) {
            startNextEpisodeCountdown()
        }
    }

    private fun shouldCheckEarlyNextEpisode(state: PlayerContentState, duration: Long): Boolean {
        return !state.isMovie &&
            state.hasNextEpisode &&
            state.nextEpisodeCountdown == null &&
            !countdownDismissed &&
            duration > 0
    }

    private fun isEarlyNextEpisodePosition(duration: Long): Boolean {
        val currentPosition = playbackController.currentPosition
        val creditsStart = creditsSegment?.startMs
        return if (creditsStart != null) {
            currentPosition >= creditsStart
        } else {
            duration > EARLY_NEXT_EPISODE_OFFSET_MS &&
                duration - currentPosition <= EARLY_NEXT_EPISODE_OFFSET_MS
        }
    }

    private fun loadSkipSegments(item: Item, season: Int?, episode: Int?) {
        log("loadSkipSegments called: title='${item.title}', imdb=${item.imdb}, s=$season, e=$episode")
        launch {
            segments = skipSegmentInteractor.loadSegments(item, season, episode)
            creditsSegment = skipSegmentInteractor.findCreditsSegment(segments)
            dismissedSegmentType = null
            // Late arrival check: if credits segment exists and position already past it
            if (creditsSegment != null && !countdownDismissed) {
                val state = (stateValue as? PlayerViewState.Content)?.content ?: return@launch
                if (shouldStartNextEpisodeForLoadedCredits(state, requireNotNull(creditsSegment))) {
                    startNextEpisodeCountdown()
                }
            }
        }
    }

    private fun shouldStartNextEpisodeForLoadedCredits(
        state: PlayerContentState,
        segment: SkipSegment,
    ): Boolean {
        return !state.isMovie &&
            state.hasNextEpisode &&
            state.nextEpisodeCountdown == null &&
            playbackController.currentPosition >= segment.startMs
    }

    private fun checkSkipSegment() {
        val state = (stateValue as? PlayerViewState.Content)?.content ?: return
        if (state.nextEpisodeCountdown == null && state.resumeDialog == null) {
            val currentPos = playbackController.currentPosition
            if (!handlePositionJump(currentPos)) {
                handleActiveSkipSegment(state, currentPos)
            }
        }
    }

    private fun handlePositionJump(currentPos: Long): Boolean {
        // Detect seek: if position jumped more than 2s in one 500ms tick, skip detection
        val positionDelta = kotlin.math.abs(currentPos - lastPositionMs)
        lastPositionMs = currentPos
        return if (positionDelta > SEEK_JUMP_THRESHOLD_MS) {
            // Position jumped — clear dismissed state, don't show overlay this tick
            dismissedSegmentType = null
            updateContent { copy(activeSkipSegment = null) }
            skipCountdownJob?.cancel()
            skipCountdownJob = null
            true
        } else {
            false
        }
    }

    private fun handleActiveSkipSegment(state: PlayerContentState, currentPos: Long) {
        val activeSegment = skipSegmentInteractor.findActiveSegment(segments, currentPos)
        when {
            activeSegment == null -> clearInactiveSkipSegment(state)
            shouldStartSkipSegmentCountdown(state, activeSegment) -> startSkipSegmentCountdown(activeSegment)
        }
    }

    private fun clearInactiveSkipSegment(state: PlayerContentState) {
        // Left segment zone — clear dismissed state
        if (state.activeSkipSegment != null) {
            updateContent { copy(activeSkipSegment = null) }
            skipCountdownJob?.cancel()
            skipCountdownJob = null
        }
        dismissedSegmentType = null
    }

    private fun shouldStartSkipSegmentCountdown(
        state: PlayerContentState,
        activeSegment: SkipSegment,
    ): Boolean {
        val isCreditsWithNextEpisode = activeSegment.type == SkipSegmentType.CREDITS &&
            !state.isMovie &&
            state.hasNextEpisode
        return !isCreditsWithNextEpisode &&
            activeSegment.type != dismissedSegmentType &&
            state.activeSkipSegment?.type != activeSegment.type
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
            audioLang = audioTrack?.language?.takeIf { it.isNotEmpty() },
            audioLabel = audioTrack?.label?.takeIf { it.isNotEmpty() },
            subtitleLang = subtitle?.language?.takeIf { it.isNotEmpty() },
            subtitleUrl = subtitle?.url?.takeIf { it.isNotEmpty() },
        )
    }

    fun getExoPlayer(): ExoPlayer? = (playbackController as? PlaybackController)?.player

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
        const val NEXT_EPISODE_COUNTDOWN_SEC = 15
        const val AUTO_MARK_WATCHED_THRESHOLD = 0.10
        const val PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS = 1500L
        const val BUFFERING_DEBOUNCE_MS = 800L
        const val EARLY_NEXT_EPISODE_OFFSET_MS = 30_000L
        const val SEEK_JUMP_THRESHOLD_MS = 2_000L
        const val SKIP_COUNTDOWN_SEC = 7
    }
}
