package com.kino.puber.ui.feature.bookmarks.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.bookmarks.BookmarkInteractor
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState

internal class BookmarksVM(
    router: AppRouter,
    private val interactor: BookmarkInteractor,
    private val mapper: VideoItemUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<BookmarksViewState>(router) {

    override val initialViewState: BookmarksViewState = BookmarksViewState.Loading

    override fun dispatchError(error: ErrorEntity) {
        when (val state = stateValue) {
            is BookmarksViewState.Content -> {
                showMessage(error.message)
                if (state.isLoadingItems) {
                    updateViewState<BookmarksViewState.Content> { copy(isLoadingItems = false) }
                }
            }
            else -> updateViewState(BookmarksViewState.Error(error.message))
        }
    }

    override fun onStart() {
        loadBookmarks()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                router.navigateTo(router.screens.details(item.id))
            }
            else -> super.onAction(action)
        }
    }

    fun onFolderSelected(folderId: Int) {
        updateViewState<BookmarksViewState.Content> {
            copy(selectedFolderId = folderId, isLoadingItems = true)
        }
        loadFolderItems(folderId)
    }

    private fun loadBookmarks() {
        launch {
            val folders = interactor.getBookmarks()
            val firstFolder = folders.firstOrNull()
            updateViewState(
                BookmarksViewState.Content(
                    folders = folders,
                    selectedFolderId = firstFolder?.id,
                    isLoadingItems = firstFolder != null,
                )
            )
            if (firstFolder != null) {
                loadFolderItems(firstFolder.id)
            }
        }
    }

    private fun loadFolderItems(folderId: Int) {
        launch {
            val response = interactor.getBookmarkItems(folderId, page = 1)
            updateViewState<BookmarksViewState.Content> {
                copy(
                    items = mapper.mapShortItemList(response.items),
                    isLoadingItems = false,
                )
            }
        }
    }
}
