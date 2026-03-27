package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FadeGradient
import com.kino.puber.core.ui.uikit.component.LoadMoreHandler
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItem
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.ui.feature.contentlist.vm.SectionVM
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

@Composable
internal fun SectionRow(
    config: SectionConfig,
    isTargetRow: Boolean,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: () -> Unit,
    onShowAll: (() -> Unit)? = null,
) {
    val sectionVm = koinViewModel<SectionVM>(qualifier = named(config.id))
    val state by sectionVm.collectViewState()

    when (val s = state) {
        is SectionState.Loading -> ShimmerSectionRow(title = config.title)
        is SectionState.Empty -> { /* hidden */ }
        is SectionState.Error -> ErrorSectionRow(
            title = config.title,
            message = s.message,
            onRetry = { sectionVm.onAction(CommonAction.RetryClicked) },
        )
        is SectionState.Content -> ContentSectionRow(
            config = config,
            state = s,
            isTargetRow = isTargetRow,
            onItemClick = onItemClick,
            onItemFocused = onItemFocused,
            onSectionFocused = onSectionFocused,
            onLoadMore = { sectionVm.onAction(CommonAction.LoadMore) },
            onShowAll = onShowAll,
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ContentSectionRow(
    config: SectionConfig,
    state: SectionState.Content,
    isTargetRow: Boolean,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: () -> Unit,
    onLoadMore: () -> Unit,
    onShowAll: (() -> Unit)?,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = config.title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (onShowAll != null) {
                Spacer(modifier = Modifier.weight(1F))
                Button(onClick = { onShowAll() }) {
                    Text("Показать все")
                }
            }
        }

        val listState = rememberLazyListState()
        val savedItemFocusRequester = remember { FocusRequester() }
        var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }

        Box(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer { savedItemFocusRequester },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                itemsIndexed(state.items) { index, item ->
                    val isFallbackTarget = if (isTargetRow) {
                        index == focusedItemIndex
                    } else {
                        index == 0
                    }
                    VideoItem(
                        modifier = Modifier
                            .then(
                                if (isFallbackTarget) Modifier.focusRequester(savedItemFocusRequester)
                                else Modifier
                            )
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    focusedItemIndex = index
                                    onSectionFocused()
                                    onItemFocused(item)
                                }
                            },
                        state = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
            FadeGradient(listState)
        }

        LoadMoreHandler(
            lazyListState = listState,
            loadMoreAtEnd = onLoadMore,
        )
    }
}

@Composable
private fun ErrorSectionRow(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}
