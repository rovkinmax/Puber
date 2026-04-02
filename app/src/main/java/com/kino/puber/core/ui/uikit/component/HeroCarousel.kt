package com.kino.puber.core.ui.uikit.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay

@Composable
fun HeroCarousel(
    items: List<HeroItemState>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    if (items.size == 1) {
        HeroItem(
            state = items.first(),
            onClick = { onItemClick(items.first().id) },
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp),
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, isFocused) {
        if (!isFocused) {
            while (true) {
                delay(5000)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next, animationSpec = tween(500))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .focusGroup(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            HeroItem(
                state = item,
                onClick = { onItemClick(item.id) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val isSelected = index == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = when {
                        isFocused && isSelected -> 24.dp
                        else -> 8.dp
                    },
                    label = "indicatorWidth",
                )
                val height by animateDpAsState(
                    targetValue = when {
                        isFocused && isSelected -> 8.dp
                        isFocused -> 6.dp
                        else -> 6.dp
                    },
                    label = "indicatorHeight",
                )
                val color by animateColorAsState(
                    targetValue = when {
                        isFocused && isSelected -> MaterialTheme.colorScheme.primary
                        isFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    label = "indicatorColor",
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .background(color = color, shape = RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
private fun HeroItem(
    state: HeroItemState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(pressedScale = 1f, focusedScale = 1f),
        border = CardDefaults.border(focusedBorder = Border.None, pressedBorder = Border.None),
        shape = CardDefaults.shape(RectangleShape),
    ) {
        Box(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val urls = remember(state.id) {
                listOf(state.wideImageUrl, state.fallbackImageUrl).filter { it.isNotEmpty() }
            }
            var urlIndex by remember(state.id) { mutableIntStateOf(0) }
            val currentUrl = urls.getOrNull(urlIndex)

            if (currentUrl != null) {
                val imageRequest = remember(currentUrl) {
                    ImageRequest.Builder(context)
                        .data(currentUrl)
                        .crossfade(true)
                        .build()
                }
                val driftDirection = remember(state.id) { if ((state.id % 2) == 0) 1f else -1f }
                val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 10_000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "kenBurnsScale",
                )
                val translateX by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 20f * driftDirection,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 10_000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "kenBurnsTranslateX",
                )
                AsyncImage(
                    model = imageRequest,
                    placeholder = rememberVectorPainter(Icons.Default.LocalMovies),
                    error = rememberVectorPainter(Icons.Default.LocalMovies),
                    onError = { if (urlIndex < urls.lastIndex) urlIndex++ },
                    contentDescription = null,
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = translateX
                        },
                )
            }

            val scrimColor = MaterialTheme.colorScheme.scrim
            val gradientBrush = remember(scrimColor) {
                Brush.verticalGradient(
                    colors = listOf(
                        scrimColor.copy(alpha = 0.1f),
                        scrimColor.copy(alpha = 0.85f),
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (state.ratings.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ratings.forEach { rating ->
                            Rating(rating)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                val infoLine = listOf(state.year, state.genres, state.country)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                if (infoLine.isNotEmpty()) {
                    Text(
                        text = infoLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (state.duration.isNotEmpty()) {
                    Text(
                        text = state.duration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}