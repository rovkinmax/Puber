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
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

class HistoryVMQueuedDeletionInputGuardTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
        private const val REFRESHED_VIDEO_ID = 120
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var router: AppRouter
    private lateinit var vm: HistoryVM

    @BeforeEach
    fun setUp() {
        api = mockk()
        router = mockk(relaxed = true)
        vm = createVm()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun queuedDeletionDuringRefreshRejectsInputUntilReconciliationSettles() {
        val pageOneCalls = AtomicInteger()
        val busy = BusyDeletionLatches()
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1), movie(2))))
                2 -> {
                    busy.operationEntered.countDown()
                    awaitLatch(busy.releaseOperation)
                    Result.success(page(listOf(movie(1), movie(2))))
                }
                else -> {
                    busy.reconciliationEntered.countDown()
                    awaitLatch(busy.releaseReconciliation)
                    Result.success(page(listOf(movie(2))))
                }
            }
        }
        stubBlockingDeletion(busy)
        vm.testOnStart()
        val initial = awaitContent()

        vm.onAction(CommonAction.Refresh)

        exerciseQueuedDeletionGuard(
            busy = busy,
            deleted = initial.items.first(),
            retainedItemId = 2,
            expectedBusyOperation = { it is HistoryOperation.LoadingFirstPage },
        )
    }

    @Test
    fun queuedDeletionDuringPaginationRejectsInputUntilReconciliationSettles() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        val busy = BusyDeletionLatches()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1), movie(2)), current = 1, total = 2))
            } else {
                busy.reconciliationEntered.countDown()
                awaitLatch(busy.releaseReconciliation)
                Result.success(page(listOf(movie(2)), current = 1, total = 2))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            if (pageTwoCalls.incrementAndGet() == 1) {
                busy.operationEntered.countDown()
                awaitLatch(busy.releaseOperation)
            }
            Result.success(page(listOf(movie(3)), current = 2, total = 2))
        }
        stubBlockingDeletion(busy)
        vm.testOnStart()
        val initial = awaitContent()

        vm.onAction(CommonAction.LoadMore)

        exerciseQueuedDeletionGuard(
            busy = busy,
            deleted = initial.items.first(),
            retainedItemId = 2,
            settledIds = listOf(2, 3),
            expectedBusyOperation = { it is HistoryOperation.LoadingNextPage },
        )
    }

    @Test
    fun queuedDeletionDuringPublicationRejectsInputUntilReconciliationSettles() {
        val pageOneCalls = AtomicInteger()
        val busy = BusyDeletionLatches()
        val blockingMapper = spyk(HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())))
        every { blockingMapper.map(any<List<History>>()) } answers {
            val history = firstArg<List<History>>()
            if (history.firstOrNull()?.video?.id == REFRESHED_VIDEO_ID) {
                busy.operationEntered.countDown()
                awaitLatch(busy.releaseOperation)
            }
            callOriginal()
        }
        vm = createVm(blockingMapper)
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1), movie(2))))
                2 -> Result.success(
                    page(
                        listOf(
                            movie(itemId = 1, videoId = REFRESHED_VIDEO_ID),
                            movie(2),
                        ),
                    ),
                )
                else -> {
                    busy.reconciliationEntered.countDown()
                    awaitLatch(busy.releaseReconciliation)
                    Result.success(page(listOf(movie(2))))
                }
            }
        }
        stubBlockingDeletion(busy)
        vm.testOnStart()
        val initial = awaitContent()

        vm.onAction(CommonAction.Refresh)

        exerciseQueuedDeletionGuard(
            busy = busy,
            deleted = initial.items.first(),
            retainedItemId = 2,
            expectedDeletionMediaId = REFRESHED_VIDEO_ID,
            expectedBusyOperation = { it is HistoryOperation.AwaitingPublication },
        )
    }

    private fun exerciseQueuedDeletionGuard(
        busy: BusyDeletionLatches,
        deleted: HistoryItemUIState,
        retainedItemId: Int,
        settledIds: List<Int> = listOf(retainedItemId),
        expectedDeletionMediaId: Int = deleted.deletionMediaId,
        expectedBusyOperation: (HistoryOperation) -> Boolean,
    ) {
        assertTrue(busy.operationEntered.await(2, TimeUnit.SECONDS))
        assertTrue(expectedBusyOperation(vm.testRuntimeState.operation))
        val retained = awaitContent().items.first { it.itemId == retainedItemId }
        val queueThread = thread {
            vm.onAction(HistoryAction.DeleteExactMedia(deleted))
        }
        val queued = awaitRuntime { it.queuedDeletion?.rowKey == deleted.rowKey }
        assertTrue(expectedBusyOperation(queued.operation))
        assertTrue(queued.isDeletionFlowActive())
        dispatchBlockedActions(deleted, retained)
        assertInputRejected()

        busy.releaseOperation.countDown()
        assertTrue(busy.mutationEntered.await(2, TimeUnit.SECONDS))
        dispatchBlockedActions(deleted, retained)
        assertInputRejected()

        busy.releaseMutation.countDown()
        assertTrue(busy.reconciliationEntered.await(2, TimeUnit.SECONDS))
        dispatchBlockedActions(deleted, retained)
        assertInputRejected()

        busy.releaseReconciliation.countDown()
        queueThread.join(2_000)
        val settled = awaitContent {
            it.items.map(HistoryItemUIState::itemId) == settledIds &&
                !it.isRefreshing &&
                it.deletingKeys.isEmpty()
        }
        vm.onAction(CommonAction.ItemSelected(settled.items.first { it.itemId == retainedItemId }))

        verify(exactly = 1) { router.navigateTo(any()) }
        coVerify(exactly = 1) { api.clearExactMediaHistory(expectedDeletionMediaId) }
    }

    private fun dispatchBlockedActions(
        deleted: HistoryItemUIState,
        retained: HistoryItemUIState,
    ) {
        vm.onAction(CommonAction.ItemSelected(retained))
        vm.onAction(HistoryAction.OpenContextMenu(retained))
        vm.onAction(
            HistoryAction.Play(
                item = retained,
                startMode = PlayerStartMode.ResumeIfAvailable,
            ),
        )
        vm.onAction(HistoryAction.OpenDetails(retained))
        vm.onAction(HistoryAction.DeleteExactMedia(deleted))
    }

    private fun assertInputRejected() {
        verify(exactly = 0) { router.navigateTo(any()) }
        assertNull(vm.testRuntimeState.openMenuKey)
        assertNull(awaitContent().openMenuKey)
    }

    private fun stubBlockingDeletion(busy: BusyDeletionLatches) {
        coEvery { api.clearExactMediaHistory(any()) } coAnswers {
            busy.mutationEntered.countDown()
            awaitLatch(busy.releaseMutation)
            Result.success(Unit)
        }
    }

    private fun createVm(
        mapper: HistoryUIMapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
    ): HistoryVM {
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

    private fun awaitRuntime(
        predicate: (HistoryRuntimeState) -> Boolean,
    ): HistoryRuntimeState {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testRuntimeState
            if (predicate(state)) return state
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for History runtime; last=${vm.testRuntimeState}")
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

    private fun awaitLatch(latch: CountDownLatch) {
        check(latch.await(2, TimeUnit.SECONDS))
    }

    private class BusyDeletionLatches {
        val operationEntered = CountDownLatch(1)
        val releaseOperation = CountDownLatch(1)
        val mutationEntered = CountDownLatch(1)
        val releaseMutation = CountDownLatch(1)
        val reconciliationEntered = CountDownLatch(1)
        val releaseReconciliation = CountDownLatch(1)
    }

}
