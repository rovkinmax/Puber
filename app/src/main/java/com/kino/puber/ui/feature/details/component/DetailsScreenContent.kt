package com.kino.puber.ui.feature.details.component

import androidx.annotation.OptIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.CaretDown
import com.adamglin.phosphoricons.duotone.Eye
import com.adamglin.phosphoricons.fill.BookmarkSimple
import com.adamglin.phosphoricons.fill.Eye
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItem
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoRowUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.player.component.EpisodesPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DETAILS_CONTENT_WEIGHT = 3F
private const val SEASONS_PANEL_FOCUS_DELAY_MS = 150L
private const val DETAILS_BUTTONS_FOCUS_DELAY_MS = 100L
private const val DETAILS_PAGE_FOCUS_DELAY_MS = 50L
private const val CHEVRON_ALPHA = 0.5F
private const val INFO_DESCRIPTION_MAX_LINES = 7
private const val INFO_ROW_MAX_LINES = 3

@OptIn(UnstableApi::class)
@Composable
internal fun DetailsScreenContent(
    state: DetailsScreenState,
    onAction: (UIAction) -> Unit,
) {
    when (state) {
        is DetailsScreenState.Loading -> DetailsContentSkeleton()
        is DetailsScreenState.Error -> FullScreenError(
            error = state.message,
            onClick = { onAction(CommonAction.RetryClicked) },
        )

        is DetailsScreenState.Content -> {
            val seasonsPanelFocusRequester = remember { FocusRequester() }
            LaunchedEffect(state.seasonsPanelVisible) {
                if (state.seasonsPanelVisible) {
                    delay(SEASONS_PANEL_FOCUS_DELAY_MS)
                    try {
                        seasonsPanelFocusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                DetailsContentBody(
                    state = state,
                    onAction = onAction,
                )
                if (state.episodes != null) {
                    EpisodesPanel(
                        visible = state.seasonsPanelVisible,
                        episodes = state.episodes,
                        onEpisodeSelected = { item -> onAction(DetailsAction.EpisodeSelected(item)) },
                        onBackPressed = { onAction(DetailsAction.CloseSeasonsPanel) },
                        modifier = Modifier.focusRequester(seasonsPanelFocusRequester),
                    )
                }
                TrailerOverlay(
                    url = state.trailerUrl,
                )
            }
        }
    }
}

@Composable
private fun DetailsContentBody(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val infoPageFocusRequester = remember { FocusRequester() }
        val similarFirstItemFocusRequester = remember { FocusRequester() }
        val hasSimilarItems = state.similarItems.isNotEmpty()
        val focusInfoPage = remember {
            {
                coroutineScope.launch {
                    listState.scrollToItem(index = INFO_PAGE_INDEX)
                    delay(DETAILS_PAGE_FOCUS_DELAY_MS)
                    runCatching { infoPageFocusRequester.requestFocus() }
                }
                Unit
            }
        }
        val focusSimilarPage = remember {
            {
                coroutineScope.launch {
                    listState.scrollToItem(index = SIMILAR_PAGE_INDEX)
                    delay(DETAILS_PAGE_FOCUS_DELAY_MS)
                    runCatching { similarFirstItemFocusRequester.requestFocus() }
                }
                Unit
            }
        }
        val isMainPageVisible by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == MAIN_PAGE_INDEX &&
                    listState.firstVisibleItemScrollOffset == 0
            }
        }
        KeepFocusedChildVisibleWithoutRepositioning {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "main") {
                    DetailsMainPage(
                        modifier = Modifier.height(pageHeight),
                        state = state,
                        onAction = onAction,
                        seasonsPanelVisible = state.seasonsPanelVisible,
                        recoverActionFocus = isMainPageVisible,
                        scrollToMainPage = { listState.scrollToItem(index = 0) },
                    )
                }
                item(key = "info") {
                    DetailsInfoPage(
                        info = state.info,
                        hasNextPage = hasSimilarItems,
                        focusRequester = infoPageFocusRequester,
                        onNextPageRequested = focusSimilarPage,
                        modifier = Modifier.height(pageHeight),
                    )
                }
                if (hasSimilarItems) {
                    item(key = "similar") {
                        DetailsSimilarPage(
                            items = state.similarItems,
                            onAction = onAction,
                            firstItemFocusRequester = similarFirstItemFocusRequester,
                            onPreviousPageRequested = focusInfoPage,
                            modifier = Modifier.height(pageHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepFocusedChildVisibleWithoutRepositioning(
    content: @Composable () -> Unit,
) {
    val bringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val isAlreadyVisible = offset >= 0F && offset + size <= containerSize
                if (isAlreadyVisible) {
                    return 0F
                }

                val targetOffset = when {
                    offset < 0F -> 0F
                    size > containerSize -> 0F
                    else -> containerSize - size
                }
                return offset - targetOffset
            }
        }
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        content = content,
    )
}

@Composable
private fun DetailsMainPage(
    modifier: Modifier,
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    seasonsPanelVisible: Boolean,
    recoverActionFocus: Boolean,
    scrollToMainPage: suspend () -> Unit,
) {
    Column(modifier = modifier) {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(DETAILS_CONTENT_WEIGHT),
            state = state.details,
            descriptionMaxLines = FIRST_PAGE_DESCRIPTION_LINES,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionButtonsRow(
            buttons = state.buttons,
            isInWatchlist = state.isInWatchlist,
            isWatched = state.isWatched,
            onAction = onAction,
            seasonsPanelVisible = seasonsPanelVisible,
            trailerVisible = state.trailerUrl != null,
            recoverActionFocus = recoverActionFocus,
            scrollToMainPage = scrollToMainPage,
        )

        Spacer(modifier = Modifier.weight(1F))

        ChevronIndicator()
    }
}

@Composable
private fun ActionButtonsRow(
    buttons: List<DetailsButtonUIState>,
    isInWatchlist: Boolean,
    isWatched: Boolean,
    onAction: (UIAction) -> Unit,
    seasonsPanelVisible: Boolean,
    trailerVisible: Boolean,
    recoverActionFocus: Boolean,
    scrollToMainPage: suspend () -> Unit,
) {
    val firstButtonFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Request a concrete child, not the Row container. TV focus can otherwise
    // stay on the previous card/details area and make OK look unresponsive.
    LaunchedEffect(seasonsPanelVisible, trailerVisible, recoverActionFocus, buttons.size) {
        delay(DETAILS_BUTTONS_FOCUS_DELAY_MS)
        if (!seasonsPanelVisible && !trailerVisible && recoverActionFocus) {
            scrollToMainPage()
            runCatching { firstButtonFocusRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .onFocusChanged { focusState ->
                if (!seasonsPanelVisible && !trailerVisible && focusState.hasFocus) {
                    coroutineScope.launch { scrollToMainPage() }
                }
            }
            .focusRestorer(firstButtonFocusRequester)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, button ->
            DetailsActionButton(
                button = button,
                isInWatchlist = isInWatchlist,
                isWatched = isWatched,
                onAction = onAction,
                modifier = if (index == 0) Modifier.focusRequester(firstButtonFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun DetailsActionButton(
    button: DetailsButtonUIState,
    isInWatchlist: Boolean,
    isWatched: Boolean,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    when (button) {
        is DetailsButtonUIState.TextButton -> DetailsTextButton(button, onAction, modifier)
        is DetailsButtonUIState.IconOnly -> DetailsIconButton(button, onAction, modifier)
        is DetailsButtonUIState.WatchlistToggle -> DetailsWatchlistButton(button, isInWatchlist, onAction, modifier)
        is DetailsButtonUIState.WatchedToggle -> DetailsWatchedButton(button, isWatched, onAction, modifier)
    }
}

@Composable
private fun DetailsTextButton(
    button: DetailsButtonUIState.TextButton,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    Button(onClick = { onAction(button.action) }, modifier = modifier) {
        Icon(
            imageVector = button.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = button.textOverride ?: stringResource(button.textRes))
    }
}

@Composable
private fun DetailsIconButton(
    button: DetailsButtonUIState.IconOnly,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    IconButton(
        onClick = { onAction(button.action) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = button.icon,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DetailsWatchlistButton(
    button: DetailsButtonUIState.WatchlistToggle,
    checked: Boolean,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    IconButton(
        onClick = { onAction(button.action) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (checked) PhosphorIcons.Fill.BookmarkSimple else PhosphorIcons.Duotone.BookmarkSimple,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(20.dp),
            tint = if (checked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun DetailsWatchedButton(
    button: DetailsButtonUIState.WatchedToggle,
    checked: Boolean,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    IconButton(
        onClick = { onAction(button.action) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (checked) PhosphorIcons.Fill.Eye else PhosphorIcons.Duotone.Eye,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(20.dp),
            tint = if (checked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun DetailsInfoPage(
    info: DetailsInfoUIState,
    hasNextPage: Boolean,
    focusRequester: FocusRequester,
    onNextPageRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 96.dp, vertical = 72.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(64.dp),
            ) {
                DetailsInfoSummary(
                    info = info,
                    modifier = Modifier.weight(2F),
                )
                DetailsInfoText(
                    info = info,
                    modifier = Modifier.weight(5F),
                )
            }
        }

        if (hasNextPage) {
            PageFocusBridge(
                onFocused = onNextPageRequested,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp),
            )
            ChevronIndicator(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DetailsInfoSummary(
    info: DetailsInfoUIState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.video_details_info_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            info.ratings.forEach { rating ->
                Rating(rating)
            }
        }
        DetailsInfoRows(rows = info.primaryRows)
    }
}

@Composable
private fun DetailsInfoText(
    info: DetailsInfoUIState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            text = info.description,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis,
            maxLines = INFO_DESCRIPTION_MAX_LINES,
        )
        DetailsInfoRows(rows = info.secondaryRows)
    }
}

@Composable
private fun DetailsInfoRows(rows: List<DetailsInfoRowUIState>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54F),
                )
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = INFO_ROW_MAX_LINES,
                )
            }
        }
    }
}

@Composable
private fun DetailsSimilarPage(
    items: List<VideoItemUIState>,
    onAction: (UIAction) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onPreviousPageRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        PageFocusBridge(
            onFocused = onPreviousPageRequested,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(96.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp, vertical = 96.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.video_details_similar_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyRow(
                modifier = Modifier
                    .focusRestorer(firstItemFocusRequester)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 64.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    VideoItem(
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        },
                        state = item,
                        onClick = { onAction(DetailsAction.SimilarSelected(item)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageFocusBridge(
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // LazyColumn does not compose the next page until it scrolls into view, so
    // D-pad focus needs an already-composed target to trigger page transitions.
    Surface(
        onClick = onFocused,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = Color.Transparent,
            focusedContentColor = Color.Transparent,
            pressedContentColor = Color.Transparent,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ChevronIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Duotone.CaretDown,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .alpha(CHEVRON_ALPHA),
        )
    }
}

@Composable
private fun DetailsContentSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(DETAILS_CONTENT_WEIGHT),
            state = VideoDetailsUIState.Loading,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
        }

        Spacer(modifier = Modifier.weight(1F))

        ChevronIndicator()
    }
}

private const val FIRST_PAGE_DESCRIPTION_LINES = 3
private const val MAIN_PAGE_INDEX = 0
private const val INFO_PAGE_INDEX = 1
private const val SIMILAR_PAGE_INDEX = 2
