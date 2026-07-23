package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.material3.MaterialTheme

@Composable
internal fun PlayerControlsOverlay(
    visible: Boolean,
    title: String,
    subtitle: String?,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isBuffering: Boolean,
    isMovie: Boolean,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    hasPreviousEpisode: Boolean,
    canMarkCurrentWatched: Boolean,
    isCurrentMediaWatched: Boolean,
    isMarkCurrentWatchedInFlight: Boolean,
    onEpisodesClick: () -> Unit,
    onMarkCurrentWatchedClick: () -> Unit,
    onAudioSubtitlesClick: () -> Unit,
    onVideoSettingsClick: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    onPreviousEpisodeClick: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onControlsInteraction: () -> Unit,
    onBackPressed: () -> Unit,
    firstButtonFocusRequester: FocusRequester,
    episodesButtonFocusRequester: FocusRequester,
    audioSubtitlesButtonFocusRequester: FocusRequester,
    videoSettingsButtonFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        ControlsScrim(onControlsInteraction = onControlsInteraction) {
            PlayerTitle(title = title, subtitle = subtitle)
            ControlsBottomBar(
                progressState = ProgressBarState(
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    isBuffering = isBuffering,
                ),
                buttonState = PlayerButtonRowState(
                    isMovie = isMovie,
                    isPlaying = isPlaying,
                    hasNextEpisode = hasNextEpisode,
                    hasPreviousEpisode = hasPreviousEpisode,
                    canMarkCurrentWatched = canMarkCurrentWatched,
                    isCurrentMediaWatched = isCurrentMediaWatched,
                    isMarkCurrentWatchedInFlight = isMarkCurrentWatchedInFlight,
                ),
                actions = PlayerControlActions(
                    onEpisodesClick = onEpisodesClick,
                    onMarkCurrentWatchedClick = onMarkCurrentWatchedClick,
                    onAudioSubtitlesClick = onAudioSubtitlesClick,
                    onVideoSettingsClick = onVideoSettingsClick,
                    onNextEpisodeClick = onNextEpisodeClick,
                    onPreviousEpisodeClick = onPreviousEpisodeClick,
                    onSeekForward = onSeekForward,
                    onSeekBackward = onSeekBackward,
                    onTogglePlayPause = onTogglePlayPause,
                ),
                focusRequesters = PlayerControlFocusRequesters(
                    firstButton = firstButtonFocusRequester,
                    episodesButton = episodesButtonFocusRequester,
                    audioSubtitlesButton = audioSubtitlesButtonFocusRequester,
                    videoSettingsButton = videoSettingsButtonFocusRequester,
                    seekBar = seekBarFocusRequester,
                ),
            )
        }
    }
}

private data class ProgressBarState(
    val currentPosition: Long,
    val duration: Long,
    val bufferedPosition: Long,
    val isBuffering: Boolean,
)

internal data class PlayerControlActions(
    val onEpisodesClick: () -> Unit,
    val onMarkCurrentWatchedClick: () -> Unit,
    val onAudioSubtitlesClick: () -> Unit,
    val onVideoSettingsClick: () -> Unit,
    val onNextEpisodeClick: () -> Unit,
    val onPreviousEpisodeClick: () -> Unit,
    val onSeekForward: () -> Unit,
    val onSeekBackward: () -> Unit,
    val onTogglePlayPause: () -> Unit,
)

internal data class PlayerControlFocusRequesters(
    val firstButton: FocusRequester,
    val episodesButton: FocusRequester,
    val audioSubtitlesButton: FocusRequester,
    val videoSettingsButton: FocusRequester,
    val seekBar: FocusRequester,
)

@Composable
private fun ControlsScrim(
    onControlsInteraction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(controlsScrimBrush())
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                onControlsInteraction()
                false
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            content()
        }
    }
}

@Composable
private fun ControlsBottomBar(
    progressState: ProgressBarState,
    buttonState: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PlayerProgressBar(
            currentPosition = progressState.currentPosition,
            duration = progressState.duration,
            bufferedPosition = progressState.bufferedPosition,
            isBuffering = progressState.isBuffering,
            onSeekForward = actions.onSeekForward,
            onSeekBackward = actions.onSeekBackward,
            onTogglePlayPause = actions.onTogglePlayPause,
            focusRequester = focusRequesters.seekBar,
        )
        PlayerButtonRow(
            state = buttonState,
            actions = actions,
            focusRequesters = focusRequesters,
        )
    }
}

@Composable
private fun controlsScrimBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.scrim.copy(alpha = CONTROLS_SCRIM_ALPHA),
            MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
            MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
            MaterialTheme.colorScheme.scrim.copy(alpha = CONTROLS_SCRIM_ALPHA),
        ),
    )
}

private const val CONTROLS_SCRIM_ALPHA = 0.7f
