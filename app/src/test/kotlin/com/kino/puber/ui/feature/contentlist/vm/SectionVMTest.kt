package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
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

class SectionVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun coordinatorRefresh_restartsPagingWithoutClearingSharedCache() = runTest {
        val paginator = paginator(testScheduler)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sideEffects = mutableListOf<Paginator.SideEffect>()
        val sideEffectCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            paginator.sideEffects.collect(sideEffects::add)
        }
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage()
        val vm = createVM(paginator, config("popular"), interactor, coordinator)
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
        val firstPaginator = paginator(testScheduler)
        val siblingPaginator = paginator(testScheduler)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val savedItemInteractor = mockk<SavedItemInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val firstConfig = config("popular")
        val siblingConfig = config("fresh")
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage()
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } returns Result.success(false)
        val first = createVM(firstPaginator, firstConfig, interactor, coordinator, savedItemInteractor)
        val sibling = createVM(siblingPaginator, siblingConfig, interactor, coordinator, savedItemInteractor)
        first.testOnStart()
        sibling.testOnStart()
        testScheduler.advanceUntilIdle()

        first.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        coVerify(timeout = 1_000, exactly = 2) { interactor.loadPage(firstConfig, page = 1) }
        coVerify(timeout = 1_000, exactly = 2) { interactor.loadPage(siblingConfig, page = 1) }
        first.testCancelScope()
        sibling.testCancelScope()
        firstPaginator.close()
        siblingPaginator.close()
    }

    private fun createVM(
        paginator: Paginator.Store<Item>,
        config: SectionConfig,
        interactor: ContentListInteractor,
        coordinator: ContentListRefreshCoordinator,
        savedItemInteractor: SavedItemInteractor = mockk(relaxed = true),
    ) = SectionVM(
        paginator = paginator,
        config = config,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        mapper = mockk<VideoItemUIMapper>(relaxed = true),
        router = mockk<AppRouter>(relaxed = true),
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
        contentListRefreshCoordinator = coordinator,
    )

    private fun paginator(testScheduler: TestCoroutineScheduler) = Paginator.Store<Item>(
        comparator = { old, new -> old.id == new.id },
        coroutineContext = StandardTestDispatcher(testScheduler),
    )

    private fun config(id: String) = SectionConfig(id = id, title = id)

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    private fun emptyPage() = PaginatedResponse<Item>(
        items = emptyList(),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )
}
