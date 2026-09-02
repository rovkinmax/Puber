package com.kino.puber.ui.feature.bookmarkpicker.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.domain.interactor.bookmarks.BookmarkFolderInteractor
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerAction
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerParams
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class BookmarkPickerVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val interactor = mockk<BookmarkFolderInteractor>()
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val params = BookmarkPickerParams(
        itemId = 42,
        itemTitle = "Interstellar",
        resultCode = 93,
    )

    @BeforeEach
    fun setUp() {
        every { errorHandler.proceedInvoke(any(), any()) } answers {
            secondArg<((ErrorEntity) -> Unit)?>()?.invoke(
                ErrorEntity(message = firstArg<Throwable>().message.orEmpty(), code = "test")
            )
        }
        coEvery { interactor.getFolders() } returns listOf(
            Bookmark(id = 7, title = "Watch later", count = 3),
            Bookmark(id = 8, title = "Family", count = 1),
        )
        coEvery { interactor.getItemFolders(42) } returns listOf(
            BookmarkFolder(id = 8, title = "Family"),
        )
        coEvery { interactor.setItemInFolder(any(), any(), any()) } returns Unit
    }

    @Test
    fun load_marksAllExistingMembershipsByFolderId() {
        val vm = createVM()

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val state = vm.testStateValue as BookmarkPickerViewState.Content
        assertEquals(listOf(7, 8), state.folders.map { it.id })
        assertEquals(listOf(false, true), state.folders.map { it.isSelected })
    }

    @Test
    fun toggleFolder_usesExplicitDesiredMembershipAndUpdatesOnlyThatFolder() {
        val vm = loadedVM()

        vm.onAction(BookmarkPickerAction.FolderToggled(7))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            interactor.setItemInFolder(itemId = 42, folderId = 7, selected = true)
        }
        val state = vm.testStateValue as BookmarkPickerViewState.Content
        assertTrue(state.folders.first { it.id == 7 }.isSelected)
        assertTrue(state.folders.first { it.id == 8 }.isSelected)
        assertTrue(state.changingFolderIds.isEmpty())
    }

    @Test
    fun createFolder_addsItemSelectsFolderAndClearsDraft() {
        val vm = loadedVM()
        coEvery {
            interactor.createFolder(title = "Weekend")
        } returns Bookmark(id = 11, title = "Weekend", count = 0)

        vm.onAction(BookmarkPickerAction.AddFolderRequested)
        vm.onAction(BookmarkPickerAction.NewFolderTitleChanged("  Weekend  "))
        vm.onAction(BookmarkPickerAction.CreateFolder)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val state = vm.testStateValue as BookmarkPickerViewState.Content
        assertEquals("", state.newFolderTitle)
        assertFalse(state.isCreatingFolder)
        assertFalse(state.isCreateFolderDialogVisible)
        assertEquals(11, state.folders.last().id)
        assertTrue(state.folders.last().isSelected)
        coVerify(exactly = 1) {
            interactor.createFolder(title = "Weekend")
        }
        coVerify(exactly = 1) {
            interactor.setItemInFolder(itemId = 42, folderId = 11, selected = true)
        }
    }

    @Test
    fun createFolder_addFailureKeepsCreatedFolderVisibleAndUnchecked() {
        val vm = loadedVM()
        coEvery {
            interactor.createFolder(title = "Weekend")
        } returns Bookmark(id = 11, title = "Weekend", count = 0)
        coEvery {
            interactor.setItemInFolder(itemId = 42, folderId = 11, selected = true)
        } throws IllegalStateException("add failed")

        vm.onAction(BookmarkPickerAction.AddFolderRequested)
        vm.onAction(BookmarkPickerAction.NewFolderTitleChanged("Weekend"))
        vm.onAction(BookmarkPickerAction.CreateFolder)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val state = vm.testStateValue as BookmarkPickerViewState.Content
        val created = state.folders.single { it.id == 11 }
        assertFalse(created.isSelected)
        assertFalse(state.isCreatingFolder)
        assertFalse(state.isCreateFolderDialogVisible)
        assertTrue(state.changingFolderIds.isEmpty())
    }

    @Test
    fun addFolderRequested_opensSeparateDialogWithEmptyDraft() {
        val vm = loadedVM()
        vm.onAction(BookmarkPickerAction.NewFolderTitleChanged("Old draft"))

        vm.onAction(BookmarkPickerAction.AddFolderRequested)

        val state = vm.testStateValue as BookmarkPickerViewState.Content
        assertTrue(state.isCreateFolderDialogVisible)
        assertEquals("", state.newFolderTitle)
    }

    @Test
    fun backFromCreateDialog_closesOnlyCreateDialog() {
        val vm = loadedVM()
        vm.onAction(BookmarkPickerAction.AddFolderRequested)
        vm.onAction(BookmarkPickerAction.NewFolderTitleChanged("Weekend"))

        vm.onBackPressed()

        val state = vm.testStateValue as BookmarkPickerViewState.Content
        assertFalse(state.isCreateFolderDialogVisible)
        assertEquals("", state.newFolderTitle)
        verify(exactly = 0) { router.back(any(), any()) }
    }

    @Test
    fun backWhileLoading_returnsNullResult() {
        val vm = createVM()

        vm.onBackPressed()

        verify(exactly = 1) {
            router.back(resultCode = 93, result = null)
        }
    }

    @Test
    fun back_returnsCompleteCurrentMembershipSummary() {
        val vm = loadedVM()

        vm.onBackPressed()

        verify(exactly = 1) {
            router.back(
                resultCode = 93,
                result = match<BookmarkPickerResult> { result ->
                    result.itemId == 42 && result.selectedFolderIds == listOf(8)
                },
            )
        }
    }

    private fun loadedVM(): BookmarkPickerVM {
        val vm = createVM()
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun createVM(): BookmarkPickerVM {
        every { router.screens } returns mockk(relaxed = true)
        return BookmarkPickerVM(
            router = router,
            params = params,
            interactor = interactor,
            errorHandler = errorHandler,
        )
    }
}
