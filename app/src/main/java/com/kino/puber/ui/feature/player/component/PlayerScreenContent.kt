package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kino.puber.ui.feature.player.model.AspectRatioMode
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerViewState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun PlayerScreenContent(
    state: PlayerViewState,
    onAction: (UIAction) -> Unit,
    exoPlayer: () -> ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    val playerFocusRequester = remember { FocusRequester() }
    val firstButtonFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }

    val contentState = (state as? PlayerViewState.Content)?.content

    // Don't manage focus while resume dialog is shown — it handles its own focus
    val hasResumeDialog = contentState?.resumeDialog != null

    // Request focus on the player surface when controls are hidden and no panel open
    LaunchedEffect(contentState?.controlsVisible, contentState?.activePanel, hasResumeDialog) {
        if (hasResumeDialog) return@LaunchedEffect
        if (contentState != null && !contentState.controlsVisible && contentState.activePanel == ActivePanel.None) {
            try {
                playerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Direct focus based on controlsFocusTarget
    LaunchedEffect(contentState?.controlsVisible, contentState?.controlsFocusTarget, hasResumeDialog) {
        if (hasResumeDialog) return@LaunchedEffect
        if (contentState?.controlsVisible == true && contentState.controlsFocusTarget != null) {
            try {
                when (contentState.controlsFocusTarget) {
                    FocusTarget.SeekBar -> seekBarFocusRequester.requestFocus()
                    FocusTarget.Buttons -> firstButtonFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    // Consume BOTH ACTION_DOWN and ACTION_UP to prevent system BackHandler
                    // from firing and popping the screen via Voyager
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        onAction(PlayerAction.OnBackPressed)
                    }
                    return@onPreviewKeyEvent true
                }
                false
            },
    ) {
        when (state) {
            is PlayerViewState.Loading -> {
                FullScreenProgressIndicator()
            }

            is PlayerViewState.Error -> {
                ErrorOverlay(
                    message = state.message,
                    onRetry = { onAction(PlayerAction.RetryPlayback) },
                    onBack = { onAction(PlayerAction.OnBackPressed) },
                )
            }

            is PlayerViewState.Content -> {
                val content = state.content

                // Layer 0: Video surface + key event handler (focusable when controls hidden)
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = exoPlayer()
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer()) {
                            view.player = exoPlayer()
                        }
                        val aspectMode = content.aspectRatios.getOrNull(content.selectedAspectRatioIndex)?.mode
                        @UnstableApi
                        view.resizeMode = when (aspectMode) {
                            AspectRatioMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(playerFocusRequester)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false

                            // Resume dialog — don't intercept
                            if (content.resumeDialog != null) return@onKeyEvent false

                            // This handler fires when player surface has focus (controls hidden, no panel)
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    onAction(PlayerAction.SeekBackward)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    onAction(PlayerAction.SeekForward)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    onAction(PlayerAction.ShowControls(FocusTarget.SeekBar))
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    onAction(PlayerAction.ShowControls(FocusTarget.Buttons))
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    onAction(PlayerAction.TogglePlayPause)
                                    true
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    onAction(PlayerAction.OnBackPressed)
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    onAction(PlayerAction.TogglePlayPause)
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                                    onAction(PlayerAction.SeekForward)
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                                    onAction(PlayerAction.SeekBackward)
                                    true
                                }
                                else -> false
                            }
                        },
                )

                // Layer 1: Seek indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SeekIndicator(state = content.seekIndicator)
                }

                // Layer 2: Controls overlay
                PlayerControlsOverlay(
                    visible = content.controlsVisible,
                    title = content.title,
                    subtitle = content.subtitle,
                    currentPosition = content.currentPosition,
                    duration = content.duration,
                    bufferedPosition = content.bufferedPosition,
                    isMovie = content.isMovie,
                    hasNextEpisode = content.hasNextEpisode,
                    onEpisodesClick = { onAction(PlayerAction.OpenEpisodesPanel) },
                    onAudioSubtitlesClick = { onAction(PlayerAction.OpenAudioSubtitlesPanel) },
                    onVideoSettingsClick = { onAction(PlayerAction.OpenVideoSettingsPanel) },
                    onNextEpisodeClick = { onAction(PlayerAction.NextEpisode) },
                    onSeekForward = { onAction(PlayerAction.SeekForward) },
                    onSeekBackward = { onAction(PlayerAction.SeekBackward) },
                    onTogglePlayPause = { onAction(PlayerAction.TogglePlayPause) },
                    onControlsInteraction = { onAction(PlayerAction.ResetControlsTimer) },
                    onBackPressed = { onAction(PlayerAction.HideControls) },
                    firstButtonFocusRequester = firstButtonFocusRequester,
                    seekBarFocusRequester = seekBarFocusRequester,
                )

                // Layer 3: Settings panels
                AudioSubtitlesPanel(
                    visible = content.activePanel == ActivePanel.AudioSubtitles,
                    soundModes = content.soundModes,
                    selectedSoundModeIndex = content.selectedSoundModeIndex,
                    audioTracks = content.audioTracks,
                    selectedAudioTrackIndex = content.selectedAudioTrackIndex,
                    subtitleTracks = content.subtitleTracks,
                    selectedSubtitleIndex = content.selectedSubtitleIndex,
                    onSoundModeSelected = { onAction(PlayerAction.SelectSoundMode(it)) },
                    onAudioTrackSelected = { onAction(PlayerAction.SelectAudioTrack(it)) },
                    onSubtitleSelected = { onAction(PlayerAction.SelectSubtitle(it)) },
                    onSubtitleSizeClick = { onAction(PlayerAction.CycleSubtitleSize) },
                    onBackPressed = { onAction(PlayerAction.ClosePanel) },
                )

                VideoSettingsPanel(
                    visible = content.activePanel == ActivePanel.VideoSettings,
                    qualities = content.qualities,
                    selectedQualityIndex = content.selectedQualityIndex,
                    speeds = content.speeds,
                    selectedSpeedIndex = content.selectedSpeedIndex,
                    aspectRatios = content.aspectRatios,
                    selectedAspectRatioIndex = content.selectedAspectRatioIndex,
                    onQualitySelected = { onAction(PlayerAction.SelectQuality(it)) },
                    onSpeedSelected = { onAction(PlayerAction.SelectSpeed(it)) },
                    onAspectRatioSelected = { onAction(PlayerAction.SelectAspectRatio(it)) },
                    onBackPressed = { onAction(PlayerAction.ClosePanel) },
                )

                EpisodesPanel(
                    visible = content.activePanel == ActivePanel.Episodes,
                    episodes = content.episodes,
                    onEpisodeSelected = { item -> onAction(PlayerAction.SelectEpisodeById(item.id)) },
                    onBackPressed = { onAction(PlayerAction.ClosePanel) },
                )

                // Layer 4: Resume dialog
                ResumeDialog(
                    state = content.resumeDialog,
                    onResume = { onAction(PlayerAction.ResumeFromPosition) },
                    onStartFromBeginning = { onAction(PlayerAction.StartFromBeginning) },
                )

                // Layer 5: Next episode countdown
                NextEpisodeOverlay(countdown = content.nextEpisodeCountdown)
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(text = stringResource(R.string.player_error_retry))
            }
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(text = stringResource(R.string.player_error_back))
            }
        }
    }
}
