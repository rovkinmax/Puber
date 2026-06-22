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
import java.util.Locale

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
    val progress = remember(currentPosition, duration) { progressOf(currentPosition, duration) }
    val bufferedProgress = remember(bufferedPosition, duration) { progressOf(bufferedPosition, duration) }
    val currentTimeText = remember(currentPosition) { formatTimeMs(currentPosition) }
    val remainingTimeText = remember(currentPosition, duration) { formatRemainingTime(currentPosition, duration) }

    val infiniteTransition = rememberInfiniteTransition(label = "buffering_shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PROGRESS_HORIZONTAL_PADDING),
    ) {
        BufferingLabel(isBuffering = isBuffering)
        ProgressBarRow(
            currentTimeText = currentTimeText,
            remainingTimeText = remainingTimeText,
            progress = progress,
            bufferedProgress = bufferedProgress,
            shimmerProgress = shimmerProgress,
            isBuffering = isBuffering,
            focusRequester = focusRequester,
            onSeekForward = onSeekForward,
            onSeekBackward = onSeekBackward,
            onTogglePlayPause = onTogglePlayPause,
        )
    }
}

@Composable
private fun BufferingLabel(isBuffering: Boolean) {
    AnimatedVisibility(
        visible = isBuffering,
        enter = fadeIn(tween(BUFFERING_FADE_IN_MS)),
        exit = fadeOut(tween(BUFFERING_FADE_OUT_MS)),
    ) {
        Text(
            text = stringResource(R.string.player_buffering),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = BUFFERING_LABEL_ALPHA),
            modifier = Modifier.padding(bottom = BUFFERING_LABEL_BOTTOM_PADDING),
        )
    }
}

@Composable
private fun ProgressBarRow(
    currentTimeText: String,
    remainingTimeText: String,
    progress: Float,
    bufferedProgress: Float,
    shimmerProgress: Float,
    isBuffering: Boolean,
    focusRequester: FocusRequester,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PROGRESS_ROW_SPACING),
    ) {
        CurrentTimeText(currentTimeText)
        SeekTrack(
            progress = progress,
            bufferedProgress = bufferedProgress,
            shimmerProgress = shimmerProgress,
            isBuffering = isBuffering,
            focusRequester = focusRequester,
            onSeekForward = onSeekForward,
            onSeekBackward = onSeekBackward,
            onTogglePlayPause = onTogglePlayPause,
            modifier = Modifier.weight(1f),
        )
        RemainingTimeText(remainingTimeText)
    }
}

@Composable
private fun CurrentTimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RemainingTimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SeekTrack(
    progress: Float,
    bufferedProgress: Float,
    shimmerProgress: Float,
    isBuffering: Boolean,
    focusRequester: FocusRequester,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(TRACK_TOUCH_HEIGHT)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handleProgressKeyEvent(
                    keyCode = keyEvent.nativeKeyEvent.keyCode,
                    action = keyEvent.nativeKeyEvent.action,
                    onSeekForward = onSeekForward,
                    onSeekBackward = onSeekBackward,
                    onTogglePlayPause = onTogglePlayPause,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        TrackBackground()
        BufferedTrack(
            bufferedProgress = bufferedProgress,
            shimmerProgress = shimmerProgress,
            isBuffering = isBuffering,
        )
        PlayedTrack(progress = progress)
        ProgressThumb(progress = progress)
    }
}

@Composable
private fun TrackBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .clip(RoundedCornerShape(TRACK_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TRACK_ALPHA)),
    )
}

@Composable
private fun BufferedTrack(bufferedProgress: Float, shimmerProgress: Float, isBuffering: Boolean) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth(bufferedProgress)
            .height(TRACK_HEIGHT)
            .clip(RoundedCornerShape(TRACK_CORNER_RADIUS))
            .then(
                if (isBuffering) {
                    Modifier.drawBehind {
                        drawRect(color = onSurfaceVariant.copy(alpha = BUFFERING_TRACK_ALPHA))
                        val bandWidth = size.width * SHIMMER_BAND_WIDTH_FRACTION
                        val x = shimmerProgress * (size.width + bandWidth) - bandWidth
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    onSurfaceVariant.copy(alpha = 0f),
                                    onSurfaceVariant.copy(alpha = SHIMMER_PEAK_ALPHA),
                                    onSurfaceVariant.copy(alpha = 0f),
                                ),
                                start = Offset(x, 0f),
                                end = Offset(x + bandWidth, 0f),
                            ),
                        )
                    }
                } else {
                    Modifier.background(onSurfaceVariant.copy(alpha = TRACK_ALPHA))
                }
            ),
    )
}

@Composable
private fun PlayedTrack(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(progress)
            .height(TRACK_HEIGHT)
            .clip(RoundedCornerShape(TRACK_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ProgressThumb(progress: Float) {
    Box(
        modifier = Modifier.fillMaxWidth(progress.coerceAtLeast(MIN_THUMB_PROGRESS)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .size(THUMB_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun handleProgressKeyEvent(
    keyCode: Int,
    action: Int,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayPause: () -> Unit,
): Boolean {
    if (action != KeyEvent.ACTION_DOWN) return false
    return when (keyCode) {
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
}

private fun progressOf(position: Long, duration: Long): Float {
    return if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private fun formatRemainingTime(currentPosition: Long, duration: Long): String {
    return if (duration > 0) {
        "-${formatTimeMs((duration - currentPosition).coerceAtLeast(0))}"
    } else {
        EMPTY_REMAINING_TIME
    }
}

private const val EMPTY_REMAINING_TIME = "-0:00"
private const val SHIMMER_DURATION_MS = 1_500
private const val BUFFERING_FADE_IN_MS = 300
private const val BUFFERING_FADE_OUT_MS = 200
private const val BUFFERING_LABEL_ALPHA = 0.7f
private const val TRACK_ALPHA = 0.5f
private const val BUFFERING_TRACK_ALPHA = 0.3f
private const val SHIMMER_PEAK_ALPHA = 0.5f
private const val SHIMMER_BAND_WIDTH_FRACTION = 0.4f
private const val MIN_THUMB_PROGRESS = 0.001f
private const val MILLIS_PER_SECOND = 1_000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

private val BUFFERING_LABEL_BOTTOM_PADDING = 4.dp
private val PROGRESS_HORIZONTAL_PADDING = 24.dp
private val PROGRESS_ROW_SPACING = 12.dp
private val TRACK_TOUCH_HEIGHT = 12.dp
private val TRACK_HEIGHT = 4.dp
private val TRACK_CORNER_RADIUS = 2.dp
private val THUMB_SIZE = 12.dp
