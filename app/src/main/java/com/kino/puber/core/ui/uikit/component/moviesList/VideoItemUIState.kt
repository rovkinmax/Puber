package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoItemUIState(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val bigImageUrl: String,
    val showTitle: Boolean = false,
    val unwatchedCount: Int? = null,
    val ratings: List<RatingUIState> = emptyList(),
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
            val context = LocalContext.current
            val imageRequest = remember(state.imageUrl) {
                ImageRequest.Builder(context)
                    .data(state.imageUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = imageRequest,
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
            val hasRatings = state.ratings.isNotEmpty()
            val hasTitle = state.showTitle && state.title.isNotEmpty()
            if (hasRatings || hasTitle) {
                val scrimColor = MaterialTheme.colorScheme.scrim
                val gradientBrush = remember(scrimColor) {
                    Brush.verticalGradient(
                        colors = listOf(
                            scrimColor.copy(alpha = 0f),
                            scrimColor.copy(alpha = 0.85f),
                        ),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(gradientBrush)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (hasRatings) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.ratings.forEach { rating ->
                                Rating(state = rating)
                            }
                        }
                    }

                    if (hasTitle) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (hasRatings) 2 else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// region Previews

private val allRatings = listOf(
    RatingUIState.KP("8.2"),
    RatingUIState.IMDB("7.5"),
    RatingUIState.PUB("9.1"),
)

private val twoRatings = listOf(
    RatingUIState.KP("8.9"),
    RatingUIState.IMDB("9.0"),
)

private val singleRating = listOf(
    RatingUIState.KP("7.3"),
)

private fun previewState(
    title: String = "Рик и Морти / Rick and Morty",
    showTitle: Boolean = false,
    unwatchedCount: Int? = null,
    ratings: List<RatingUIState> = emptyList(),
) = VideoItemUIState(
    id = 1,
    title = title,
    imageUrl = "",
    bigImageUrl = "",
    showTitle = showTitle,
    unwatchedCount = unwatchedCount,
    ratings = ratings,
)

@Preview(name = "Ratings only (3)")
@Composable
private fun PreviewRatingsOnly() = PuberTheme {
    VideoItem(state = previewState(ratings = allRatings), onClick = {})
}

@Preview(name = "Ratings only (2)")
@Composable
private fun PreviewTwoRatings() = PuberTheme {
    VideoItem(state = previewState(ratings = twoRatings), onClick = {})
}

@Preview(name = "Single rating (KP)")
@Composable
private fun PreviewSingleRating() = PuberTheme {
    VideoItem(state = previewState(ratings = singleRating), onClick = {})
}

@Preview(name = "Ratings + Title")
@Composable
private fun PreviewRatingsWithTitle() = PuberTheme {
    VideoItem(
        state = previewState(
            ratings = allRatings,
            showTitle = true,
        ),
        onClick = {},
    )
}

@Preview(name = "Ratings + Title + Badge")
@Composable
private fun PreviewRatingsTitleBadge() = PuberTheme {
    VideoItem(
        state = previewState(
            ratings = twoRatings,
            showTitle = true,
            unwatchedCount = 5,
        ),
        onClick = {},
    )
}

@Preview(name = "Title only (no ratings)")
@Composable
private fun PreviewTitleOnly() = PuberTheme {
    VideoItem(
        state = previewState(showTitle = true),
        onClick = {},
    )
}

@Preview(name = "Long title + Ratings")
@Composable
private fun PreviewLongTitleWithRatings() = PuberTheme {
    VideoItem(
        state = previewState(
            title = "Невероятные приключения невероятного героя в невероятном мире / The Incredible Adventures",
            showTitle = true,
            ratings = allRatings,
        ),
        onClick = {},
    )
}

@Preview(name = "No ratings, no title (plain card)")
@Composable
private fun PreviewPlainCard() = PuberTheme {
    VideoItem(state = previewState(), onClick = {})
}

@Preview(name = "Badge only (no ratings)")
@Composable
private fun PreviewBadgeOnly() = PuberTheme {
    VideoItem(
        state = previewState(unwatchedCount = 12),
        onClick = {},
    )
}

// endregion