package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.ResumeDialogState

@Composable
internal fun ResumeDialog(
    state: ResumeDialogState?,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state != null,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val resumeButtonFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try {
                resumeButtonFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            state?.let {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (it.episodeInfo != null) {
                        Text(
                            text = it.episodeInfo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        text = stringResource(R.string.player_resume_title, it.formattedTime),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.focusRequester(resumeButtonFocusRequester),
                        ) {
                            Text(text = stringResource(R.string.player_resume_continue))
                        }

                        Button(
                            onClick = onStartFromBeginning,
                        ) {
                            Text(text = stringResource(R.string.player_resume_from_start))
                        }
                    }
                }
            }
        }
    }
}