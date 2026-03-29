package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FadeGradient
import com.kino.puber.core.ui.uikit.component.LoadMoreHandler
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.ui.feature.contentlist.vm.SectionVM
import org.koin.compose.LocalKoinScope
import org.koin.core.qualifier.named

@Composable
internal fun SectionRowContent(
    config: SectionConfig,
    isTargetRow: Boolean,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: () -> Unit,
    onShowAll: (() -> Unit)? = null,
) {
    val scope = LocalKoinScope.current
    val sectionVm = remember(config.id) { scope.get<SectionVM>(named(config.id)) }
    val state by sectionVm.collectViewState()

    val contentFocusRequester = remember { FocusRequester() }
    val hasFocusRef = remember { booleanArrayOf(false) }

    Box(modifier = Modifier.onFocusChanged { hasFocusRef[0] = it.hasFocus }) {
        when (val s = state) {
            is SectionState.Loading -> ShimmerSectionCards()
            is SectionState.Empty -> { /* hidden */ }
            is SectionState.Error -> ErrorSectionContent(
                message = s.message,
                onRetry = { sectionVm.onAction(CommonAction.RetryClicked) },
            )
            is SectionState.Content -> {
                val shouldTransferFocus = hasFocusRef[0]
                ContentSectionCards(
                    state = s,
                    isTargetRow = isTargetRow,
                    contentFocusRequester = contentFocusRequester,
                    onItemClick = onItemClick,
                    onItemFocused = onItemFocused,
                    onSectionFocused = onSectionFocused,
                    onLoadMore = { sectionVm.onAction(CommonAction.LoadMore) },
                    onShowAll = onShowAll,
                )
                LaunchedEffect(Unit) {
                    if (shouldTransferFocus) {
                        contentFocusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContentSectionCards(
    state: SectionState.Content,
    isTargetRow: Boolean,
    contentFocusRequester: FocusRequester,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: () -> Unit,
    onLoadMore: () -> Unit,
    onShowAll: (() -> Unit)?,
) {
    val listState = rememberLazyListState()
    val savedItemFocusRequester = remember { FocusRequester() }
    var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        PositionFocusedItemInLazyLayout {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
                    .focusRequester(contentFocusRequester)
                    .focusProperties {
                        onEnter = {
                            if (requestedFocusDirection == FocusDirection.Down ||
                                requestedFocusDirection == FocusDirection.Up
                            ) {
                                savedItemFocusRequester.requestFocus()
                                cancelFocusChange()
                            }
                        }
                    }
                    .focusRestorer(savedItemFocusRequester),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    val isFallbackTarget = if (isTargetRow) {
                        index == focusedItemIndex
                    } else {
                        index == 0
                    }
                    val focusModifier = remember(index, item.id) {
                        Modifier.onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedItemIndex = index
                                onSectionFocused()
                                onItemFocused(item)
                            }
                        }
                    }
                    val clickCallback = remember(item.id) { { onItemClick(item) } }
                    VideoItemHorizontal(
                        modifier = Modifier
                            .then(
                                if (isFallbackTarget) Modifier.focusRequester(savedItemFocusRequester)
                                else Modifier
                            )
                            .then(focusModifier),
                        state = item,
                        onClick = clickCallback,
                    )
                }
                if (onShowAll != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .height(PuberTheme.Defaults.HorizontalVideoItemHeight)
                                .aspectRatio(PuberTheme.Defaults.HorizontalVideoItemAspectRatio),
                            contentAlignment = Alignment.Center,
                        ) {
                            Button(
                                onClick = { onShowAll() },
                            ) {
                                Text(
                                    stringResource(R.string.show_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
            FadeGradient(listState)
        }
    }

    if (onShowAll == null) {
        LoadMoreHandler(
            lazyListState = listState,
            loadMoreAtEnd = onLoadMore,
        )
    }
}

@Composable
private fun ErrorSectionContent(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.error_button_retry))
        }
    }
}
