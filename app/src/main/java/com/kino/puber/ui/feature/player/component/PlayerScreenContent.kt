package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

    PlayerFocusEffects(
        content = contentState,
        focusRequesters = focusRequesters,
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
            )
        }
    }
}

@Composable
private fun PlayerFocusEffects(
    content: PlayerContentState?,
    focusRequesters: PlayerFocusRequesters,
) {
    val hasResumeDialog = content?.resumeDialog != null
    LaunchedEffect(content?.controlsVisible, content?.activePanel, hasResumeDialog) {
        if (hasResumeDialog) return@LaunchedEffect
        if (content != null && !content.controlsVisible && content.activePanel == ActivePanel.None) {
            runCatching { focusRequesters.player.requestFocus() }
        }
    }

    LaunchedEffect(content?.controlsVisible, content?.controlsFocusTarget, hasResumeDialog) {
        if (hasResumeDialog) return@LaunchedEffect
        if (content?.controlsVisible == true && content.controlsFocusTarget != null) {
            requestControlsFocus(content.controlsFocusTarget, focusRequesters)
        }
    }
}

private fun requestControlsFocus(
    target: FocusTarget,
    focusRequesters: PlayerFocusRequesters,
) {
    runCatching {
        when (target) {
            FocusTarget.SeekBar -> focusRequesters.seekBar.requestFocus()
            FocusTarget.Buttons -> focusRequesters.firstButton.requestFocus()
            FocusTarget.EpisodesButton -> focusRequesters.episodesButton.requestFocus()
            FocusTarget.AudioSubtitlesButton -> focusRequesters.audioSubtitlesButton.requestFocus()
            FocusTarget.VideoSettingsButton -> focusRequesters.videoSettingsButton.requestFocus()
        }
    }
}

@Composable
private fun PlayerContent(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    exoPlayer: () -> ExoPlayer?,
    focusRequesters: PlayerFocusRequesters,
) {
    PlayerVideoSurface(
        content = content,
        exoPlayer = exoPlayer,
        onAction = onAction,
        focusRequester = focusRequesters.player,
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
