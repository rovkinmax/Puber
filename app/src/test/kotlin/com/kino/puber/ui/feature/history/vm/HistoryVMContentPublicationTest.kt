package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

class HistoryVMContentPublicationTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
        private const val REFRESHED_VIDEO_ID = 110
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var router: AppRouter

    @BeforeEach
    fun setUp() {
        api = mockk()
        router = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun concurrentMenuTransitionWinsOverContentPublicationAndKeepsRuntimeAligned() {
        val pageCalls = AtomicInteger()
        val publicationEntered = CountDownLatch(1)
        val releasePublication = CountDownLatch(1)
        val menuCompleted = CountDownLatch(1)
        val mapper = spyk(HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())))
        every { mapper.map(any<List<History>>()) } answers {
            val history = firstArg<List<History>>()
            if (history.firstOrNull()?.video?.id == REFRESHED_VIDEO_ID) {
                publicationEntered.countDown()
                check(releasePublication.await(2, TimeUnit.SECONDS))
            }
            callOriginal()
        }
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1), movie(2))))
            } else {
                Result.success(
                    page(
                        listOf(
                            movie(itemId = 1, videoId = REFRESHED_VIDEO_ID),
                            movie(2),
                        ),
                    ),
                )
            }
        }
        val vm = createVm(mapper)
        vm.testOnStart()
        val menuItem = awaitContent(vm).items.last()

        vm.onAction(CommonAction.Refresh)

        assertTrue(publicationEntered.await(2, TimeUnit.SECONDS))
        val menuThread = thread {
            vm.onAction(CommonAction.ItemFocused(menuItem))
            vm.onAction(HistoryAction.OpenContextMenu(menuItem))
            menuCompleted.countDown()
        }
        assertFalse(menuCompleted.await(100, TimeUnit.MILLISECONDS))

        releasePublication.countDown()
        assertTrue(menuCompleted.await(2, TimeUnit.SECONDS))
        menuThread.join(2_000)
        val published = awaitContent(vm) {
            it.items.first().deletionMediaId == REFRESHED_VIDEO_ID &&
                it.focusKey == menuItem.rowKey &&
                it.openMenuKey == menuItem.rowKey
        }
        val runtime = vm.testRuntimeState

        assertEquals(menuItem.rowKey, published.focusKey)
        assertEquals(menuItem.rowKey, published.openMenuKey)
        assertEquals(published.focusKey, runtime.focusedKey)
        assertEquals(published.openMenuKey, runtime.openMenuKey)
    }

    @Test
    fun concurrentMenuTransitionWaitsForAvailabilityPublicationAndPreservesLatestContent() {
        val availabilityRead = CountDownLatch(1)
        val releaseAvailabilityWrite = CountDownLatch(1)
        val paginationEntered = CountDownLatch(1)
        val releasePagination = CountDownLatch(1)
        val menuWorkerAtLockAcquire = CountDownLatch(1)
        val menuCompleted = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(
                items = listOf(movie(1), movie(2)),
                current = 1,
                total = 2,
            ),
        )
        coEvery { api.getHistoryData(2) } coAnswers {
            paginationEntered.countDown()
            check(releasePagination.await(2, TimeUnit.SECONDS))
            Result.success(
                page(
                    items = listOf(movie(3)),
                    current = 2,
                    total = 2,
                ),
            )
        }
        val vm = createVm(HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())))
        vm.testOnStart()
        val menuItem = awaitContent(vm).items.last()
        vm.testAfterDeleteAvailabilityRead = {
            vm.testAfterDeleteAvailabilityRead = null
            availabilityRead.countDown()
            check(releaseAvailabilityWrite.await(2, TimeUnit.SECONDS))
        }
        vm.testBeforeFocusPublicationLockAcquire = {
            vm.testBeforeFocusPublicationLockAcquire = null
            menuWorkerAtLockAcquire.countDown()
        }

        val paginationThread = thread {
            vm.onAction(CommonAction.LoadMore)
        }

        assertTrue(availabilityRead.await(2, TimeUnit.SECONDS))
        val menuThread = thread {
            vm.onAction(CommonAction.ItemFocused(menuItem))
            vm.onAction(HistoryAction.OpenContextMenu(menuItem))
            menuCompleted.countDown()
        }
        assertTrue(menuWorkerAtLockAcquire.await(2, TimeUnit.SECONDS))
        assertTrue(
            awaitBlockedBeforeCompletion(menuThread, menuCompleted),
            "Focus/menu worker completed instead of blocking on contentPublicationLock",
        )
        assertEquals(1L, menuCompleted.count)

        releaseAvailabilityWrite.countDown()
        assertTrue(menuCompleted.await(2, TimeUnit.SECONDS))
        assertTrue(paginationEntered.await(2, TimeUnit.SECONDS))
        val loadingMore = awaitContent(vm) {
            it.isLoadingMore &&
                it.items.map(HistoryItemUIState::itemId) == listOf(1, 2) &&
                it.focusKey == menuItem.rowKey &&
                it.openMenuKey == menuItem.rowKey
        }
        assertFalse(loadingMore.isDeleteExactMediaAvailable)

        releasePagination.countDown()
        paginationThread.join(2_000)
        menuThread.join(2_000)
        val published = awaitContent(vm) {
            !it.isLoadingMore &&
                it.items.map(HistoryItemUIState::itemId) == listOf(1, 2, 3) &&
                it.focusKey == menuItem.rowKey &&
                it.openMenuKey == menuItem.rowKey
        }
        val runtime = vm.testRuntimeState

        assertTrue(published.isDeleteExactMediaAvailable)
        assertEquals(menuItem.rowKey, published.focusKey)
        assertEquals(menuItem.rowKey, published.openMenuKey)
        assertEquals(published.focusKey, runtime.focusedKey)
        assertEquals(published.openMenuKey, runtime.openMenuKey)
    }

    private fun awaitBlockedBeforeCompletion(
        worker: Thread,
        completed: CountDownLatch,
    ): Boolean {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            if (worker.state == Thread.State.BLOCKED) return true
            if (completed.count == 0L) return false
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for worker contention; state=${worker.state}")
    }

    private fun createVm(mapper: HistoryUIMapper): HistoryVM {
        val screens = mockk<Screens>(relaxed = true)
        val errorHandler = mockk<ErrorHandler>(relaxed = true)
        every { router.screens } returns screens
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
        return HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = spyk(
                HistoryInteractor(
                    api = api,
                    itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true),
                ),
            ),
            mapper = mapper,
            router = router,
            errorHandler = errorHandler,
        )
    }

    private fun awaitContent(
        vm: HistoryVM,
        predicate: (HistoryViewState.Content) -> Boolean = { true },
    ): HistoryViewState.Content {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testStateValue
            if (state is HistoryViewState.Content && predicate(state)) {
                return state
            }
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for History state; last=${vm.testStateValue}")
    }

    private fun page(
        items: List<History>,
        current: Int = 1,
        total: Int = 1,
    ): PaginatedResponse<History> {
        return PaginatedResponse(
            items = items,
            pagination = Pagination(
                current = current,
                perpage = 20,
                total = total,
                totalItems = items.size,
            ),
        )
    }

    private fun movie(
        itemId: Int,
        videoId: Int = itemId * 100,
    ): History {
        return History(
            item = Item(
                id = itemId,
                title = "Synthetic movie $itemId",
                type = ItemType.MOVIE,
            ),
            video = Video(
                id = videoId,
                number = itemId,
                watching = WatchingInfo(time = 120, duration = 600),
            ),
        )
    }
}
