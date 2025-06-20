package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoItemUIState(
    val title: String,
    val imageUrl: String,
)

@Composable
fun VideoItem(
    modifier: Modifier = Modifier,
    state: VideoItemUIState,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .size(
                PuberTheme.Defaults.VideoItemWidth,
                PuberTheme.Defaults.VideoItemHeight,
            ),
        onClick = onClick,
        scale = CardDefaults.scale(pressedScale = 1F, focusedScale = 1.05F),
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = ImageRequest.Builder(LocalContext.current)
                .data(state.imageUrl)
                .crossfade(true)
                .build(),
            placeholder = rememberVectorPainter(Icons.Default.LocalMovies),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}