package com.kino.puber.ui.feature.details.component

import androidx.annotation.OptIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.adamglin.phosphoricons.duotone.CaretUp
import com.adamglin.phosphoricons.duotone.Eye
import com.adamglin.phosphoricons.fill.BookmarkSimple
import com.adamglin.phosphoricons.fill.Eye
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.EpisodeContextMenuDialog
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItem
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsCastMemberUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoRowUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.player.component.EpisodesPanel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DETAILS_CONTENT_WEIGHT = 3F
private const val SEASONS_PANEL_FOCUS_DELAY_MS = 150L
private const val DETAILS_BUTTONS_FOCUS_DELAY_MS = 100L
private const val DETAILS_PAGE_FOCUS_DELAY_MS = 50L
private const val CHEVRON_ALPHA = 0.5F
private const val INFO_ROW_MAX_LINES = 2
private const val ACTOR_CARD_WIDTH = 112
private const val ACTOR_CARD_HEIGHT = 132
private const val ACTOR_PORTRAIT_SIZE = 72
internal const val ACTOR_PORTRAIT_TEST_TAG_PREFIX = "details_actor_portrait:"
private val INFO_CHIP_FOCUS_SAFE_PADDING = 8.dp
private val INFO_PAGE_SCROLL_STEP = 240.dp
private val DETAILS_PAGE_PEEK_HEIGHT = 32.dp
private val PAGE_FOCUS_BRIDGE_HEIGHT = 56.dp

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

        is DetailsScreenState.Content -> DetailsLoadedContent(state, onAction)
    }
}

@Composable
private fun DetailsLoadedContent(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
) {
    val seasonsPanelFocusRequester = remember { FocusRequester() }
    var episodeContextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }
    LaunchedEffect(state.seasonsPanelVisible, state.initialEpisodeFocusId) {
        if (state.seasonsPanelVisible && state.initialEpisodeFocusId == null) {
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
            onEpisodeContextMenu = { episodeContextMenuItem = it },
        )
        ScheduleAwareEpisodesPanel(
            state = state,
            contextMenuItem = episodeContextMenuItem,
            focusRequester = seasonsPanelFocusRequester,
            onAction = onAction,
            onContextMenuItemChanged = { episodeContextMenuItem = it },
        )
        EpisodeContextMenuDialog(
            episode = episodeContextMenuItem,
            onDismiss = { episodeContextMenuItem = null },
            onPlay = { onAction(DetailsAction.EpisodeSelected(it)) },
            onMarkEpisodeWatched = { item, watched ->
                onAction(DetailsAction.EpisodeWatchedChanged(item, watched))
            },
            onMarkSeasonWatched = { item, watched ->
                onAction(DetailsAction.SeasonWatchedChanged(item, watched))
            },
        )
        TrailerOverlay(url = state.trailerUrl)
    }
}

@Composable
private fun ScheduleAwareEpisodesPanel(
    state: DetailsScreenState.Content,
    contextMenuItem: VideoItemUIState?,
    focusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
    onContextMenuItemChanged: (VideoItemUIState?) -> Unit,
) {
    val episodes = state.episodes ?: return
    EpisodesPanel(
        visible = state.seasonsPanelVisible,
        episodes = episodes,
        initialFocusedItemId = state.initialEpisodeFocusId,
        onEpisodeSelected = { item ->
            if (item.presentation == VideoItemPresentation.Playable) {
                onAction(DetailsAction.EpisodeSelected(item))
            }
        },
        onEpisodeContextMenu = { item ->
            if (item.presentation == VideoItemPresentation.Playable) {
                onContextMenuItemChanged(item)
            }
        },
        onBackPressed = { onAction(DetailsAction.CloseSeasonsPanel) },
        allowFocusExit = contextMenuItem != null,
        modifier = Modifier.focusRequester(focusRequester),
    )
}

@Composable
private fun DetailsContentBody(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageCount = if (state.similarItems.isNotEmpty()) DETAILS_PAGES_WITH_SIMILAR else DETAILS_PAGES_BASE
        val pagerState = rememberPagerState(pageCount = { pageCount })
        val infoPageFocusRequester = remember { FocusRequester() }
        val similarFirstItemFocusRequester = remember { FocusRequester() }
        val hasSimilarItems = state.similarItems.isNotEmpty()
        val focusActions = rememberDetailsPageFocusActions(
            pagerState = pagerState,
            infoPageFocusRequester = infoPageFocusRequester,
            similarFirstItemFocusRequester = similarFirstItemFocusRequester,
        )
        val isMainPageVisible by remember {
            derivedStateOf {
                pagerState.currentPage == MAIN_PAGE_INDEX &&
                    pagerState.currentPageOffsetFraction == 0F
            }
        }
        val currentPage by remember {
            derivedStateOf { pagerState.currentPage }
        }
        RequestCurrentDetailsPageFocus(
            currentPage = pagerState.currentPage,
            castCardCount = state.info.castCards.size,
            hasSimilarItems = hasSimilarItems,
            infoPageFocusRequester = infoPageFocusRequester,
            similarFirstItemFocusRequester = similarFirstItemFocusRequester,
        )
        DetailsPager(
            pagerState = pagerState,
            state = state,
            currentPage = currentPage,
            isMainPageVisible = isMainPageVisible,
            hasSimilarItems = hasSimilarItems,
            focusActions = focusActions,
            infoPageFocusRequester = infoPageFocusRequester,
            similarFirstItemFocusRequester = similarFirstItemFocusRequester,
            onAction = onAction,
            onEpisodeContextMenu = onEpisodeContextMenu,
        )
    }
}

private data class DetailsPageFocusActions(
    val focusMainPage: () -> Unit,
    val focusInfoPage: () -> Unit,
    val focusSimilarPage: () -> Unit,
)

@Composable
private fun rememberDetailsPageFocusActions(
    pagerState: PagerState,
    infoPageFocusRequester: FocusRequester,
    similarFirstItemFocusRequester: FocusRequester,
): DetailsPageFocusActions {
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, infoPageFocusRequester, similarFirstItemFocusRequester, coroutineScope) {
        DetailsPageFocusActions(
            focusMainPage = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(MAIN_PAGE_INDEX)
                }
                Unit
            },
            focusInfoPage = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(INFO_PAGE_INDEX)
                    delay(DETAILS_PAGE_FOCUS_DELAY_MS)
                    runCatching { infoPageFocusRequester.requestFocus() }
                }
                Unit
            },
            focusSimilarPage = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(SIMILAR_PAGE_INDEX)
                    delay(DETAILS_PAGE_FOCUS_DELAY_MS)
                    runCatching { similarFirstItemFocusRequester.requestFocus() }
                }
                Unit
            },
        )
    }
}

@Composable
private fun RequestCurrentDetailsPageFocus(
    currentPage: Int,
    castCardCount: Int,
    hasSimilarItems: Boolean,
    infoPageFocusRequester: FocusRequester,
    similarFirstItemFocusRequester: FocusRequester,
) {
    LaunchedEffect(currentPage, castCardCount, hasSimilarItems) {
        delay(DETAILS_PAGE_FOCUS_DELAY_MS)
        when (currentPage) {
            INFO_PAGE_INDEX -> runCatching { infoPageFocusRequester.requestFocus() }
            SIMILAR_PAGE_INDEX -> if (hasSimilarItems) {
                runCatching { similarFirstItemFocusRequester.requestFocus() }
            }
        }
    }
}

@Composable
private fun DetailsPager(
    pagerState: PagerState,
    state: DetailsScreenState.Content,
    currentPage: Int,
    isMainPageVisible: Boolean,
    hasSimilarItems: Boolean,
    focusActions: DetailsPageFocusActions,
    infoPageFocusRequester: FocusRequester,
    similarFirstItemFocusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
) {
    KeepFocusedChildVisibleWithoutRepositioning {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = DETAILS_PAGE_PEEK_HEIGHT),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                MAIN_PAGE_INDEX -> DetailsMainPage(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onAction = onAction,
                    onEpisodeContextMenu = onEpisodeContextMenu,
                    seasonsPanelVisible = state.seasonsPanelVisible,
                    recoverActionFocus = isMainPageVisible,
                    showPageChevron = currentPage == MAIN_PAGE_INDEX,
                    scrollToMainPage = { pagerState.animateScrollToPage(MAIN_PAGE_INDEX) },
                )
                INFO_PAGE_INDEX -> DetailsInfoPage(
                    info = state.info,
                    hasNextPage = hasSimilarItems,
                    showPageChevrons = currentPage == INFO_PAGE_INDEX,
                    focusRequester = infoPageFocusRequester,
                    onAction = onAction,
                    onPreviousPageRequested = focusActions.focusMainPage,
                    onNextPageRequested = focusActions.focusSimilarPage,
                    modifier = Modifier.fillMaxSize(),
                )
                SIMILAR_PAGE_INDEX -> DetailsSimilarPage(
                    items = state.similarItems,
                    onAction = onAction,
                    firstItemFocusRequester = similarFirstItemFocusRequester,
                    onPreviousPageRequested = focusActions.focusInfoPage,
                    showPageChevron = currentPage == SIMILAR_PAGE_INDEX,
                    modifier = Modifier.fillMaxSize(),
                )
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
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    seasonsPanelVisible: Boolean,
    recoverActionFocus: Boolean,
    showPageChevron: Boolean,
    scrollToMainPage: suspend () -> Unit,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(DETAILS_CONTENT_WEIGHT),
        ) {
            VideoItemGridDetails(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F),
                state = state.details,
                descriptionMaxLines = FIRST_PAGE_DESCRIPTION_LINES,
            )
            state.seriesStatus?.let { status ->
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72F),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ActionButtonsRow(
            buttons = state.buttons,
            isInWatchlist = state.isInWatchlist,
            isWatched = state.isWatched,
            onAction = onAction,
            currentEpisode = state.currentEpisode,
            onEpisodeContextMenu = onEpisodeContextMenu,
            seasonsPanelVisible = seasonsPanelVisible,
            trailerVisible = state.trailerUrl != null,
            recoverActionFocus = recoverActionFocus,
            scrollToMainPage = scrollToMainPage,
        )

        Spacer(modifier = Modifier.weight(1F))

        if (showPageChevron) {
            ChevronIndicator()
        }
    }
}

@Composable
private fun ActionButtonsRow(
    buttons: List<DetailsButtonUIState>,
    isInWatchlist: Boolean,
    isWatched: Boolean,
    onAction: (UIAction) -> Unit,
    currentEpisode: VideoItemUIState?,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
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
                currentEpisode = currentEpisode,
                onEpisodeContextMenu = onEpisodeContextMenu,
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
    currentEpisode: VideoItemUIState?,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    modifier: Modifier,
) {
    when (button) {
        is DetailsButtonUIState.TextButton -> DetailsTextButton(
            button = button,
            onAction = onAction,
            currentEpisode = currentEpisode,
            onEpisodeContextMenu = onEpisodeContextMenu,
            modifier = modifier,
        )
        is DetailsButtonUIState.IconOnly -> DetailsIconButton(button, onAction, modifier)
        is DetailsButtonUIState.WatchlistToggle -> DetailsWatchlistButton(button, isInWatchlist, onAction, modifier)
        is DetailsButtonUIState.WatchedToggle -> DetailsWatchedButton(button, isWatched, onAction, modifier)
    }
}

@Composable
private fun DetailsTextButton(
    button: DetailsButtonUIState.TextButton,
    onAction: (UIAction) -> Unit,
    currentEpisode: VideoItemUIState?,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    modifier: Modifier,
) {
    val buttonModifier = if (button.action == DetailsAction.PlayClicked && currentEpisode != null) {
        modifier.onTvContextMenuKey(onOpen = { onEpisodeContextMenu(currentEpisode) })
    } else {
        modifier
    }
    Button(onClick = { onAction(button.action) }, modifier = buttonModifier) {
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
internal fun DetailsInfoPage(
    info: DetailsInfoUIState,
    hasNextPage: Boolean,
    showPageChevrons: Boolean,
    focusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
    onPreviousPageRequested: () -> Unit,
    onNextPageRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        DetailsInfoTopNavigation(
            showPageChevrons = showPageChevrons,
            onPreviousPageRequested = onPreviousPageRequested,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        DetailsInfoContent(
            info = info,
            hasNextPage = hasNextPage,
            focusRequester = focusRequester,
            onAction = onAction,
            onPreviousPageRequested = onPreviousPageRequested,
            onNextPageRequested = onNextPageRequested,
        )
        if (hasNextPage) {
            DetailsInfoBottomNavigation(
                showPageChevrons = showPageChevrons,
                onNextPageRequested = onNextPageRequested,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DetailsInfoTopNavigation(
    showPageChevrons: Boolean,
    onPreviousPageRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageFocusBridge(
        enabled = showPageChevrons,
        onFocused = onPreviousPageRequested,
        modifier = modifier
            .fillMaxWidth()
            .height(PAGE_FOCUS_BRIDGE_HEIGHT),
    )
    if (showPageChevrons) {
        ChevronIndicator(
            direction = ChevronDirection.Up,
            modifier = modifier,
        )
    }
}

@Composable
private fun DetailsInfoContent(
    info: DetailsInfoUIState,
    hasNextPage: Boolean,
    focusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
    onPreviousPageRequested: () -> Unit,
    onNextPageRequested: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollActions = rememberDetailsInfoScrollActions(
        scrollState = scrollState,
        hasNextPage = hasNextPage,
        onPreviousPageRequested = onPreviousPageRequested,
        onNextPageRequested = onNextPageRequested,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (info.castCards.isEmpty()) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .onDirectionKey(Key.DirectionUp, onKey = scrollActions.onUp)
            .onDirectionKey(Key.DirectionDown, onKey = scrollActions.onDown)
            .padding(start = 16.dp, top = 44.dp, end = 16.dp, bottom = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = if (hasNextPage) PAGE_FOCUS_BRIDGE_HEIGHT else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailsInfoHeader(info)
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodyMedium,
            )
            DetailsCastRow(
                info = info,
                firstItemFocusRequester = focusRequester,
                hasNextPage = hasNextPage,
                onAction = onAction,
                onPreviousPageRequested = scrollActions.onUp,
                onNextPageRequested = scrollActions.onDown,
            )
            DetailsInfoGrid(rows = info.primaryRows + info.secondaryRows)
        }
    }
}

private data class DetailsInfoScrollActions(
    val onUp: () -> Unit,
    val onDown: () -> Unit,
)

@Composable
private fun rememberDetailsInfoScrollActions(
    scrollState: ScrollState,
    hasNextPage: Boolean,
    onPreviousPageRequested: () -> Unit,
    onNextPageRequested: () -> Unit,
): DetailsInfoScrollActions {
    val coroutineScope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { INFO_PAGE_SCROLL_STEP.roundToPx() }
    return remember(
        scrollState,
        hasNextPage,
        onPreviousPageRequested,
        onNextPageRequested,
        coroutineScope,
        scrollStepPx,
    ) {
        DetailsInfoScrollActions(
            onUp = {
                if (scrollState.value > 0) {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            value = (scrollState.value - scrollStepPx).coerceAtLeast(0),
                        )
                    }
                } else {
                    onPreviousPageRequested()
                }
            },
            onDown = {
                if (scrollState.value < scrollState.maxValue) {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            value = (scrollState.value + scrollStepPx).coerceAtMost(scrollState.maxValue),
                        )
                    }
                } else if (hasNextPage) {
                    onNextPageRequested()
                }
            },
        )
    }
}

@Composable
private fun DetailsInfoBottomNavigation(
    showPageChevrons: Boolean,
    onNextPageRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageFocusBridge(
        enabled = showPageChevrons,
        onFocused = onNextPageRequested,
        modifier = modifier
            .fillMaxWidth()
            .height(PAGE_FOCUS_BRIDGE_HEIGHT),
    )
    if (showPageChevrons) {
        ChevronIndicator(
            direction = ChevronDirection.Down,
            modifier = modifier,
        )
    }
}

@Composable
private fun DetailsInfoHeader(info: DetailsInfoUIState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.video_details_info_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            info.ratings.forEach { rating -> Rating(rating) }
        }
    }
}

@Composable
private fun DetailsInfoGrid(rows: List<DetailsInfoRowUIState>) {
    val columnCount = DETAILS_INFO_COLUMN_COUNT
    val chunkSize = ((rows.size + columnCount - 1) / columnCount).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        rows.chunked(chunkSize).forEach { columnRows ->
            DetailsInfoRows(
                rows = columnRows,
                modifier = Modifier.weight(1F),
            )
        }
    }
}

@Composable
private fun DetailsInfoRows(
    rows: List<DetailsInfoRowUIState>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
private fun DetailsCastRow(
    info: DetailsInfoUIState,
    firstItemFocusRequester: FocusRequester,
    hasNextPage: Boolean,
    onAction: (UIAction) -> Unit,
    onPreviousPageRequested: () -> Unit,
    onNextPageRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (info.castCards.isEmpty()) return

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val safeScrollOffset = with(LocalDensity.current) {
        -INFO_CHIP_FOCUS_SAFE_PADDING.roundToPx()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.video_details_info_cast),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64F),
        )
        LazyRow(
            state = listState,
            modifier = Modifier
                .focusRestorer(firstItemFocusRequester)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = INFO_CHIP_FOCUS_SAFE_PADDING,
                top = INFO_CHIP_FOCUS_SAFE_PADDING,
                end = 64.dp,
                bottom = INFO_CHIP_FOCUS_SAFE_PADDING,
            ),
        ) {
            itemsIndexed(
                items = info.castCards,
                key = { index, card -> "$index:${card.actorQuery}" },
            ) { index, card ->
                val itemModifier = if (index == 0) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                }
                DetailsCastCard(
                    card = card,
                    onClick = { onAction(DetailsAction.CastMemberSelected(card.actorQuery)) },
                    modifier = itemModifier
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    listState.scrollToItem(
                                        index = (index - 1).coerceAtLeast(0),
                                        scrollOffset = safeScrollOffset,
                                    )
                                }
                            }
                        }
                        .onDirectionKey(Key.DirectionUp, onKey = onPreviousPageRequested)
                        .onDirectionKey(Key.DirectionDown, enabled = hasNextPage, onKey = onNextPageRequested),
                )
            }
        }
    }
}

@Composable
private fun DetailsCastCard(
    card: DetailsCastMemberUIState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = card.displayName
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56F),
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .size(width = ACTOR_CARD_WIDTH.dp, height = ACTOR_CARD_HEIGHT.dp)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActorPortrait(card)
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActorPortrait(card: DetailsCastMemberUIState) {
    var imageFailed by remember(card.photoUrl) { mutableStateOf(false) }
    val photoUrl = card.photoUrl
    Box(
        modifier = Modifier
            .size(ACTOR_PORTRAIT_SIZE.dp)
            .testTag(ACTOR_PORTRAIT_TEST_TAG_PREFIX + card.actorQuery)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = actorInitials(card.displayName),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72F),
        )
        if (!photoUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                onError = { imageFailed = true },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

private fun actorInitials(name: String): String {
    return name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString(separator = "") { word -> word.first().uppercase() }
}

@Composable
private fun DetailsSimilarPage(
    items: List<VideoItemUIState>,
    onAction: (UIAction) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onPreviousPageRequested: () -> Unit,
    showPageChevron: Boolean,
    modifier: Modifier = Modifier,
) {
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }
    Box(modifier = modifier.fillMaxWidth()) {
        PageFocusBridge(
            enabled = showPageChevron,
            onFocused = onPreviousPageRequested,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(PAGE_FOCUS_BRIDGE_HEIGHT),
        )
        if (showPageChevron) {
            ChevronIndicator(
                direction = ChevronDirection.Up,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onDirectionKey(Key.DirectionUp, onKey = onPreviousPageRequested)
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
                        onContextMenu = { contextMenuItem = item },
                    )
                }
            }
        }
        VideoItemContextMenuDialog(
            item = contextMenuItem,
            onDismiss = { contextMenuItem = null },
            onAction = onAction,
        )
    }
}

@Composable
private fun PageFocusBridge(
    enabled: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) {
        Box(modifier = modifier)
        return
    }

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

private fun Modifier.onDirectionKey(
    key: Key,
    enabled: Boolean = true,
    onKey: () -> Unit,
): Modifier {
    if (!enabled) {
        return this
    }
    return onPreviewKeyEvent { event ->
        if (event.key != key) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                onKey()
                true
            }
            KeyEventType.KeyUp -> true
            else -> false
        }
    }
}

private enum class ChevronDirection {
    Up,
    Down,
}

@Composable
private fun ChevronIndicator(
    modifier: Modifier = Modifier,
    direction: ChevronDirection = ChevronDirection.Down,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (direction == ChevronDirection.Up) 12.dp else 0.dp,
                bottom = if (direction == ChevronDirection.Down) 16.dp else 0.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (direction) {
                ChevronDirection.Up -> PhosphorIcons.Duotone.CaretUp
                ChevronDirection.Down -> PhosphorIcons.Duotone.CaretDown
            },
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
private const val DETAILS_INFO_COLUMN_COUNT = 4
private const val DETAILS_PAGES_BASE = 2
private const val DETAILS_PAGES_WITH_SIMILAR = 3
