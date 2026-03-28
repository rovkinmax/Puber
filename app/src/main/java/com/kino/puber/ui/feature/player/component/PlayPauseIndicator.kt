package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.kino.puber.ui.feature.player.model.PlayPauseIndicatorState

@Composable
internal fun PlayPauseIndicator(
    state: PlayPauseIndicatorState?,
    modifier: Modifier = Modifier,
) {
    val lastState = remember { mutableStateOf(state) }
    SideEffect {
        if (state != null) {
            lastState.value = state
        }
    }
    AnimatedVisibility(
        visible = state != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        lastState.value?.let {
            Icon(
                imageVector = if (it.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}
