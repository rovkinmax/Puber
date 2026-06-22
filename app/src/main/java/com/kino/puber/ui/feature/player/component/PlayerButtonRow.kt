package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R

@Composable
internal fun PlayerButtonRow(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryControls(state, actions, focusRequesters)

        Spacer(modifier = Modifier.weight(1f))

        EpisodeNavigationButtons(state, actions)
    }
}

internal data class PlayerButtonRowState(
    val isMovie: Boolean,
    val isPlaying: Boolean,
    val hasNextEpisode: Boolean,
    val hasPreviousEpisode: Boolean,
)

@Composable
private fun PrimaryControls(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayPauseButton(
            isPlaying = state.isPlaying,
            onClick = actions.onTogglePlayPause,
            focusRequester = focusRequesters.firstButton,
        )
        if (!state.isMovie) {
            PlayerButton(
                text = stringResource(R.string.player_button_episodes),
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                onClick = actions.onEpisodesClick,
                modifier = Modifier.focusRequester(focusRequesters.episodesButton),
            )
        }
        PlayerButton(
            text = stringResource(R.string.player_button_audio_subtitles),
            icon = Icons.Default.Subtitles,
            onClick = actions.onAudioSubtitlesClick,
            modifier = Modifier.focusRequester(focusRequesters.audioSubtitlesButton),
        )
        PlayerButton(
            text = stringResource(R.string.player_button_video),
            icon = Icons.Default.Videocam,
            onClick = actions.onVideoSettingsClick,
            modifier = Modifier.focusRequester(focusRequesters.videoSettingsButton),
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.focusRequester(focusRequester),
        colors = transparentButtonColors(),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun EpisodeNavigationButtons(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
) {
    if (state.isMovie) return
    if (state.hasPreviousEpisode) {
        IconOnlyButton(
            icon = Icons.Default.SkipPrevious,
            onClick = actions.onPreviousEpisodeClick,
        )
    }
    if (state.hasNextEpisode) {
        IconOnlyButton(
            icon = Icons.Default.SkipNext,
            onClick = actions.onNextEpisodeClick,
        )
    }
}

@Composable
private fun IconOnlyButton(icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = transparentButtonColors(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun PlayerButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = transparentButtonColors(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun transparentButtonColors() = ButtonDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.primary,
    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
)
