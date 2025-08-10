package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.theme.PuberTheme


@Composable
fun VideoItemGridDetails(
    modifier: Modifier,
    state: VideoDetailsUIState,
) {
    if (state.isLoading) {
        Row(modifier = modifier) {
            VideoDetailsDescription(
                modifier = Modifier.weight(3F),
                state = state
            )
            VideoDetailsPoster(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(5F),
                imageUrl = state.imageUrl,
            )
        }
    } else {
        Row(modifier = modifier) {
            VideoDetailsDescription(
                modifier = Modifier.weight(3F),
                state = state
            )
            VideoDetailsPoster(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(5F),
                imageUrl = state.imageUrl,
            )
        }
    }
}

@Composable
private fun VideoDetailsDescription(
    modifier: Modifier,
    state: VideoDetailsUIState
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .placeholder(visible = state.isLoading),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = state.isLoading),
                text = "${state.year}, ${state.genres} ${state.duration} ${state.country}",
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.description.split("\n").forEach { line ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .placeholder(
                                    visible = true,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .height(8.dp)
                        )
                    }
                }
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = state.description,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VideoDetailsPoster(
    modifier: Modifier,
    imageUrl: String,
) {
    Box(
        modifier = modifier,
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .placeholder(visible = imageUrl.isEmpty()),
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
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

@Composable
@Preview(device = TV_1080p)
private fun VideoItemGridLoadingPreview() = PuberTheme {
    Column {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3F),
            state = VideoDetailsUIState.Loading,
        )

        Box(Modifier.weight(2F))
    }
}