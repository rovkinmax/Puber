package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun BufferingIndicator(
    visible: Boolean,
    speedBps: Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.7f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.7f),
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "buffering")

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rotation",
        )

        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )

        val primaryColor = MaterialTheme.colorScheme.primary
        val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

        val speedText = remember(speedBps) { formatSpeed(speedBps) }

        Box(contentAlignment = Alignment.Center) {
            // Rotating arc with gradient
            Canvas(modifier = Modifier.size(80.dp)) {
                val strokeWidth = 4.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background ring
                drawArc(
                    color = surfaceColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Spinning arc
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to primaryColor.copy(alpha = 0f),
                        0.7f to primaryColor.copy(alpha = pulse),
                        1f to primaryColor,
                    ),
                    startAngle = rotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            // Center text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (speedText.isNotEmpty()) {
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

private fun formatSpeed(bps: Long): String {
    if (bps <= 0) return ""
    val kbps = bps / 1000.0
    return if (kbps >= 1000) {
        "%.1f MB/s".format(kbps / 1000.0)
    } else {
        "%.0f KB/s".format(kbps)
    }
}
