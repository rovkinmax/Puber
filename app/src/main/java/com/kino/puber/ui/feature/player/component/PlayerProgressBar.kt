package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R

@Composable
internal fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isBuffering: Boolean = false,
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val progress = remember(currentPosition, duration) {
        if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }
    val bufferedProgress = remember(bufferedPosition, duration) {
        if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }
    val currentTimeText = remember(currentPosition) { formatTimeMs(currentPosition) }
    val remainingTimeText = remember(currentPosition, duration) {
        if (duration > 0) "-${formatTimeMs((duration - currentPosition).coerceAtLeast(0))}" else "-0:00"
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val infiniteTransition = rememberInfiniteTransition(label = "buffering_shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
        ) {
            Text(
                text = stringResource(R.string.player_buffering),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = currentTimeText,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                onSeekBackward()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                onSeekForward()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                onTogglePlayPause()
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Track background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(surfaceVariant.copy(alpha = 0.5f))
                )
                // Buffered (shimmer when buffering)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .then(
                            if (isBuffering) {
                                Modifier.drawBehind {
                                    drawRect(color = onSurfaceVariant.copy(alpha = 0.3f))
                                    val bandWidth = size.width * 0.4f
                                    val x = shimmerProgress * (size.width + bandWidth) - bandWidth
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                onSurfaceVariant.copy(alpha = 0f),
                                                onSurfaceVariant.copy(alpha = 0.5f),
                                                onSurfaceVariant.copy(alpha = 0f),
                                            ),
                                            start = Offset(x, 0f),
                                            end = Offset(x + bandWidth, 0f),
                                        ),
                                    )
                                }
                            } else {
                                Modifier.background(onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        )
                )
                // Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(primaryColor)
                )
                // Thumb
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtLeast(0.001f)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                }
            }

            Text(
                text = remainingTimeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
