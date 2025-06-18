package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Immutable
data class PagingListState<Item>(
    val items: List<Item> = emptyList(),
    val isLoadingMoreStart: Boolean = false,
    val isLoadingMoreEnd: Boolean = false,
    val pageStartError: PagingListPageError? = null,
    val pageEndError: PagingListPageError? = null,
) {
    val totalItemsCount: Int

    init {
        var length = items.size
        if (pageStartError != null) {
            length++
        }

        if (isLoadingMoreStart) {
            length++
        }

        if (isLoadingMoreEnd) {
            length++
        }

        if (pageEndError != null) {
            length++
        }
        totalItemsCount = length
    }

    inline fun <reified T : Item> mapItems(mapper: (T) -> Item): PagingListState<Item> {
        return copy(items.map { item ->
            if (item is T) {
                mapper(item)
            } else {
                item
            }
        })
    }
}

@Immutable
data class PagingListPageError(
    val message: String,
    val onActionClick: () -> Unit,
)

@Composable
fun <Item> PagingColumn(
    modifier: Modifier = Modifier,
    pagingListState: PagingListState<Item>,
    item: @Composable LazyItemScope.(index: Int, item: Item) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    headers: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
    loadingItem: @Composable () -> Unit = { },
    loadMoreAtEnd: () -> Unit = {},
    loadMoreAtStart: () -> Unit = {},
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = verticalArrangement,
        contentPadding = contentPadding,
    ) {
        if (pagingListState.pageStartError != null) {
            item {
                ListItemError(
                    error = pagingListState.pageStartError.message,
                    onClick = pagingListState.pageStartError.onActionClick,
                )
            }
        }

        if (pagingListState.isLoadingMoreStart) {
            item {
                loadingItem()
            }
        }
        headers()
        items(pagingListState.items.size) { index ->
            item(index, pagingListState.items[index])
        }
        footer()

        if (pagingListState.isLoadingMoreEnd) {
            item {
                loadingItem()
            }
        }

        if (pagingListState.pageEndError != null) {
            item {
                ListItemError(
                    error = pagingListState.pageEndError.message,
                    onClick = pagingListState.pageEndError.onActionClick,
                )
            }
        }
    }

    LoadMoreHandler(
        lazyListState = listState,
        loadMoreAtEnd = loadMoreAtEnd,
        loadMoreAtStart = loadMoreAtStart,
    )
}