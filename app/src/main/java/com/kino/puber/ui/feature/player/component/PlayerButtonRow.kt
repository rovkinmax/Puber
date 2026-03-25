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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
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
    isMovie: Boolean,
    hasNextEpisode: Boolean,
    onEpisodesClick: () -> Unit,
    onAudioSubtitlesClick: () -> Unit,
    onVideoSettingsClick: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    firstButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isMovie) {
                PlayerButton(
                    text = stringResource(R.string.player_button_episodes),
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    onClick = onEpisodesClick,
                    modifier = Modifier.focusRequester(firstButtonFocusRequester),
                )
                PlayerButton(
                    text = stringResource(R.string.player_button_audio_subtitles),
                    icon = Icons.Default.Subtitles,
                    onClick = onAudioSubtitlesClick,
                )
            } else {
                PlayerButton(
                    text = stringResource(R.string.player_button_audio_subtitles),
                    icon = Icons.Default.Subtitles,
                    onClick = onAudioSubtitlesClick,
                    modifier = Modifier.focusRequester(firstButtonFocusRequester),
                )
            }
            PlayerButton(
                text = stringResource(R.string.player_button_video),
                icon = Icons.Default.Videocam,
                onClick = onVideoSettingsClick,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!isMovie && hasNextEpisode) {
            Button(
                onClick = onNextEpisodeClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
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
