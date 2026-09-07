package com.kino.puber.ui.feature.showall.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ShowAllVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: ContentListInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
    }

    @Test
    fun itemSelected_navigatesForContentChangeResultToDetails() {
        val screen = mockk<PuberScreen>()
        every { screens.details(42) } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemSelected(videoItem(42)))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun itemPlayed_navigatesForContentChangeResultToPlayer() {
        val screen = mockk<PuberScreen>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun childChanges_areAccumulatedAndReturnedOnBack() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        vm.onBackPressed()

        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> {
                    it.changes[42] == setOf(ContentChangeType.Watched)
                },
            )
        }
        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
    }

    @Test
    fun directSavedChange_isAccumulatedAndInvalidatesFirstPageCacheAfterSuccess() {
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } returns Result.success(false)
        val vm = createVM()

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        vm.onBackPressed()

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> {
                    it.changes[42] == setOf(ContentChangeType.Bookmark)
                },
            )
        }
    }

    @Test
    fun backWhileDirectSavePending_waitsAndReturnsChangeExactlyOnce() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } coAnswers {
            releaseSave.await()
            Result.success(false)
        }
        val vm = createVM()

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        vm.onBackPressed()
        vm.onBackPressed()

        verify(exactly = 0) { router.back(any(), any()) }
        verify(exactly = 2) { router.addBackDispatcher(vm) }
        releaseSave.complete(Unit)

        verify(exactly = 1) {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> {
                    it.changes[42] == setOf(ContentChangeType.Bookmark)
                },
            )
        }
    }

    @Test
    fun backWithoutChanges_returnsEmptyChangeSet() {
        val vm = createVM()

        vm.onBackPressed()

        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> { it.isEmpty },
            )
        }
    }

    @Test
    fun firstPage_publishesInteractorItemsWithoutAdditionalFiltering() = runBlocking {
        val item = Item(
            id = 25,
            title = "Interactor result",
            type = ItemType.MOVIE,
            genres = listOf(Genre(ANIME_GENRE_ID, "Anime")),
        )
        val mappedItem = videoItem(25)
        val mapper = mockk<VideoItemUIMapper>()
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item)
        every { mapper.mapShortItemList(listOf(item)) } returns listOf(mappedItem)
        val paginator = Paginator.Store<Item> { old, new -> old.id == new.id }
        val vm = createVM(paginator = paginator, mapper = mapper)

        vm.testOnStart()
        withTimeout(2_000) {
            while (vm.testStateValue !is ShowAllViewState.Content) {
                delay(10)
            }
        }

        assertEquals(ShowAllViewState.Content(listOf(mappedItem)), vm.testStateValue)
        verify(exactly = 1) { mapper.mapShortItemList(listOf(item)) }
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun bookmarkPickerResult_marksTheItemBookmarkedAndDropsTheStaleFirstPage() = runBlocking {
        val item = Item(id = 25, title = "Interactor result", type = ItemType.MOVIE)
        val mapper = mockk<VideoItemUIMapper>()
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item)
        every { mapper.mapShortItemList(listOf(item)) } returns listOf(videoItem(25))
        val screen = mockk<PuberScreen>()
        every { screens.bookmarkPicker(any(), any()) } returns screen
        val paginator = Paginator.Store<Item> { old, new -> old.id == new.id }
        val vm = createVM(paginator = paginator, mapper = mapper)
        vm.testOnStart()
        withTimeout(2_000) {
            while (vm.testStateValue !is ShowAllViewState.Content) {
                delay(10)
            }
        }

        vm.onAction(CommonAction.ItemBookmarksRequested(videoItem(25)))
        val listener = slot<(BookmarkPickerResult?) -> Unit>()
        verify { router.navigateForResult<BookmarkPickerResult>(screen, any(), capture(listener)) }
        listener.captured(BookmarkPickerResult(itemId = 25, selectedFolderIds = listOf(7)))

        val state = vm.testStateValue as ShowAllViewState.Content
        assertTrue(state.items.single().isBookmarked)
        verify { interactor.invalidateFirstPageCache() }
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun itemBookmarksRequested_opensTheBookmarkPickerForThatItem() {
        val screen = mockk<PuberScreen>()
        every {
            screens.bookmarkPicker(itemId = 42, resultCode = any())
        } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemBookmarksRequested(videoItem(42)))

        verify { router.navigateForResult<BookmarkPickerResult>(screen, any(), any()) }
    }

    private fun createVM(
        config: SectionConfig = SectionConfig(id = "popular", title = "Popular"),
        paginator: Paginator.Store<Item> = Paginator.Store { old, new -> old.id == new.id },
        mapper: VideoItemUIMapper = mockk(relaxed = true),
        contentListInteractor: ContentListInteractor = interactor,
    ) = ShowAllVM(
        paginator = paginator,
        config = config,
        interactor = contentListInteractor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        router = router,
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    private fun page(
        item: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = listOf(item),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )
}
