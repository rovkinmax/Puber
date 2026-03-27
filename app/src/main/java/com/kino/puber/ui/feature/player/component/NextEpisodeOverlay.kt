package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R

private const val COUNTDOWN_TOTAL_SEC = 7

@Composable
internal fun NextEpisodeOverlay(
    countdown: Int?,
    onNextEpisode: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = countdown != null,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val nextButtonFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            try {
                nextButtonFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Row(
                modifier = Modifier
                    .padding(end = 32.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cancel button
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.player_next_episode_cancel),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                // Next episode button with progress fill
                NextEpisodeButton(
                    countdown = countdown ?: 0,
                    onClick = onNextEpisode,
                    focusRequester = nextButtonFocusRequester,
                )
            }
        }
    }
}

@Composable
private fun NextEpisodeButton(
    countdown: Int,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
    var targetProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(countdown) {
        targetProgress = 1f - (countdown.toFloat() / COUNTDOWN_TOTAL_SEC)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearEasing,
        ),
        label = "next_episode_progress",
    )
    val shape = RoundedCornerShape(50)
    val progressColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)

    Surface(
        onClick = onClick,
        modifier = Modifier.focusRequester(focusRequester),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Box {
            Text(
                text = stringResource(R.string.player_next_episode_countdown, countdown),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress)
                        .background(progressColor),
                )
            }
        }
    }

}