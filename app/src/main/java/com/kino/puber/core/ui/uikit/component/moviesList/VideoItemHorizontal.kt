package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun VideoItemHorizontal(
    modifier: Modifier = Modifier,
    state: VideoItemUIState,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(PuberTheme.Defaults.HorizontalVideoItemHeight)
            .aspectRatio(PuberTheme.Defaults.HorizontalVideoItemAspectRatio),
        onClick = onClick,
        scale = CardDefaults.scale(pressedScale = 1F, focusedScale = 1.05F),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val fallbackUrls = remember(state.id) {
                listOf(state.wideImageUrl, state.bigImageUrl, state.imageUrl)
                    .filter { it.isNotEmpty() }
            }
            var urlIndex by remember(state.id) { mutableIntStateOf(0) }
            val currentUrl = fallbackUrls.getOrNull(urlIndex)

            if (currentUrl != null) {
                val imageRequest = remember(currentUrl) {
                    ImageRequest.Builder(context)
                        .data(currentUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = imageRequest,
                    placeholder = rememberVectorPainter(Icons.Default.LocalMovies),
                    error = rememberVectorPainter(Icons.Default.LocalMovies),
                    onError = {
                        if (urlIndex < fallbackUrls.lastIndex) {
                            urlIndex++
                        }
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }

            val scrimColor = MaterialTheme.colorScheme.scrim
            val gradientBrush = remember(scrimColor) {
                Brush.verticalGradient(
                    colors = listOf(
                        scrimColor.copy(alpha = 0.2f),
                        scrimColor.copy(alpha = 0.9f),
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val count = state.unwatchedCount
                if (count != null && count > 0) {
                    Box(
                        modifier = Modifier
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
                    Spacer(Modifier.weight(1f))
                }

                if (state.ratings.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.ratings.forEach { rating ->
                            Rating(state = rating)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

            }

            val progressValue = state.progressPercent
            if (progressValue != null) {
                LinearProgressIndicator(
                    progress = { progressValue.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )
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

private fun previewState(
    title: String = "Рик и Морти / Rick and Morty",
    unwatchedCount: Int? = null,
    ratings: List<RatingUIState> = emptyList(),
    progressPercent: Float? = null,
) = VideoItemUIState(
    id = 1,
    title = title,
    imageUrl = "",
    bigImageUrl = "",
    showTitle = true,
    unwatchedCount = unwatchedCount,
    ratings = ratings,
    progressPercent = progressPercent,
)

@Preview(name = "Horizontal - All ratings")
@Composable
private fun PreviewHorizontalAllRatings() = PuberTheme {
    VideoItemHorizontal(state = previewState(ratings = allRatings), onClick = {})
}

@Preview(name = "Horizontal - Two ratings + Badge")
@Composable
private fun PreviewHorizontalTwoRatingsBadge() = PuberTheme {
    VideoItemHorizontal(
        state = previewState(ratings = twoRatings, unwatchedCount = 5),
        onClick = {},
    )
}

@Preview(name = "Horizontal - No ratings")
@Composable
private fun PreviewHorizontalNoRatings() = PuberTheme {
    VideoItemHorizontal(state = previewState(), onClick = {})
}

@Preview(name = "Horizontal - Long title")
@Composable
private fun PreviewHorizontalLongTitle() = PuberTheme {
    VideoItemHorizontal(
        state = previewState(
            title = "Невероятные приключения невероятного героя в невероятном мире",
            ratings = allRatings,
        ),
        onClick = {},
    )
}

// endregion