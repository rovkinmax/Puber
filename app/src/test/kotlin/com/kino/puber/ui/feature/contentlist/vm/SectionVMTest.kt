package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
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
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

class SectionVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun coordinatorRefresh_restartsPagingWithoutClearingSharedCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sideEffects = mutableListOf<Paginator.SideEffect>()
        val sideEffectCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            paginator.sideEffects.collect(sideEffects::add)
        }
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage()
        val vm = createVM(paginator, config("popular"), interactor, coordinator, dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        sideEffects.clear()

        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { interactor.invalidateFirstPageCache() }
        assertEquals(listOf(Paginator.SideEffect.LoadFirstPage), sideEffects)
        sideEffectCollector.cancel()
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun coordinatorSubscriber_createdBeforeRefreshReceivesItWhenCollectionStartsAfter() = runTest {
        val coordinator = ContentListRefreshCoordinator()
        val refreshRequests = coordinator.refreshRequests()

        coordinator.requestRefresh()

        assertEquals(Unit, withTimeout(1_000) { refreshRequests.first() })
    }

    @Test
    fun directSavedChange_invalidatesCacheOnceAndRestartsSiblingSections() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstPaginator = paginator(dispatcher)
        val siblingPaginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val savedItemInteractor = mockk<SavedItemInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val firstConfig = config("popular")
        val siblingConfig = config("fresh")
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage()
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } returns Result.success(false)
        val first = createVM(
            paginator = firstPaginator,
            config = firstConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            savedItemInteractor = savedItemInteractor,
        )
        val sibling = createVM(
            paginator = siblingPaginator,
            config = siblingConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            savedItemInteractor = savedItemInteractor,
        )
        first.testOnStart()
        sibling.testOnStart()
        testScheduler.advanceUntilIdle()

        first.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        coVerify(exactly = 2) { interactor.loadPage(firstConfig, page = 1) }
        coVerify(exactly = 2) { interactor.loadPage(siblingConfig, page = 1) }
        first.testCancelScope()
        sibling.testCancelScope()
        firstPaginator.close()
        siblingPaginator.close()
    }

    @Test
    fun firstPage_publishesInteractorItemsWithoutAdditionalFiltering() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>()
        val mapper = mockk<VideoItemUIMapper>()
        val coordinator = ContentListRefreshCoordinator()
        val item = Item(
            id = 25,
            title = "Interactor result",
            type = ItemType.MOVIE,
            genres = listOf(Genre(ANIME_GENRE_ID, "Anime")),
        )
        val mappedItem = videoItem(25)
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item)
        every { mapper.mapShortItemList(listOf(item)) } returns listOf(mappedItem)
        val vm = createVM(
            paginator = paginator,
            config = config("anime"),
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapper,
        )

        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(SectionState.Content(listOf(mappedItem)), vm.testStateValue)
        verify(exactly = 1) { mapper.mapShortItemList(listOf(item)) }
        vm.testCancelScope()
        paginator.close()
    }

    private fun createVM(
        paginator: Paginator.Store<Item>,
        config: SectionConfig,
        interactor: ContentListInteractor,
        coordinator: ContentListRefreshCoordinator,
        pagingCoroutineContext: CoroutineContext,
        savedItemInteractor: SavedItemInteractor = mockk(relaxed = true),
        mapper: VideoItemUIMapper = mockk(relaxed = true),
    ) = SectionVM(
        paginator = paginator,
        config = config,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        router = mockk<AppRouter>(relaxed = true),
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
        contentListRefreshCoordinator = coordinator,
        pagingCoroutineContext = pagingCoroutineContext,
    )

    private fun paginator(coroutineContext: CoroutineContext) = Paginator.Store<Item>(
        comparator = { old, new -> old.id == new.id },
        coroutineContext = coroutineContext,
    )

    private fun config(id: String) = SectionConfig(
        id = id,
        title = id,
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    private fun emptyPage(
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse<Item>(
        items = emptyList(),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )

    private fun page(
        item: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = listOf(item),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )
}
