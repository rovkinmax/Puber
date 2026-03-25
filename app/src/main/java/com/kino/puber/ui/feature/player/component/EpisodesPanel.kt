package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Composable
internal fun EpisodesPanel(
    visible: Boolean,
    episodes: VideoGridUIState?,
    onEpisodeSelected: (VideoItemUIState) -> Unit,
    onBackPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(top = 32.dp),
        ) {
            if (episodes != null) {
                VideoGrid(
                    state = episodes,
                    onItemClick = onEpisodeSelected,
                    enableTopSideGradient = true,
                )
            }
        }
    }
}
