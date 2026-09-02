package com.kino.puber.ui.feature.bookmarkpicker.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.bookmarks.BookmarkFolderInteractor
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkFolderUi
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerAction
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerParams
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerViewState
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
                )
            },
        )
    }

    override fun dispatchError(error: ErrorEntity) {
        when (val state = stateValue) {
            is BookmarkPickerViewState.Content -> {
                updateViewState(
                    state.copy(
                        changingFolderIds = emptySet(),
                        isCreatingFolder = false,
                    )
                )
                showMessage(error.message)
            }
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
                )
            )
        }
    }

    private fun toggleFolder(folderId: Int) {
        val state = stateValue as? BookmarkPickerViewState.Content ?: return
        if (folderId in state.changingFolderIds) return
        val folder = state.folders.firstOrNull { it.id == folderId } ?: return
        val newSelection = !folder.isSelected
        updateViewState(
            state.copy(changingFolderIds = state.changingFolderIds + folderId)
        )
        launch {
            interactor.setItemInFolder(
                itemId = params.itemId,
                folderId = folderId,
                selected = newSelection,
            )
            updateViewState<BookmarkPickerViewState.Content> {
                copy(
                    folders = folders.map { current ->
                        if (current.id == folderId) {
                            current.copy(
                                isSelected = newSelection,
                            )
                        } else {
                            current
                        }
                    },
                    changingFolderIds = changingFolderIds - folderId,
                )
            }
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
            val folder = interactor.createFolder(state.newFolderTitle.trim())
            updateViewState<BookmarkPickerViewState.Content> {
                copy(
                    folders = folders + BookmarkFolderUi(
                        id = folder.id,
                        title = folder.title,
                        isSelected = false,
                    ),
                    newFolderTitle = "",
                    isCreateFolderDialogVisible = false,
                    changingFolderIds = changingFolderIds + folder.id,
                )
            }
            interactor.setItemInFolder(
                itemId = params.itemId,
                folderId = folder.id,
                selected = true,
            )
            updateViewState<BookmarkPickerViewState.Content> {
                copy(
                    folders = folders.map { current ->
                        if (current.id == folder.id) {
                            current.copy(isSelected = true)
                        } else {
                            current
                        }
                    },
                    changingFolderIds = changingFolderIds - folder.id,
                    isCreatingFolder = false,
                )
            }
        }
    }
}
