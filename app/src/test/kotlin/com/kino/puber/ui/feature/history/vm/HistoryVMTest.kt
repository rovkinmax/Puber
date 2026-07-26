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
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class HistoryVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var itemDetailsRepository: ItemDetailsRepository
    private lateinit var interactor: HistoryInteractor
    private lateinit var mapper: HistoryUIMapper
    private lateinit var screens: Screens
    private lateinit var router: AppRouter
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setUp() {
        api = mockk()
        itemDetailsRepository = mockk(relaxed = true)
        interactor = spyk(HistoryInteractor(api, itemDetailsRepository))
        mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider()))
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)

        every { router.screens } returns screens
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun initialLoad_mapsContentAndEmptyAndErrorStates() {
        val contentVM = startWith(page(listOf(movie(1))))
        assertEquals(listOf(1), awaitContent(contentVM).itemIds())

        val emptyVM = startWith(page(emptyList()))
        awaitState(emptyVM) { it == HistoryViewState.Empty }

        coEvery { api.getHistoryData(1) } returns Result.failure(IOException("initial failure"))
        val errorVM = createVM().also(HistoryVM::testOnStart)
        val error = awaitState(errorVM) { it is HistoryViewState.Error } as HistoryViewState.Error
        assertEquals("initial failure", error.message)
    }

    @Test
    fun loadMore_isSerializedAndAppendsTheNextPage() {
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(listOf(movie(1)), current = 1, total = 2),
        )
        coEvery { api.getHistoryData(2) } coAnswers {
            delay(150)
            Result.success(page(listOf(movie(2)), current = 2, total = 2))
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm) { it.itemIds() == listOf(1) }

        vm.onAction(CommonAction.LoadMore)
        vm.onAction(CommonAction.LoadMore)

        awaitContent(vm) { it.isLoadingMore }
        assertEquals(listOf(1, 2), awaitContent(vm) { it.itemIds() == listOf(1, 2) }.itemIds())
        coVerify(exactly = 1) { api.getHistoryData(2) }
    }

    @Test
    fun nextPageFailure_keepsLoadedContent() {
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(listOf(movie(1)), current = 1, total = 2),
        )
        coEvery { api.getHistoryData(2) } coAnswers {
            delay(100)
            Result.failure(IOException("page failure"))
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)

        vm.onAction(CommonAction.LoadMore)

        awaitContent(vm) { it.isLoadingMore }
        val content = awaitContent(vm) { !it.isLoadingMore }
        assertEquals(listOf(1), content.itemIds())
        assertEquals("page failure", content.nextPageErrorMessage)
        verify { errorHandler.map(match { it.message == "page failure" }) }
    }

    @Test
    fun duplicateOnlyPage_automaticallyContinuesToRenderableLaterPageWithoutDuplicatesOrFocusLoss() {
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(listOf(movie(1)), current = 1, total = 3),
        )
        coEvery { api.getHistoryData(2) } returns Result.success(
            page(
                listOf(movie(itemId = 1, recordId = 11)),
                current = 2,
                total = 3,
            ),
        )
        coEvery { api.getHistoryData(3) } returns Result.success(
            page(listOf(movie(2)), current = 3, total = 3),
        )
        val vm = createVM().also(HistoryVM::testOnStart)
        val first = awaitContent(vm).items.single()
        vm.onAction(CommonAction.ItemFocused(first))

        vm.onAction(CommonAction.LoadMore)

        val content = awaitContent(vm) { it.itemIds() == listOf(1, 2) }
        assertEquals(listOf(1, 2), content.itemIds())
        assertEquals(1, content.focusedItemId())
        assertFalse(content.hasMorePages)
        coVerify(exactly = 1) { api.getHistoryData(2) }
        coVerify(exactly = 1) { api.getHistoryData(3) }
    }

    @Test
    fun failedNextPage_explicitRetryAtSameThresholdDoesNotDuplicateRequestsRowsOrLoseFocus() {
        val pageTwoCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(listOf(movie(1)), current = 1, total = 2),
        )
        coEvery { api.getHistoryData(2) } coAnswers {
            if (pageTwoCalls.incrementAndGet() == 1) {
                Result.failure(IOException("page failure"))
            } else {
                Result.success(
                    page(
                        listOf(movie(itemId = 1, recordId = 11), movie(2)),
                        current = 2,
                        total = 2,
                    ),
                )
            }
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        val first = awaitContent(vm).items.single()
        vm.onAction(CommonAction.ItemFocused(first))

        vm.onAction(CommonAction.LoadMore)

        val failed = awaitContent(vm) { it.nextPageErrorMessage == "page failure" }
        assertEquals(listOf(1), failed.itemIds())
        assertEquals(1, failed.focusedItemId())
        assertEquals(1, pageTwoCalls.get())

        vm.onAction(CommonAction.ReloadNextPage)

        val retried = awaitContent(vm) { it.itemIds() == listOf(1, 2) }
        assertEquals(listOf(1, 2), retried.itemIds())
        assertEquals(1, retried.focusedItemId())
        assertEquals(null, retried.nextPageErrorMessage)
        assertEquals(2, pageTwoCalls.get())
        coVerify(exactly = 2) { api.getHistoryData(2) }
    }

    @Test
    fun refreshAfterPageError_keepsContentVisible() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1)), current = 1, total = 2))
            } else {
                delay(150)
                Result.success(page(listOf(movie(itemId = 1, videoId = 112))))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            delay(100)
            Result.failure(IOException("page failure"))
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        awaitContent(vm) { it.isLoadingMore }
        awaitContent(vm) { !it.isLoadingMore }

        vm.onAction(CommonAction.Refresh)

        val refreshing = awaitContent(vm) { it.isRefreshing }
        assertEquals(100, refreshing.items.single().deletionMediaId)
        assertEquals(
            112,
            awaitContent(vm) { it.items.single().deletionMediaId == 112 }
                .items.single().deletionMediaId,
        )
    }

    @Test
    fun refresh_keepsContentVisibleAndResetsTraversalForAuthoritativePageOne() {
        val calls = AtomicInteger()
        val first = movie(itemId = 1, videoId = 111)
        val refreshed = movie(itemId = 1, videoId = 112)
        coEvery { api.getHistoryData(1) } coAnswers {
            if (calls.incrementAndGet() == 1) {
                Result.success(page(listOf(first)))
            } else {
                delay(150)
                Result.success(page(listOf(refreshed)))
            }
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm) { it.items.single().deletionMediaId == 111 }

        vm.onAction(CommonAction.Refresh)

        val refreshing = awaitContent(vm) { it.isRefreshing }
        assertEquals(111, refreshing.items.single().deletionMediaId)
        assertEquals(
            112,
            awaitContent(vm) { it.items.single().deletionMediaId == 112 }
                .items.single().deletionMediaId,
        )
    }

    @Test
    fun failedRetainedRefresh_restoresDataSeedsTraversalAndBlocksInterleavedLoadMore() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1)), current = 1, total = 2))
            } else {
                delay(150)
                Result.failure(IOException("refresh failure"))
            }
        }
        coEvery { api.getHistoryData(2) } returns Result.success(
            page(listOf(movie(2)), current = 2, total = 2),
        )
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)

        vm.onAction(CommonAction.Refresh)
        awaitContent(vm) { it.isRefreshing }
        vm.onAction(CommonAction.LoadMore)
        Thread.sleep(40)
        coVerify(exactly = 0) { api.getHistoryData(2) }

        val restored = awaitContent(vm) { !it.isRefreshing && pageOneCalls.get() == 2 }
        assertEquals(listOf(1), restored.itemIds())

        vm.onAction(CommonAction.LoadMore)
        assertEquals(listOf(1, 2), awaitContent(vm) { it.itemIds() == listOf(1, 2) }.itemIds())
        coVerify(exactly = 1) { api.getHistoryData(2) }
    }

    @Test
    fun retryAfterInitialError_restartsPageOneAndTraversal() {
        val calls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (calls.incrementAndGet() == 1) {
                Result.failure(IOException("initial failure"))
            } else {
                Result.success(page(listOf(movie(1))))
            }
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitState(vm) { it is HistoryViewState.Error }

        vm.onAction(CommonAction.RetryClicked)

        assertEquals(listOf(1), awaitContent(vm).itemIds())
    }

    @Test
    fun deletionFailure_keepsCardSerializesMutationAndSuspendsLoadMore() {
        val mutationEntered = CountDownLatch(1)
        val releaseMutation = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(listOf(movie(1)), current = 1, total = 2),
        )
        coEvery { api.getHistoryData(2) } returns Result.success(
            page(listOf(movie(2)), current = 2, total = 2),
        )
        coEvery { api.clearExactMediaHistory(any()) } coAnswers {
            mutationEntered.countDown()
            check(releaseMutation.await(2, TimeUnit.SECONDS))
            Result.failure(IOException("delete failure"))
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        val item = awaitContent(vm).items.single()

        val actionThread = thread {
            vm.onAction(HistoryAction.DeleteExactMedia(item))
        }
        assertTrue(mutationEntered.await(2, TimeUnit.SECONDS))
        vm.onAction(HistoryAction.DeleteExactMedia(item))
        vm.onAction(CommonAction.LoadMore)

        assertTrue(awaitContent(vm) { item.rowKey in it.deletingKeys }.deletingKeys.isNotEmpty())
        releaseMutation.countDown()
        actionThread.join(2_000)
        val restored = awaitContent(vm) { it.deletingKeys.isEmpty() }
        assertEquals(listOf(1), restored.itemIds())
        coVerify(exactly = 1) { api.clearExactMediaHistory(item.deletionMediaId) }
        verify(exactly = 0) { itemDetailsRepository.invalidate(item.itemId) }
        coVerify(exactly = 0) { api.getHistoryData(2) }
    }

    @Test
    fun deletingFirstItemAcrossLoadedPages_focusesOldNext() {
        verifyDeletionFocus(
            deleteIndex = 0,
            reconciledPageOne = listOf(movie(2), movie(3)),
            reconciledPageTwo = listOf(movie(4)),
            reconciledTotal = 2,
            expectedIds = listOf(2, 3, 4),
            expectedFocusItemId = 2,
            expectedPageTwoCalls = 2,
        )
    }

    @Test
    fun deletingMiddleItemAcrossLoadedPages_focusesOldNext() {
        verifyDeletionFocus(
            deleteIndex = 1,
            reconciledPageOne = listOf(movie(1), movie(3)),
            reconciledPageTwo = listOf(movie(4)),
            reconciledTotal = 2,
            expectedIds = listOf(1, 3, 4),
            expectedFocusItemId = 3,
            expectedPageTwoCalls = 2,
        )
    }

    @Test
    fun deletingLastItemAcrossLoadedPages_focusesPreviousAndHonorsPageContraction() {
        verifyDeletionFocus(
            deleteIndex = 3,
            reconciledPageOne = listOf(movie(1), movie(2), movie(3)),
            reconciledPageTwo = null,
            reconciledTotal = 1,
            expectedIds = listOf(1, 2, 3),
            expectedFocusItemId = 3,
            expectedPageTwoCalls = 1,
        )
    }

    @Test
    fun deletingFinalVisibleItem_transitionsToEmpty() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1))))
            } else {
                Result.success(page(emptyList(), total = 0))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        val vm = createVM().also(HistoryVM::testOnStart)
        val item = awaitContent(vm).items.single()

        vm.onAction(HistoryAction.DeleteExactMedia(item))

        awaitState(vm) { it == HistoryViewState.Empty }
        assertEquals(2, pageOneCalls.get())
    }

    @Test
    fun reconciliationFailure_keepsPostDeleteContentAndRetryUsesSameBoundedReload() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1), movie(2)), total = 3))
                2 -> Result.success(page(listOf(movie(2), movie(3)), total = 3))
                else -> Result.success(page(listOf(movie(2), movie(3)), total = 3))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            when (pageTwoCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(3), movie(4)), current = 2, total = 3))
                2 -> Result.failure(IOException("reconcile failure"))
                else -> Result.success(page(listOf(movie(4), movie(5)), current = 2, total = 3))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        val loaded = awaitContent(vm) { it.itemIds() == listOf(1, 2, 3, 4) }
        val deleted = loaded.items.first()

        vm.onAction(HistoryAction.DeleteExactMedia(deleted))

        val retained = awaitContent(vm) {
            pageOneCalls.get() == 2 &&
                pageTwoCalls.get() == 2 &&
                it.itemIds() == listOf(2, 3, 4) &&
                !it.isRefreshing &&
                it.reloadErrorMessage == "reconcile failure"
        }
        assertEquals(2, retained.focusedItemId())
        assertEquals("reconcile failure", retained.reloadErrorMessage)

        vm.onAction(CommonAction.LoadMore)
        vm.onAction(CommonAction.Refresh)
        vm.onAction(HistoryAction.DeleteExactMedia(retained.items.first()))
        Thread.sleep(40)
        assertEquals(2, pageOneCalls.get())
        assertEquals(2, pageTwoCalls.get())
        coVerify(exactly = 1) { api.clearExactMediaHistory(deleted.deletionMediaId) }

        vm.onAction(HistoryAction.RetryReconciliation)

        val reconciled = awaitContent(vm) {
            pageOneCalls.get() == 3 &&
                pageTwoCalls.get() == 3 &&
                it.itemIds() == listOf(2, 3, 4, 5) &&
                !it.isRefreshing
        }
        assertEquals(2, reconciled.focusedItemId())
        assertEquals(null, reconciled.reloadErrorMessage)
        coVerify(exactly = 0) { api.getHistoryData(3) }
    }

    @Test
    fun onResumeDuringPendingReconciliation_runsAfterSuccessfulRetry() {
        val pageOneCalls = AtomicInteger()
        val deferredRefreshEntered = CountDownLatch(1)
        val releaseDeferredRefresh = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.success(page(listOf(movie(1), movie(2))))
                2 -> Result.failure(IOException("reconcile failure"))
                3 -> Result.success(page(listOf(movie(2), movie(3))))
                else -> {
                    deferredRefreshEntered.countDown()
                    check(releaseDeferredRefresh.await(2, TimeUnit.SECONDS))
                    Result.success(page(listOf(movie(2), movie(4))))
                }
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        val vm = createVM().also(HistoryVM::testOnStart)
        val deleted = awaitContent(vm).items.first()
        vm.onAction(HistoryAction.DeleteExactMedia(deleted))
        awaitContent(vm) { it.reloadErrorMessage == "reconcile failure" }

        vm.onAction(CommonAction.OnResume)
        vm.onAction(HistoryAction.RetryReconciliation)

        assertTrue(deferredRefreshEntered.await(2, TimeUnit.SECONDS))
        val refreshing = awaitContent(vm) {
            it.isRefreshing && it.itemIds() == listOf(2, 3)
        }
        assertEquals(listOf(2, 3), refreshing.itemIds())
        releaseDeferredRefresh.countDown()

        val final = awaitContent(vm) {
            pageOneCalls.get() == 4 && it.itemIds() == listOf(2, 4) && !it.isRefreshing
        }
        assertEquals(listOf(2, 4), final.itemIds())
    }

    private fun verifyDeletionFocus(
        deleteIndex: Int,
        reconciledPageOne: List<History>,
        reconciledPageTwo: List<History>?,
        reconciledTotal: Int,
        expectedIds: List<Int>,
        expectedFocusItemId: Int,
        expectedPageTwoCalls: Int,
    ) {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        coEvery { api.getHistoryData(any()) } coAnswers {
            when (firstArg<Int>()) {
                1 -> {
                    val items = if (pageOneCalls.incrementAndGet() == 1) {
                        listOf(movie(1), movie(2))
                    } else {
                        reconciledPageOne
                    }
                    val total = if (pageOneCalls.get() == 1) 2 else reconciledTotal
                    Result.success(page(items, current = 1, total = total))
                }
                2 -> {
                    val call = pageTwoCalls.incrementAndGet()
                    val items = if (call == 1) {
                        listOf(movie(3), movie(4))
                    } else {
                        requireNotNull(reconciledPageTwo)
                    }
                    Result.success(page(items, current = 2, total = 2))
                }
                else -> error("Unexpected page")
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        val loaded = awaitContent(vm) { it.itemIds() == listOf(1, 2, 3, 4) }
        val deleted = loaded.items[deleteIndex]

        vm.onAction(HistoryAction.DeleteExactMedia(deleted))

        val reconciled = awaitContent(vm) {
            pageOneCalls.get() == 2 &&
                pageTwoCalls.get() == expectedPageTwoCalls &&
                it.itemIds() == expectedIds
        }
        assertEquals(expectedFocusItemId, reconciled.focusedItemId())
        coVerify(exactly = 1) { api.clearExactMediaHistory(deleted.deletionMediaId) }
        assertEquals(expectedPageTwoCalls, pageTwoCalls.get())
    }

    private fun createVM(): HistoryVM {
        return HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = interactor,
            mapper = mapper,
            router = router,
            errorHandler = errorHandler,
        )
    }

    private fun startWith(response: PaginatedResponse<History>): HistoryVM {
        coEvery { api.getHistoryData(1) } returns Result.success(response)
        return createVM().also(HistoryVM::testOnStart)
    }

    private fun awaitContent(
        vm: HistoryVM,
        predicate: (HistoryViewState.Content) -> Boolean = { true },
    ): HistoryViewState.Content {
        return awaitState(vm) { state ->
            state is HistoryViewState.Content && predicate(state)
        } as HistoryViewState.Content
    }

    private fun awaitState(
        vm: HistoryVM,
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

    private fun HistoryViewState.Content.itemIds(): List<Int> = items.map(HistoryItemUIState::itemId)

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
        recordId: Int? = null,
        videoId: Int = itemId * 100,
        videoNumber: Int = itemId,
    ): History {
        return History(
            recordId = recordId,
            item = Item(id = itemId, title = "Synthetic movie $itemId", type = ItemType.MOVIE),
            video = Video(
                id = videoId,
                number = videoNumber,
                watching = WatchingInfo(time = 120, duration = 600),
            ),
        )
    }

}
