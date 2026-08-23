package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.CalendarBlank
import com.adamglin.phosphoricons.fill.Eye
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.SkeletonAsyncImage
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
sealed interface VideoItemPresentation {
    data object Playable : VideoItemPresentation
    data object Scheduled : VideoItemPresentation
}

@Immutable
data class VideoItemUIState(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val bigImageUrl: String,
    val wideImageUrl: String = "",
    val imageFallbackUrls: List<String> = emptyList(),
    val showTitle: Boolean = false,
    val unwatchedCount: Int? = null,
    val ratings: List<RatingUIState> = emptyList(),
    val progressPercent: Float? = null,
    val isWatched: Boolean? = false,
    val showWatchedIndicator: Boolean = true,
    val isSeriesLike: Boolean = false,
    val isSaved: Boolean = false,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isSeasonWatched: Boolean? = null,
    val presentation: VideoItemPresentation = VideoItemPresentation.Playable,
    val scheduledReleaseDate: String? = null,
    val scheduledSubtitle: String? = null,
)

internal const val WATCHED_INDICATOR_TEST_TAG = "watched_indicator"
internal const val SCHEDULED_VIDEO_ITEM_TEST_TAG = "scheduled_video_item"

@Composable
fun VideoItem(
    modifier: Modifier = Modifier,
    state: VideoItemUIState,
    onClick: () -> Unit,
    onContextMenu: (() -> Unit)? = null,
) {
    val isPlayable = state.presentation == VideoItemPresentation.Playable
    Card(
        modifier = modifier.videoItemModifier(
            isPlayable = isPlayable,
            isScheduled = state.presentation == VideoItemPresentation.Scheduled,
            onContextMenu = onContextMenu,
        ),
        scale = CardDefaults.scale(pressedScale = 1f, focusedScale = 1f),
        onClick = {
            if (isPlayable) {
                onClick()
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.presentation == VideoItemPresentation.Scheduled) {
                ScheduledVideoItemContent(state)
            } else {
                PlayableVideoItemContent(state)
            }
        }
    }
}

@Composable
private fun Modifier.videoItemModifier(
    isPlayable: Boolean,
    isScheduled: Boolean,
    onContextMenu: (() -> Unit)?,
): Modifier = this
    .then(
        if (onContextMenu != null && isPlayable) {
            Modifier.onTvContextMenuKey(onOpen = onContextMenu)
        } else {
            Modifier
        }
    )
    .then(if (isScheduled) Modifier.testTag(SCHEDULED_VIDEO_ITEM_TEST_TAG) else Modifier)
    .size(
        PuberTheme.Defaults.VideoItemWidth,
        PuberTheme.Defaults.VideoItemHeight,
    )

@Composable
private fun BoxScope.PlayableVideoItemContent(state: VideoItemUIState) {
    VideoItemArtwork(state)
    UnwatchedCountBadge(
        count = state.unwatchedCount,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    WatchedIndicatorBadge(
        visible = state.isWatched == true && state.showWatchedIndicator,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    VideoItemMetadata(
        state = state,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

@Composable
private fun VideoItemArtwork(state: VideoItemUIState) {
    val context = LocalContext.current
    val urls = remember(state.imageUrl, state.imageFallbackUrls) {
        (listOf(state.imageUrl) + state.imageFallbackUrls)
            .filter { it.isNotBlank() }
            .distinct()
    }
    var urlIndex by remember(state.id, urls) { mutableIntStateOf(0) }
    val currentUrl = urls.getOrNull(urlIndex)
    val imageRequest = remember(currentUrl) {
        currentUrl?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build()
        }
    }
    SkeletonAsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = imageRequest,
        onError = {
            if (urlIndex < urls.lastIndex) {
                urlIndex++
            }
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun UnwatchedCountBadge(
    count: Int?,
    modifier: Modifier = Modifier,
) {
    if (count == null || count <= 0) return

    Box(
        modifier = modifier
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

@Composable
private fun VideoItemMetadata(
    state: VideoItemUIState,
    modifier: Modifier = Modifier,
) {
    val hasRatings = state.ratings.isNotEmpty()
    val hasTitle = state.showTitle && state.title.isNotEmpty()
    if (!hasRatings && !hasTitle) return

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
        modifier = modifier
            .fillMaxWidth()
            .background(gradientBrush)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (hasRatings) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.ratings.forEach { rating -> Rating(state = rating) }
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

@Composable
private fun ScheduledVideoItemContent(state: VideoItemUIState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Duotone.CalendarBlank,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        state.scheduledSubtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.scheduledReleaseDate?.takeIf { it.isNotBlank() }?.let { releaseDate ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = releaseDate,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WatchedIndicatorBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .testTag(WATCHED_INDICATOR_TEST_TAG)
            .padding(6.dp)
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.48F),
                RoundedCornerShape(6.dp),
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Fill.Eye,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9F),
        )
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
    progressPercent: Float? = null,
) = VideoItemUIState(
    id = 1,
    title = title,
    imageUrl = "",
    bigImageUrl = "",
    showTitle = showTitle,
    unwatchedCount = unwatchedCount,
    ratings = ratings,
    progressPercent = progressPercent,
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

@Preview(name = "Scheduled episode")
@Composable
private fun PreviewScheduledEpisode() = PuberTheme {
    VideoItem(
        state = VideoItemUIState(
            id = -1001,
            title = "Будущая серия",
            imageUrl = "",
            bigImageUrl = "",
            presentation = VideoItemPresentation.Scheduled,
            scheduledReleaseDate = "Дата выхода: 23.08.2026",
        ),
        onClick = {},
    )
}

// endregion
