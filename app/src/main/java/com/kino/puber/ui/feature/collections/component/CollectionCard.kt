package com.kino.puber.ui.feature.collections.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.SkeletonAsyncImage
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.collections.model.CollectionUIState

@Composable
internal fun CollectionCard(
    state: CollectionUIState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(PuberTheme.Defaults.HorizontalVideoItemHeight)
            .aspectRatio(PuberTheme.Defaults.HorizontalVideoItemAspectRatio),
        onClick = onClick,
        scale = CardDefaults.scale(pressedScale = 1F, focusedScale = 1F),
    ) {
        Box(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val imageUrls = remember(state.wideImageUrl, state.imageUrl, state.fallbackImageUrls) {
                (listOf(state.wideImageUrl, state.imageUrl) + state.fallbackImageUrls)
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            var urlIndex by remember(state.id, imageUrls) { mutableIntStateOf(0) }
            val currentUrl = imageUrls.getOrNull(urlIndex)
            val imageRequest = remember(currentUrl) {
                currentUrl?.let {
                    ImageRequest.Builder(context)
                        .data(it)
                        .crossfade(true)
                        .build()
                }
            }
            SkeletonAsyncImage(
                model = imageRequest,
                onError = {
                    if (urlIndex < imageUrls.lastIndex) {
                        urlIndex++
                    }
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

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
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.count > 0) {
                    Text(
                        text = "${state.count} шт.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
