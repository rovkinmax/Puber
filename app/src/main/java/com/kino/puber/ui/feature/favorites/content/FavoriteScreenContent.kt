package com.kino.puber.ui.feature.favorites.content

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState

@Composable
internal fun FavoriteScreenContent(
    state: FavoriteViewState,
    onAction: (UIAction) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            FavoriteViewState.Empty -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Empty")
            }

            is FavoriteViewState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.message)
            }

            FavoriteViewState.Loading -> FullScreenProgressIndicator()
            is FavoriteViewState.Content -> FavoriteScreenContentBody(
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun FavoriteScreenContentBody(
    state: FavoriteViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(mainContentFocus)
        ) {
            Box(modifier = Modifier.weight(2F)) {
                VideoItemGridDetails(
                    state = state.selectedItem,
                )
            }

            Row(Modifier.weight(2F)) {
                VideoGrid(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.gridState,
                    onItemClick = { onAction(CommonAction.ItemSelected(it)) },
                    onItemFocused = { onAction(CommonAction.ItemFocused(it)) },
                )
            }
        }
    }
}

@Composable
private fun VideoItemGridDetails(
    state: VideoDetailsUIState?,
) {
    Box(
        Modifier
            .fillMaxSize()
    ) {
        if (state != null) {
            Row(modifier = Modifier.fillMaxSize()) {
                VideoDetailsDescription(state)
                VideoDetailsPoster(state)
            }
        }

    }
}

@Composable
private fun RowScope.VideoDetailsDescription(state: VideoDetailsUIState) {
    Box(
        modifier = Modifier
            .weight(3F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                state.ratings.forEach { rating ->
                    Rating(rating)
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "${state.year}, ${state.genres} ${state.duration} ${state.country}",
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = state.description,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.VideoDetailsPoster(state: VideoDetailsUIState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .weight(5F),
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            model = ImageRequest.Builder(LocalContext.current)
                .data(state.imageUrl)
                .crossfade(true)
                .build(),
            placeholder = rememberVectorPainter(Icons.Default.LocalMovies),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
        )

        val gradientWidth = 48.dp
        Box(
            modifier = Modifier
                .width(gradientWidth)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.0F),
                        ),
                        endX = with(LocalDensity.current) { gradientWidth.toPx() },
                    )
                )
        )

        val gradientHeight = 48.dp
        Box(
            modifier = Modifier
                .height(gradientHeight)
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.0F),
                            MaterialTheme.colorScheme.surface,
                        ),
                        endY = with(LocalDensity.current) { gradientWidth.toPx() },
                    )
                )
        )
    }
}