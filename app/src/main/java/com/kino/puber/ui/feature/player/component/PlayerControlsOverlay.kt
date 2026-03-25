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
    isMovie: Boolean,
    hasNextEpisode: Boolean,
    onEpisodesClick: () -> Unit,
    onAudioSubtitlesClick: () -> Unit,
    onVideoSettingsClick: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onControlsInteraction: () -> Unit,
    onBackPressed: () -> Unit,
    firstButtonFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        ),
                    )
                )
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    // Reset hide timer on any key press while controls are visible
                    onControlsInteraction()
                    false // Let D-pad navigate between buttons naturally; BACK handled by Voyager BackHandler
                },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                PlayerTitle(
                    title = title,
                    subtitle = subtitle,
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    PlayerProgressBar(
                        currentPosition = currentPosition,
                        duration = duration,
                        bufferedPosition = bufferedPosition,
                        onSeekForward = onSeekForward,
                        onSeekBackward = onSeekBackward,
                        onTogglePlayPause = onTogglePlayPause,
                        focusRequester = seekBarFocusRequester,
                    )

                    PlayerButtonRow(
                        isMovie = isMovie,
                        hasNextEpisode = hasNextEpisode,
                        onEpisodesClick = onEpisodesClick,
                        onAudioSubtitlesClick = onAudioSubtitlesClick,
                        onVideoSettingsClick = onVideoSettingsClick,
                        onNextEpisodeClick = onNextEpisodeClick,
                        firstButtonFocusRequester = firstButtonFocusRequester,
                    )
                }
            }
        }
    }
}
