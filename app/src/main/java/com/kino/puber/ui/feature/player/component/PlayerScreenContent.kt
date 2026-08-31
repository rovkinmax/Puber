package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerViewState

@Composable
internal fun PlayerScreenContent(
    state: PlayerViewState,
    onAction: (UIAction) -> Unit,
    exoPlayer: () -> ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = rememberPlayerFocusRequesters()
    val contentState = (state as? PlayerViewState.Content)?.content
    val focusOwner = contentState?.focusOwner()
    var previousFocusOwner by remember { mutableStateOf(focusOwner) }
    var controlsTransferInProgress by remember { mutableStateOf(false) }
    val startsControlsTransfer =
        previousFocusOwner == PlayerFocusOwner.Player &&
            focusOwner == PlayerFocusOwner.Controls
    val retainPlayerAnchorFocus =
        focusOwner == PlayerFocusOwner.Controls &&
            (startsControlsTransfer || controlsTransferInProgress)
    val latestFocusOwner by rememberUpdatedState(focusOwner)

    SideEffect {
        when {
            startsControlsTransfer -> controlsTransferInProgress = true
            focusOwner != PlayerFocusOwner.Controls -> controlsTransferInProgress = false
        }
        previousFocusOwner = focusOwner
    }

    PlayerFocusEffects(
        content = contentState,
        focusRequesters = focusRequesters,
        onControlsFocusEstablished = {
            if (latestFocusOwner == PlayerFocusOwner.Controls) {
                controlsTransferInProgress = false
            }
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        when (state) {
            is PlayerViewState.Loading -> FullScreenProgressIndicator()
            is PlayerViewState.Error -> ErrorOverlay(
                message = state.message,
                onRetry = { onAction(PlayerAction.RetryPlayback) },
                onBack = { onAction(PlayerAction.OnBackPressed) },
            )
            is PlayerViewState.Content -> PlayerContent(
                content = state.content,
                onAction = onAction,
                exoPlayer = exoPlayer,
                focusRequesters = focusRequesters,
                retainPlayerAnchorFocus = retainPlayerAnchorFocus,
            )
        }
    }
}

@Composable
private fun PlayerFocusEffects(
    content: PlayerContentState?,
    focusRequesters: PlayerFocusRequesters,
    onControlsFocusEstablished: () -> Unit,
) {
    val focusOwner = content?.focusOwner()
    val controlsFocusTarget = content?.controlsFocusTarget ?: FocusTarget.Buttons
    val latestContent by rememberUpdatedState(content)
    val latestOnControlsFocusEstablished by rememberUpdatedState(onControlsFocusEstablished)

    LaunchedEffect(focusOwner) {
        if (focusOwner == PlayerFocusOwner.Player) {
            focusRequesters.player.requestWhenAttached(
                isOwner = {
                    latestContent?.focusOwner() == PlayerFocusOwner.Player
                },
            )
        }
    }

    LaunchedEffect(focusOwner, controlsFocusTarget) {
        if (focusOwner == PlayerFocusOwner.Controls) {
            val focusEstablished = requestControlsFocus(
                target = controlsFocusTarget,
                focusRequesters = focusRequesters,
                isOwner = {
                    latestContent?.let {
                        it.focusOwner() == PlayerFocusOwner.Controls &&
                            (it.controlsFocusTarget ?: FocusTarget.Buttons) == controlsFocusTarget
                    } == true
                },
            )
            if (focusEstablished) {
                latestOnControlsFocusEstablished()
            }
        }
    }
}

private suspend fun requestControlsFocus(
    target: FocusTarget,
    focusRequesters: PlayerFocusRequesters,
    isOwner: () -> Boolean,
): Boolean {
    val (focusRequester, fallback) = when (target) {
        FocusTarget.SeekBar -> focusRequesters.seekBar to focusRequesters.firstButton
        FocusTarget.Buttons -> focusRequesters.firstButton to focusRequesters.seekBar
        FocusTarget.EpisodesButton -> focusRequesters.episodesButton to focusRequesters.firstButton
        FocusTarget.AudioSubtitlesButton ->
            focusRequesters.audioSubtitlesButton to focusRequesters.firstButton
        FocusTarget.VideoSettingsButton ->
            focusRequesters.videoSettingsButton to focusRequesters.firstButton
    }
    return focusRequester.requestWhenAttached(
        isOwner = isOwner,
        fallback = fallback,
    )
}

private enum class PlayerFocusOwner {
    ResumeDialog,
    Panel,
    Controls,
    Player,
}

private fun PlayerContentState.focusOwner(): PlayerFocusOwner {
    return when {
        resumeDialog != null -> PlayerFocusOwner.ResumeDialog
        activePanel != ActivePanel.None -> PlayerFocusOwner.Panel
        controlsVisible -> PlayerFocusOwner.Controls
        else -> PlayerFocusOwner.Player
    }
}

@Composable
private fun PlayerContent(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    exoPlayer: () -> ExoPlayer?,
    focusRequesters: PlayerFocusRequesters,
    retainPlayerAnchorFocus: Boolean,
) {
    PlayerVideoSurface(
        content = content,
        exoPlayer = exoPlayer,
    )
    PlayerFocusAnchor(
        content = content,
        onAction = onAction,
        focusRequester = focusRequesters.player,
        retainFocus = retainPlayerAnchorFocus,
    )
    PlaybackFeedbackLayer(content = content)
    BufferingProgressLayer(content = content)
    PlayerControlsLayer(
        content = content,
        onAction = onAction,
        focusRequesters = focusRequesters,
    )
    PlayerSettingsPanels(content = content, onAction = onAction)
    PlayerOverlayLayers(content = content, onAction = onAction)
}

@Composable
private fun PlayerFocusAnchor(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
    retainFocus: Boolean,
) {
    val isFocusOwner =
        content.focusOwner() == PlayerFocusOwner.Player || retainFocus
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable(enabled = isFocusOwner)
            .then(
                if (isFocusOwner) {
                    Modifier.onKeyEvent { keyEvent ->
                        handlePlayerKeyEvent(
                            keyEvent = keyEvent.nativeKeyEvent,
                            onAction = onAction,
                        )
                    }
                } else {
                    Modifier
                },
            ),
    )
}

private fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    onAction: (UIAction) -> Unit,
): Boolean {
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
    return when (keyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> {
            onAction(PlayerAction.ShowControls(FocusTarget.SeekBar))
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            onAction(PlayerAction.ShowControls(FocusTarget.Buttons))
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            onAction(PlayerAction.SeekBackward)
            onAction(PlayerAction.ShowControls(FocusTarget.SeekBar))
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            onAction(PlayerAction.SeekForward)
            onAction(PlayerAction.ShowControls(FocusTarget.SeekBar))
            true
        }
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            onAction(PlayerAction.TogglePlayPause)
            onAction(PlayerAction.ShowControls(FocusTarget.Buttons))
            true
        }
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
            onAction(PlayerAction.SeekForward)
            onAction(PlayerAction.ShowControls(FocusTarget.SeekBar))
            true
        }
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
            onAction(PlayerAction.SeekBackward)
            onAction(PlayerAction.ShowControls(FocusTarget.SeekBar))
            true
        }
        else -> false
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { retryFocusRequester.requestFocus() }
    }

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
            ErrorMessage(message)
            ErrorButton(
                text = stringResource(R.string.player_error_retry),
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocusRequester),
                primary = true,
            )
            ErrorButton(
                text = stringResource(R.string.player_error_back),
                onClick = onBack,
                primary = false,
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ErrorButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = if (primary) {
        ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
    ) {
        Text(text = text)
    }
}
