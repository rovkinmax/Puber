package com.kino.puber.ui.feature.bookmarks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState

@Composable
internal fun BookmarksScreenContent(
    state: BookmarksViewState,
    onAction: (UIAction) -> Unit,
    onFolderSelected: (Int) -> Unit,
) {
    when (state) {
        is BookmarksViewState.Loading -> FullScreenProgressIndicator()
        is BookmarksViewState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message)
            }
        }
        is BookmarksViewState.Content -> {
            BookmarksContent(
                state = state,
                onAction = onAction,
                onFolderSelected = onFolderSelected,
            )
        }
    }
}

@Composable
private fun BookmarksContent(
    state: BookmarksViewState.Content,
    onAction: (UIAction) -> Unit,
    onFolderSelected: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.folders.size > 1) {
            FolderChips(
                folders = state.folders,
                selectedFolderId = state.selectedFolderId,
                onFolderSelected = onFolderSelected,
            )
        }

        if (state.isLoadingItems) {
            FullScreenProgressIndicator()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                    val clickCallback = remember(item.id) {
                        { onAction(CommonAction.ItemSelected(item)) }
                    }
                    VideoItemHorizontal(
                        state = item,
                        onClick = clickCallback,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderChips(
    folders: List<Bookmark>,
    selectedFolderId: Int?,
    onFolderSelected: (Int) -> Unit,
) {
    val chipShape = RoundedCornerShape(16.dp)
    LazyRow(
        modifier = Modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(items = folders, key = { it.id }) { folder ->
            val isSelected = folder.id == selectedFolderId
            Surface(
                onClick = { onFolderSelected(folder.id) },
                shape = ClickableSurfaceDefaults.shape(chipShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = folder.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
