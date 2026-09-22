package com.kino.puber.ui.feature.bookmarkpicker.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.domain.interactor.bookmarks.BookmarkFolderInteractor
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkFolderUi
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerAction
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerParams
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class BookmarkPickerVM(
    router: AppRouter,
    private val params: BookmarkPickerParams,
    private val interactor: BookmarkFolderInteractor,
    override val errorHandler: ErrorHandler,
) : PuberVM<BookmarkPickerViewState>(router) {

    override val initialViewState: BookmarkPickerViewState = BookmarkPickerViewState.Loading

    override fun onStart() {
        load()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is BookmarkPickerAction.FolderToggled -> toggleFolder(action.folderId)
            BookmarkPickerAction.AddFolderRequested -> showCreateFolderDialog()
            BookmarkPickerAction.AddFolderDismissed -> dismissCreateFolderDialog()
            is BookmarkPickerAction.NewFolderTitleChanged -> updateNewFolderTitle(action.title)
            BookmarkPickerAction.CreateFolder -> createFolder()
            BookmarkPickerAction.Dismiss -> onBackPressed()
            BookmarkPickerAction.Retry -> load()
            else -> super.onAction(action)
        }
    }

    override fun onBackPressed() {
        val state = stateValue as? BookmarkPickerViewState.Content
        if (state?.isCreateFolderDialogVisible == true) {
            dismissCreateFolderDialog()
            return
        }
        // The create dialog is hidden as soon as the folder exists, but the item is only added to
        // it by a second request. Closing in that window would report a selection missing the very
        // folder the user just created, so BACK waits it out.
        if (state?.isCreatingFolder == true) return
        val selectedFolderIds = state
            ?.folders
            ?.filter(BookmarkFolderUi::isSelected)
            ?.map(BookmarkFolderUi::id)
        router.back(
            resultCode = params.resultCode,
            result = selectedFolderIds?.let {
                BookmarkPickerResult(
                    itemId = params.itemId,
                    selectedFolderIds = it,
                    isInQuickFolder = state.quickFolderId != null && state.quickFolderId in it,
                )
            },
        )
    }

    override fun dispatchError(error: ErrorEntity) {
        when (stateValue) {
            // Each request clears its own row before the error reaches here. Clearing every row
            // instead would drop the spinner of a toggle that is still in flight, letting the
            // user press it again and race two writes for the same folder.
            is BookmarkPickerViewState.Content -> showMessage(error.message)
            else -> updateViewState(BookmarkPickerViewState.Error(error.message))
        }
    }

    private fun load() {
        launch {
            updateViewState(BookmarkPickerViewState.Loading)
            val (folders, memberships) = coroutineScope {
                val folders = async { interactor.getFolders() }
                val memberships = async { interactor.getItemFolders(params.itemId) }
                folders.await() to memberships.await()
            }
            val selectedIds = memberships.mapTo(mutableSetOf()) { it.id }
            updateViewState(
                BookmarkPickerViewState.Content(
                    folders = folders.map { folder ->
                        BookmarkFolderUi(
                            id = folder.id,
                            title = folder.title,
                            isSelected = folder.id in selectedIds,
                        )
                    },
                    quickFolderId = interactor.resolveQuickFolder(folders)?.id,
                )
            )
        }
    }

    private fun toggleFolder(folderId: Int) {
        val state = stateValue as? BookmarkPickerViewState.Content ?: return
        if (folderId in state.changingFolderIds) return
        val folder = state.folders.firstOrNull { it.id == folderId } ?: return
        val newSelection = !folder.isSelected
        // Applied up front so the row and any result reported before the request lands agree with
        // what the user just did; reverted below if the request fails.
        setFolderSelected(folderId, newSelection)
        updateViewState<BookmarkPickerViewState.Content> {
            copy(changingFolderIds = changingFolderIds + folderId)
        }
        launch {
            runCatching {
                interactor.setItemInFolder(
                    itemId = params.itemId,
                    folderId = folderId,
                    selected = newSelection,
                )
            }.onFailure { error ->
                setFolderSelected(folderId, folder.isSelected)
                updateViewState<BookmarkPickerViewState.Content> {
                    copy(changingFolderIds = changingFolderIds - folderId)
                }
                throw error
            }
            updateViewState<BookmarkPickerViewState.Content> {
                copy(changingFolderIds = changingFolderIds - folderId)
            }
        }
    }

    private fun setFolderSelected(folderId: Int, selected: Boolean) {
        updateViewState<BookmarkPickerViewState.Content> {
            copy(
                folders = folders.map { current ->
                    if (current.id == folderId) current.copy(isSelected = selected) else current
                },
            )
        }
    }

    private fun updateNewFolderTitle(title: String) {
        updateViewState<BookmarkPickerViewState.Content> {
            copy(newFolderTitle = title)
        }
    }

    private fun showCreateFolderDialog() {
        updateViewState<BookmarkPickerViewState.Content> {
            copy(
                newFolderTitle = "",
                isCreateFolderDialogVisible = true,
            )
        }
    }

    private fun dismissCreateFolderDialog() {
        updateViewState<BookmarkPickerViewState.Content> {
            if (isCreatingFolder) {
                this
            } else {
                copy(
                    newFolderTitle = "",
                    isCreateFolderDialogVisible = false,
                )
            }
        }
    }

    private fun createFolder() {
        val state = stateValue as? BookmarkPickerViewState.Content ?: return
        if (!state.isCreateFolderDialogVisible || !state.canCreateFolder) return
        updateViewState(state.copy(isCreatingFolder = true))
        launch {
            val folder = createFolderOrClearProgress(state.newFolderTitle.trim())
            updateViewState<BookmarkPickerViewState.Content> {
                copy(
                    folders = folders + BookmarkFolderUi(
                        id = folder.id,
                        title = folder.title,
                        // A folder is only created here in order to file the item into it, so the
                        // row is selected from the moment it appears.
                        isSelected = true,
                    ),
                    newFolderTitle = "",
                    isCreateFolderDialogVisible = false,
                    changingFolderIds = changingFolderIds + folder.id,
                )
            }
            runCatching {
                interactor.setItemInFolder(
                    itemId = params.itemId,
                    folderId = folder.id,
                    selected = true,
                )
            }.onFailure { error ->
                setFolderSelected(folder.id, selected = false)
                updateViewState<BookmarkPickerViewState.Content> {
                    copy(
                        changingFolderIds = changingFolderIds - folder.id,
                        isCreatingFolder = false,
                    )
                }
                throw error
            }
            updateViewState<BookmarkPickerViewState.Content> {
                copy(
                    changingFolderIds = changingFolderIds - folder.id,
                    isCreatingFolder = false,
                )
            }
        }
    }

    /**
     * On a failed create the draft dialog stays open with the typed title so the user can retry,
     * but the in-progress flag has to go or Create and Cancel are both left disabled.
     */
    private suspend fun createFolderOrClearProgress(title: String): Bookmark {
        return try {
            interactor.createFolder(title)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            updateViewState<BookmarkPickerViewState.Content> { copy(isCreatingFolder = false) }
            throw error
        }
    }
}
