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
        RequestResumeButtonFocus(resumeButtonFocusRequester)

        ResumeDialogScrim {
            state?.let {
                ResumeDialogCard(
                    state = it,
                    resumeButtonFocusRequester = resumeButtonFocusRequester,
                    onResume = onResume,
                    onStartFromBeginning = onStartFromBeginning,
                )
            }
        }
    }
}

@Composable
private fun RequestResumeButtonFocus(resumeButtonFocusRequester: FocusRequester) {
    LaunchedEffect(Unit) {
        try {
            resumeButtonFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun ResumeDialogScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ResumeDialogCard(
    state: ResumeDialogState,
    resumeButtonFocusRequester: FocusRequester,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(DIALOG_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surface)
            .padding(DIALOG_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DIALOG_ITEM_SPACING),
    ) {
        ResumeEpisodeInfo(state.episodeInfo)
        ResumeTitle(state.formattedTime)
        ResumeActions(
            resumeButtonFocusRequester = resumeButtonFocusRequester,
            onResume = onResume,
            onStartFromBeginning = onStartFromBeginning,
        )
    }
}

@Composable
private fun ResumeEpisodeInfo(episodeInfo: String?) {
    if (episodeInfo != null) {
        Text(
            text = episodeInfo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EPISODE_INFO_ALPHA),
        )
    }
}

@Composable
private fun ResumeTitle(formattedTime: String) {
    Text(
        text = stringResource(R.string.player_resume_title, formattedTime),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ResumeActions(
    resumeButtonFocusRequester: FocusRequester,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ACTION_SPACING)) {
        Button(
            onClick = onResume,
            modifier = Modifier.focusRequester(resumeButtonFocusRequester),
        ) {
            Text(text = stringResource(R.string.player_resume_continue))
        }

        Button(onClick = onStartFromBeginning) {
            Text(text = stringResource(R.string.player_resume_from_start))
        }
    }
}

private const val SCRIM_ALPHA = 0.6f
private const val EPISODE_INFO_ALPHA = 0.7f

private val DIALOG_CORNER_RADIUS = 16.dp
private val DIALOG_PADDING = 32.dp
private val DIALOG_ITEM_SPACING = 24.dp
private val ACTION_SPACING = 16.dp
