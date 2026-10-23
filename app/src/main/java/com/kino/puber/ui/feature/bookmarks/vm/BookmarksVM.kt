package com.kino.puber.ui.feature.bookmarks.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.bookmarks.BookmarkInteractor
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState
import com.kino.puber.ui.feature.bookmarkpicker.openBookmarkPicker
import kotlinx.coroutines.Job

internal class BookmarksVM(
    router: AppRouter,
    private val interactor: BookmarkInteractor,
    private val mapper: VideoItemUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<BookmarksViewState>(router) {

    override val initialViewState: BookmarksViewState = BookmarksViewState.Loading

    private var folderItemsJob: Job? = null

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
                openDetails(item.id)
            }
            is CommonAction.ItemPlayed<*> -> {
                val item = action.item as VideoItemUIState
                openPlayer(item.id)
            }
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
            is CommonAction.ItemBookmarksRequested<*> ->
                router.openBookmarkPicker(
                    item = action.item as VideoItemUIState,
                    listener = { result -> if (result != null) loadBookmarks() },
                )
            else -> super.onAction(action)
        }
    }

    fun onFolderSelected(folderId: Int) {
        updateViewState<BookmarksViewState.Content> {
            copy(selectedFolderId = folderId, isLoadingItems = true)
        }
        loadFolderItems(folderId)
    }

    private fun openDetails(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun openPlayer(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        if (ContentChangeType.Bookmark in changes.types || ContentChangeType.Watchlist in changes.types) {
            loadBookmarks()
            return
        }
        (stateValue as? BookmarksViewState.Content)?.selectedFolderId?.let(::loadFolderItems)
    }

    private fun loadBookmarks() {
        launch {
            val folders = interactor.getBookmarks()
            val previousFolderId = (stateValue as? BookmarksViewState.Content)?.selectedFolderId
            val selectedFolder = folders.firstOrNull { it.id == previousFolderId }
                ?: folders.firstOrNull()
            updateViewState(
                BookmarksViewState.Content(
                    folders = folders,
                    selectedFolderId = selectedFolder?.id,
                    items = emptyList(),
                    isLoadingItems = selectedFolder != null,
                )
            )
            if (selectedFolder != null) {
                loadFolderItems(selectedFolder.id)
            }
        }
    }

    /**
     * A walk owns the item list until it finishes, so a new one must cancel the one it replaces:
     * two live walks both pass the `selectedFolderId` guard in [publishFolderItems], letting the
     * older walk's shorter accumulation land after the newer one and letting whichever finishes
     * first clear `isLoadingItems` while the other is still paging.
     */
    private fun loadFolderItems(folderId: Int) {
        folderItemsJob?.cancel()
        folderItemsJob = launch {
            loadAllFolderItems(folderId)
        }
    }

    /**
     * Walks every page of a folder, publishing each one as it arrives so a large folder shows its
     * first rows immediately instead of after the whole walk. `total` is re-read from every
     * response, and a folder that shrinks mid-walk (the page asked for is not the page returned)
     * ends the walk with what has been collected rather than discarding it.
     */
    private suspend fun loadAllFolderItems(folderId: Int) {
        val items = mutableListOf<Item>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages) {
            val response = interactor.getBookmarkItems(folderId, page)
            if (response.pagination.current != page) break
            items += response.items
            totalPages = response.pagination.total
            page++
            publishFolderItems(folderId, items, isLoadingItems = page <= totalPages)
        }
        publishFolderItems(folderId, items, isLoadingItems = false)
    }

    private fun publishFolderItems(folderId: Int, items: List<Item>, isLoadingItems: Boolean) {
        updateViewState<BookmarksViewState.Content> {
            // A folder switched during the walk owns the list now; drop the stale pages.
            if (selectedFolderId != folderId) {
                this
            } else {
                copy(
                    items = mapper.mapShortItemList(items.distinctBy { it.id }).markSaved(),
                    isLoadingItems = isLoadingItems,
                )
            }
        }
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        val folderId = (stateValue as? BookmarksViewState.Content)?.selectedFolderId ?: return
        launch {
            interactor.setItemSaved(itemId = item.id, folderId = folderId, saved = saved)
            updateViewState<BookmarksViewState.Content> {
                copy(items = items.updateSaved(item.id, saved).filterNot { video -> !video.isSaved })
            }
        }
    }

    private fun List<VideoItemUIState>.markSaved(): List<VideoItemUIState> {
        return map { item -> item.copy(isSaved = true) }
    }

    private fun List<VideoItemUIState>.updateSaved(itemId: Int, saved: Boolean): List<VideoItemUIState> {
        return map { item ->
            if (item.id == itemId) item.copy(isSaved = saved) else item
        }
    }
}
