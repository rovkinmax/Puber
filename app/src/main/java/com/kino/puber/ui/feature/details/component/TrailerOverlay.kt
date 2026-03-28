package com.kino.puber.ui.feature.details.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme

@UnstableApi
@Composable
internal fun TrailerOverlay(
    url: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = url != null,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val context = LocalContext.current
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build()
        }

        DisposableEffect(url) {
            if (url != null) {
                exoPlayer.setMediaItem(MediaItem.fromUri(url.toUri()))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
            onDispose {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
