package com.kino.puber.core.ui.uikit.component.modifier

import androidx.annotation.FloatRange
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.tv.material3.MaterialTheme
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.fade
import com.eygraber.compose.placeholder.placeholder
import com.kino.puber.core.ui.uikit.theme.PurpleGrey40

private const val PROGRESS_FOR_MAX_ALPHA: Float = 0.1F
val LocalSkeletonColor = staticCompositionLocalOf { PurpleGrey40 }


@Composable
fun Modifier.placeholder(
    visible: Boolean,
    color: Color = LocalSkeletonColor.current,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    @FloatRange(from = 0.0, to = 1.0) progressForMaxAlpha: Float = PROGRESS_FOR_MAX_ALPHA,
): Modifier = placeholder(
    visible = visible,
    shape = shape,
    color = color,
    highlight = PlaceholderHighlight.fade(
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        highlightColor = MaterialTheme.colorScheme.onBackground.copy(alpha = progressForMaxAlpha),
    ),
)