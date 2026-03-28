package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoItemUIState(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val bigImageUrl: String,
    val showTitle: Boolean = false,
    val unwatchedCount: Int? = null,
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
        Box(modifier = Modifier.fillMaxSize()) {
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
            val count = state.unwatchedCount
            if (count != null && count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            if (state.showTitle && state.title.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f),
                                ),
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}