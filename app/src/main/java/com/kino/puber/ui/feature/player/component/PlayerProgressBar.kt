package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
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
        if (duration > 0) "-${formatTimeMs(duration - currentPosition)}" else "-0:00"
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
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
            // Buffered
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(onSurfaceVariant.copy(alpha = 0.5f))
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
