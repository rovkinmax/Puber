package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SoundModeUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState

@Composable
internal fun AudioSubtitlesPanel(
    visible: Boolean,
    soundModes: List<SoundModeUIState>,
    selectedSoundModeIndex: Int,
    audioTracks: List<AudioTrackUIState>,
    selectedAudioTrackIndex: Int,
    subtitleTracks: List<SubtitleTrackUIState>,
    selectedSubtitleIndex: Int,
    onSoundModeSelected: (Int) -> Unit,
    onAudioTrackSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSubtitleSizeClick: () -> Unit,
    onBackPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                ) {
                    onBackPressed?.invoke()
                    true
                } else false
            },
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        val panelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try { panelFocusRequester.requestFocus() } catch (_: Exception) {}
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 48.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (soundModes.isNotEmpty()) {
                        SettingsPanelColumn(
                            header = stringResource(R.string.player_panel_sound),
                            items = soundModes.map { it.label },
                            selectedIndex = selectedSoundModeIndex,
                            onItemSelected = onSoundModeSelected,
                            modifier = Modifier.weight(1f),
                            firstItemFocusRequester = panelFocusRequester,
                        )
                    }

                    if (audioTracks.isNotEmpty()) {
                        SettingsPanelColumn(
                            header = stringResource(R.string.player_panel_audio),
                            items = audioTracks.map { it.label },
                            selectedIndex = selectedAudioTrackIndex,
                            onItemSelected = onAudioTrackSelected,
                            modifier = Modifier.weight(1f),
                            firstItemFocusRequester = if (soundModes.isEmpty()) panelFocusRequester else null,
                        )
                    }

                    SettingsPanelColumn(
                        header = stringResource(R.string.player_panel_subtitles),
                        items = subtitleTracks.map { it.label },
                        selectedIndex = selectedSubtitleIndex,
                        onItemSelected = onSubtitleSelected,
                        modifier = Modifier.weight(1f),
                    )
                }

                Button(
                    onClick = onSubtitleSizeClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.player_subtitle_size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
