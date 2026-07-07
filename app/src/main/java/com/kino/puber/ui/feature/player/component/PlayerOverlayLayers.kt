package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState

private const val TRANSPARENT_SCRIM_STOP = 0.7f
private const val BOTTOM_SCRIM_ALPHA = 0.5f

@Composable
internal fun PlaybackFeedbackLayer(content: PlayerContentState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SeekIndicator(state = content.seekIndicator)
        PlayPauseIndicator(state = content.playPauseIndicator)
    }
}

@Composable
internal fun BufferingProgressLayer(content: PlayerContentState) {
    AnimatedVisibility(
        visible = content.isBuffering && !content.controlsVisible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bufferingProgressBrush()),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PlayerProgressBar(
                currentPosition = content.currentPosition,
                duration = content.duration,
                bufferedPosition = content.bufferedPosition,
                isBuffering = true,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
internal fun PlayerControlsLayer(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequesters: PlayerFocusRequesters,
) {
    PlayerControlsOverlay(
        visible = content.controlsVisible,
        title = content.title,
        subtitle = content.subtitle,
        currentPosition = content.currentPosition,
        duration = content.duration,
        bufferedPosition = content.bufferedPosition,
        isBuffering = content.isBuffering,
        isMovie = content.isMovie,
        isPlaying = content.isPlaying,
        hasNextEpisode = content.hasNextEpisode,
        hasPreviousEpisode = content.hasPreviousEpisode,
        canMarkCurrentWatched = content.canMarkCurrentWatched,
        isCurrentMediaWatched = content.isCurrentMediaWatched,
        onEpisodesClick = rememberAction(onAction, PlayerAction.OpenEpisodesPanel),
        onMarkCurrentWatchedClick = rememberAction(onAction, PlayerAction.MarkCurrentWatched),
        onAudioSubtitlesClick = rememberAction(onAction, PlayerAction.OpenAudioSubtitlesPanel),
        onVideoSettingsClick = rememberAction(onAction, PlayerAction.OpenVideoSettingsPanel),
        onNextEpisodeClick = rememberAction(onAction, PlayerAction.NextEpisode),
        onPreviousEpisodeClick = rememberAction(onAction, PlayerAction.PreviousEpisode),
        onSeekForward = rememberAction(onAction, PlayerAction.SeekForward),
        onSeekBackward = rememberAction(onAction, PlayerAction.SeekBackward),
        onTogglePlayPause = rememberAction(onAction, PlayerAction.TogglePlayPause),
        onControlsInteraction = rememberAction(onAction, PlayerAction.ResetControlsTimer),
        onBackPressed = rememberAction(onAction, PlayerAction.HideControls),
        firstButtonFocusRequester = focusRequesters.firstButton,
        episodesButtonFocusRequester = focusRequesters.episodesButton,
        audioSubtitlesButtonFocusRequester = focusRequesters.audioSubtitlesButton,
        videoSettingsButtonFocusRequester = focusRequesters.videoSettingsButton,
        seekBarFocusRequester = focusRequesters.seekBar,
    )
}

@Composable
internal fun PlayerOverlayLayers(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
) {
    if (content.nextEpisodeCountdown == null && content.resumeDialog == null) {
        SkipSegmentOverlay(
            state = content.activeSkipSegment,
            onSkip = rememberAction(onAction, PlayerAction.SkipSegmentClicked),
            onCancel = rememberAction(onAction, PlayerAction.CancelSkipSegment),
        )
    }

    DebugOverlayLayer(content = content)
    ResumeDialog(
        state = content.resumeDialog,
        onResume = rememberAction(onAction, PlayerAction.ResumeFromPosition),
        onStartFromBeginning = rememberAction(onAction, PlayerAction.StartFromBeginning),
    )
    NextEpisodeOverlay(
        countdown = content.nextEpisodeCountdown,
        onNextEpisode = rememberAction(onAction, PlayerAction.NextEpisode),
        onCancel = rememberAction(onAction, PlayerAction.CancelNextEpisodeCountdown),
    )
}

@Composable
private fun DebugOverlayLayer(content: PlayerContentState) {
    AnimatedVisibility(
        visible = content.controlsVisible && content.debugInfo != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            DebugOverlay(
                debugInfo = content.debugInfo,
                modifier = Modifier.padding(top = 12.dp, end = 16.dp),
            )
        }
    }
}

@Composable
internal fun rememberAction(
    onAction: (UIAction) -> Unit,
    action: UIAction,
): () -> Unit {
    return remember(onAction, action) {
        { onAction(action) }
    }
}

@Composable
private fun bufferingProgressBrush(): Brush {
    return Brush.verticalGradient(
        colorStops = arrayOf(
            TRANSPARENT_SCRIM_STOP to MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
            1f to MaterialTheme.colorScheme.scrim.copy(alpha = BOTTOM_SCRIM_ALPHA),
        ),
    )
}
