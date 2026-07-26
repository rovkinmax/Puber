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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class HistoryVMDeletionSchedulingTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var itemDetailsRepository: ItemDetailsRepository
    private lateinit var router: AppRouter
    private lateinit var errorHandler: ErrorHandler
    private lateinit var vm: HistoryVM

    @BeforeEach
    fun setUp() {
        api = mockk()
        itemDetailsRepository = mockk(relaxed = true)
        router = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
        vm = HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = spyk(HistoryInteractor(api, itemDetailsRepository)),
            mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
            router = router,
            errorHandler = errorHandler,
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun deleteSelectionDuringRefresh_isQueuedOnceAndRetainsSemanticFocus() {
        val pageOneCalls = AtomicInteger()
        val refreshEntered = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1), movie(2))))
                2 -> {
                    refreshEntered.countDown()
                    check(releaseRefresh.await(2, TimeUnit.SECONDS))
                    Result.success(page(listOf(movie(1), movie(2))))
                }
                else -> Result.success(page(listOf(movie(2))))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val deleted = awaitContent().items.first()
        vm.onAction(HistoryAction.OpenContextMenu(deleted))

        vm.onAction(CommonAction.Refresh)

        assertTrue(refreshEntered.await(2, TimeUnit.SECONDS))
        val refreshing = awaitContent { it.isRefreshing }
        assertFalse(refreshing.isDeleteExactMediaAvailable)
        vm.onAction(HistoryAction.DeleteExactMedia(deleted))
        val queued = awaitContent {
            it.openMenuKey == null && it.focusKey == deleted.rowKey
        }
        assertFalse(queued.isDeleteExactMediaAvailable)
        coVerify(exactly = 0) { api.clearExactMediaHistory(any()) }

        releaseRefresh.countDown()

        val reconciled = awaitContent { it.itemIds() == listOf(2) }
        assertEquals(2, reconciled.focusedItemId())
        assertTrue(reconciled.isDeleteExactMediaAvailable)
        assertEquals(3, pageOneCalls.get())
        coVerify(exactly = 1) { api.clearExactMediaHistory(deleted.deletionMediaId) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(deleted.itemId) }
    }

    @Test
    fun queuedDeletionAfterRefresh_usesCurrentDeletionMediaIdForSurvivingRow() {
        val pageOneCalls = AtomicInteger()
        val refreshEntered = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(itemId = 1, videoId = 100))))
                2 -> {
                    refreshEntered.countDown()
                    check(releaseRefresh.await(2, TimeUnit.SECONDS))
                    Result.success(page(listOf(movie(itemId = 1, videoId = 120))))
                }
                else -> Result.success(page(emptyList()))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val staleItem = awaitContent().items.single()

        vm.onAction(CommonAction.Refresh)

        assertTrue(refreshEntered.await(2, TimeUnit.SECONDS))
        vm.onAction(HistoryAction.DeleteExactMedia(staleItem))
        releaseRefresh.countDown()

        awaitState { it == HistoryViewState.Empty }
        assertEquals(3, pageOneCalls.get())
        coVerify(exactly = 0) { api.clearExactMediaHistory(100) }
        coVerify(exactly = 1) { api.clearExactMediaHistory(120) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(1) }
    }

    @Test
    fun deletionDuringAuthoritativeContentPublication_waitsAndUsesPublishedMediaId() {
        val pageOneCalls = AtomicInteger()
        val publicationEntered = CountDownLatch(1)
        val releasePublication = CountDownLatch(1)
        val blockingMapper = spyk(
            HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
        )
        every { blockingMapper.map(any<List<History>>()) } answers {
            val history = firstArg<List<History>>()
            if (history.singleOrNull()?.video?.id == 120) {
                publicationEntered.countDown()
                check(releasePublication.await(2, TimeUnit.SECONDS))
            }
            callOriginal()
        }
        vm = HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = spyk(HistoryInteractor(api, itemDetailsRepository)),
            mapper = blockingMapper,
            router = router,
            errorHandler = errorHandler,
        )
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(itemId = 1, videoId = 100))))
                2 -> Result.success(page(listOf(movie(itemId = 1, videoId = 120))))
                else -> Result.success(page(emptyList()))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val staleMenuItem = awaitContent().items.single()

        vm.onAction(CommonAction.Refresh)

        assertTrue(publicationEntered.await(2, TimeUnit.SECONDS))
        val deletionThread = thread {
            vm.onAction(HistoryAction.DeleteExactMedia(staleMenuItem))
        }
        awaitRuntime { it.queuedDeletion?.rowKey == staleMenuItem.rowKey }
        coVerify(exactly = 0) { api.clearExactMediaHistory(any()) }

        releasePublication.countDown()
        deletionThread.join(2_000)

        awaitState { it == HistoryViewState.Empty }
        assertEquals(3, pageOneCalls.get())
        coVerify(exactly = 0) { api.clearExactMediaHistory(100) }
        coVerify(exactly = 1) { api.clearExactMediaHistory(120) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(1) }
    }

    @Test
    fun staleMenuActionAfterPublication_resolvesCurrentPublishedMediaId() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(itemId = 1, videoId = 100))))
                2 -> Result.success(page(listOf(movie(itemId = 1, videoId = 120))))
                else -> Result.success(page(emptyList()))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val staleMenuItem = awaitContent().items.single()

        vm.onAction(CommonAction.Refresh)
        awaitContent {
            pageOneCalls.get() == 2 &&
                it.items.single().deletionMediaId == 120 &&
                it.isDeleteExactMediaAvailable
        }

        vm.onAction(HistoryAction.DeleteExactMedia(staleMenuItem))

        awaitState { it == HistoryViewState.Empty }
        coVerify(exactly = 0) { api.clearExactMediaHistory(100) }
        coVerify(exactly = 1) { api.clearExactMediaHistory(120) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(1) }
    }

    @Test
    fun refreshToEmptyDropsQueuedDeletionWithoutIssuingStaleMutation() {
        val pageOneCalls = AtomicInteger()
        val refreshEntered = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1))))
            } else {
                refreshEntered.countDown()
                check(releaseRefresh.await(2, TimeUnit.SECONDS))
                Result.success(page(emptyList()))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns
            Result.failure(IllegalStateException("stale mutation must not run"))
        vm.testOnStart()
        val staleItem = awaitContent().items.single()

        vm.onAction(CommonAction.Refresh)

        assertTrue(refreshEntered.await(2, TimeUnit.SECONDS))
        vm.onAction(HistoryAction.DeleteExactMedia(staleItem))
        releaseRefresh.countDown()

        awaitState { it == HistoryViewState.Empty }
        coVerify(exactly = 0) { api.clearExactMediaHistory(any()) }
        verify(exactly = 0) { itemDetailsRepository.invalidate(any()) }
    }

    @Test
    fun refreshWithoutQueuedRowDropsDeletionWithoutMutatingAnotherRow() {
        val pageOneCalls = AtomicInteger()
        val refreshEntered = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1), movie(2))))
            } else {
                refreshEntered.countDown()
                check(releaseRefresh.await(2, TimeUnit.SECONDS))
                Result.success(page(listOf(movie(2))))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns
            Result.failure(IllegalStateException("missing queued row must not mutate"))
        vm.testOnStart()
        val staleItem = awaitContent().items.first()

        vm.onAction(CommonAction.Refresh)

        assertTrue(refreshEntered.await(2, TimeUnit.SECONDS))
        vm.onAction(HistoryAction.DeleteExactMedia(staleItem))
        releaseRefresh.countDown()

        val refreshed = awaitContent {
            it.itemIds() == listOf(2) &&
                !it.isRefreshing &&
                it.isDeleteExactMediaAvailable
        }
        assertTrue(refreshed.isDeleteExactMediaAvailable)
        coVerify(exactly = 0) { api.clearExactMediaHistory(any()) }
        verify(exactly = 0) { itemDetailsRepository.invalidate(any()) }
    }

    @Test
    fun deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        val paginationEntered = CountDownLatch(1)
        val releasePagination = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1), movie(2)), current = 1, total = 2))
            } else {
                Result.success(page(listOf(movie(1), movie(3)), current = 1, total = 2))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            if (pageTwoCalls.incrementAndGet() == 1) {
                paginationEntered.countDown()
                check(releasePagination.await(2, TimeUnit.SECONDS))
                Result.success(page(listOf(movie(3), movie(4)), current = 2, total = 2))
            } else {
                Result.success(page(listOf(movie(4)), current = 2, total = 2))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val deleted = awaitContent().items[1]
        vm.onAction(HistoryAction.OpenContextMenu(deleted))

        vm.onAction(CommonAction.LoadMore)

        assertTrue(paginationEntered.await(2, TimeUnit.SECONDS))
        val paginating = awaitContent { it.isLoadingMore }
        assertFalse(paginating.isDeleteExactMediaAvailable)
        vm.onAction(HistoryAction.DeleteExactMedia(deleted))
        val queued = awaitContent {
            it.openMenuKey == null && it.focusKey == deleted.rowKey
        }
        assertFalse(queued.isDeleteExactMediaAvailable)
        coVerify(exactly = 0) { api.clearExactMediaHistory(any()) }

        releasePagination.countDown()

        val reconciled = awaitContent { it.itemIds() == listOf(1, 3, 4) }
        assertEquals(3, reconciled.focusedItemId())
        assertTrue(reconciled.isDeleteExactMediaAvailable)
        assertEquals(2, pageOneCalls.get())
        assertEquals(2, pageTwoCalls.get())
        coVerify(exactly = 1) { api.clearExactMediaHistory(deleted.deletionMediaId) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(deleted.itemId) }
    }

    @Test
    fun deletingLastRenderedRow_keepsReconcilingStateUntilReflowedRowLoads() {
        val pageOneCalls = AtomicInteger()
        val reconciliationEntered = CountDownLatch(1)
        val releaseReconciliation = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1))))
                else -> {
                    reconciliationEntered.countDown()
                    check(releaseReconciliation.await(2, TimeUnit.SECONDS))
                    Result.success(page(listOf(movie(2))))
                }
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val deleted = awaitContent().items.single()

        val actionThread = thread {
            vm.onAction(HistoryAction.DeleteExactMedia(deleted))
        }
        assertTrue(reconciliationEntered.await(2, TimeUnit.SECONDS))
        val reconciling = awaitContent {
            it.items.isEmpty() && it.isRefreshing && it.reloadErrorMessage == null
        }
        assertFalse(reconciling.isDeleteExactMediaAvailable)
        assertFalse(vm.testStateValue is HistoryViewState.Empty)

        releaseReconciliation.countDown()
        actionThread.join(2_000)

        val reconciled = awaitContent { it.itemIds() == listOf(2) && !it.isRefreshing }
        assertEquals(2, reconciled.focusedItemId())
        assertEquals(2, pageOneCalls.get())
    }

    @Test
    fun deletingLastRenderedRow_reconciliationFailureKeepsRetryableNonEmptyState() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1))))
                2 -> Result.failure(IllegalStateException("reconcile failure"))
                else -> Result.success(page(listOf(movie(2))))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val deleted = awaitContent().items.single()

        vm.onAction(HistoryAction.DeleteExactMedia(deleted))

        val failed = awaitContent {
            it.items.isEmpty() &&
                !it.isRefreshing &&
                it.reloadErrorMessage == "reconcile failure"
        }
        assertFalse(failed.isDeleteExactMediaAvailable)
        assertFalse(vm.testStateValue is HistoryViewState.Empty)

        vm.onAction(HistoryAction.RetryReconciliation)

        val reconciled = awaitContent { it.itemIds() == listOf(2) && !it.isRefreshing }
        assertEquals(2, reconciled.focusedItemId())
        assertEquals(null, reconciled.reloadErrorMessage)
        assertEquals(3, pageOneCalls.get())
    }

    @Test
    fun unchangedReconciliationResponseSettlesWithoutPaginatorPublication() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            pageOneCalls.incrementAndGet()
            Result.success(page(listOf(movie(1), movie(2))))
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        vm.testOnStart()
        val initial = awaitContent()
        val deleted = initial.items.first()
        vm.onAction(CommonAction.ItemFocused(initial.items.last()))

        vm.onAction(CommonAction.Refresh)

        awaitContent {
            pageOneCalls.get() == 2 &&
                !it.isRefreshing &&
                it.focusedItemId() == 2
        }

        vm.onAction(HistoryAction.DeleteExactMedia(deleted))

        val reconciled = awaitContent {
            pageOneCalls.get() == 3 &&
                it.itemIds() == listOf(1, 2) &&
                !it.isRefreshing
        }
        assertTrue(reconciled.isDeleteExactMediaAvailable)
        assertEquals(2, reconciled.focusedItemId())
        coVerify(exactly = 1) { api.clearExactMediaHistory(deleted.deletionMediaId) }
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

    private fun awaitState(
        predicate: (HistoryViewState) -> Boolean,
    ): HistoryViewState {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testStateValue
            if (predicate(state)) return state
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

    private fun HistoryViewState.Content.itemIds(): List<Int> {
        return items.map(HistoryItemUIState::itemId)
    }

    private fun HistoryViewState.Content.focusedItemId(): Int? {
        return items.firstOrNull { it.rowKey == focusKey }?.itemId
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
