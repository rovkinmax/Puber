package com.kino.puber.ui.feature.player.vm

import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
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
import com.kino.puber.domain.model.SubtitleSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@UnstableApi
internal class PlayerVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val params: PlayerScreenParams,
    private val mapper: PlayerUIMapper,
    private val interactor: PlayerInteractor,
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
    private var countdownJob: Job? = null

    private val seekHandler = SeekHandler()
    private val controlsStateMachine = ControlsStateMachine()
    private val progressTracker = ProgressTracker()

    private val playbackCallback = object : PlaybackController.Callback {
        override fun onPlaybackStateChanged(isPlaying: Boolean, position: Long, duration: Long, buffered: Long) {
            updateContent {
                copy(
                    isPlaying = isPlaying,
                    currentPosition = position,
                    duration = duration,
                    bufferedPosition = buffered,
                )
            }
            checkAutoMarkWatched()
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
    }

    private fun buildResumeDialog(watchingTime: Int?): ResumeDialogState? {
        val savedPosition = watchingTime?.toLong()?.times(1000) ?: 0L
        if (savedPosition <= 0) return null
        return ResumeDialogState(
            savedPosition = savedPosition,
            formattedTime = mapper.formatTime(savedPosition),
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
            is PlayerAction.ResumeFromPosition -> resumeFromSavedPosition()
            is PlayerAction.StartFromBeginning -> startFromBeginning()
            is PlayerAction.RetryPlayback -> retryPlayback()
            is PlayerAction.OnBackPressed -> handleBackAndCheckExit()
            else -> super.onAction(action)
        }
    }

    private fun togglePlayPause() {
        if (playbackController.isPlaying) {
            playbackController.pause()
            saveCurrentPosition()
        } else {
            playbackController.play()
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
        seekIndicatorHideJob?.cancel()
        progressTracker.reset()

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
        when {
            !state.content.isMovie && state.content.hasNextEpisode -> startNextEpisodeCountdown()
            !state.content.isMovie -> updateContent { copy(controlsVisible = true) }
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
            copy(resumeDialog = null)
        }
        scheduleControlsHide()
    }

    private fun startFromBeginning() {
        playbackController.seekTo(0)
        playbackController.play()
        updateContent {
            copy(resumeDialog = null)
        }
        scheduleControlsHide()
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
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val CONTROLS_HIDE_DELAY_MS = 3000L
        const val SEEK_INDICATOR_HIDE_DELAY_MS = 1500L
        const val PROGRESS_SYNC_INTERVAL_MS = 30_000L
        const val NEXT_EPISODE_COUNTDOWN_SEC = 7
        const val AUTO_MARK_WATCHED_THRESHOLD = 0.05
    }
}
