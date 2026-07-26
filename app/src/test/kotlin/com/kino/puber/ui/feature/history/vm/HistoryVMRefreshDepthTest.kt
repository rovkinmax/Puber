package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
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
import io.mockk.verify
import java.io.IOException
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

internal class HistoryVMRefreshDepthTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var itemDetailsRepository: ItemDetailsRepository
    private lateinit var screens: Screens
    private lateinit var router: AppRouter
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setUp() {
        api = mockk()
        itemDetailsRepository = mockk(relaxed = true)
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
    fun onResumeDuringLoadMore_runsDeferredRefreshAndKeepsLaterPageAvailable() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        val releasePageTwo = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            val call = pageOneCalls.incrementAndGet()
            val deletionMediaId = if (call == 1) 100 else 120
            Result.success(
                page(
                    listOf(movie(itemId = 1, videoId = deletionMediaId)),
                    current = 1,
                    total = 2,
                ),
            )
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            if (pageTwoCalls.incrementAndGet() == 1) {
                check(releasePageTwo.await(2, TimeUnit.SECONDS))
            }
            Result.success(page(listOf(movie(2)), current = 2, total = 3))
        }
        coEvery { api.getHistoryData(3) } returns Result.success(
            page(listOf(movie(3)), current = 3, total = 3),
        )
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)

        val loadThread = thread { vm.onAction(CommonAction.LoadMore) }
        awaitContent(vm) { it.isLoadingMore }
        vm.onAction(CommonAction.OnResume)
        releasePageTwo.countDown()
        loadThread.join(2_000)

        val refreshed = awaitContent(vm) {
            pageOneCalls.get() == 2 && it.items.first().deletionMediaId == 120
        }
        assertFalse(refreshed.isRefreshing)
        assertEquals(listOf(1, 2), refreshed.itemIds())
        assertTrue(refreshed.hasMorePages)
        coVerify(exactly = 2) { api.getHistoryData(1) }
        coVerify(exactly = 2) { api.getHistoryData(2) }

        vm.onAction(CommonAction.LoadMore)

        val completed = awaitContent(vm) { it.itemIds() == listOf(1, 2, 3) }
        assertFalse(completed.hasMorePages)
        coVerify(exactly = 1) { api.getHistoryData(3) }
    }

    @Test
    fun onResumeRefresh_refetchesLoadedDepthAndPreservesFocusedLaterPageItem() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        val refreshedPageTwoEntered = CountDownLatch(1)
        val releaseRefreshedPageTwo = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1)), current = 1, total = 2))
            } else {
                Result.success(page(listOf(movie(3), movie(1)), current = 1, total = 2))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            val call = pageTwoCalls.incrementAndGet()
            if (call == 2) {
                refreshedPageTwoEntered.countDown()
                check(releaseRefreshedPageTwo.await(2, TimeUnit.SECONDS))
            }
            Result.success(
                page(
                    listOf(
                        movie(itemId = 1, videoId = 120),
                        movie(itemId = 2, videoId = if (call == 1) 200 else 220),
                    ),
                    current = 2,
                    total = 2,
                ),
            )
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        val loaded = awaitContent(vm) { it.itemIds() == listOf(1, 2) }
        vm.onAction(CommonAction.ItemFocused(loaded.items.last()))

        val refreshThread = thread { vm.onAction(CommonAction.OnResume) }
        assertTrue(refreshedPageTwoEntered.await(2, TimeUnit.SECONDS))
        val refreshing = awaitContent(vm) {
            it.isRefreshing && it.itemIds() == listOf(1, 2)
        }
        assertEquals(2, refreshing.focusedItemId())
        releaseRefreshedPageTwo.countDown()
        refreshThread.join(2_000)

        val refreshed = awaitContent(vm) {
            it.itemIds() == listOf(3, 1, 2) &&
                it.items.last().deletionMediaId == 220 &&
                !it.isRefreshing
        }
        assertEquals(2, refreshed.focusedItemId())
        assertEquals(2, pageOneCalls.get())
        assertEquals(2, pageTwoCalls.get())
    }

    @Test
    fun refreshFailureOnLaterPage_restoresEntireStableWindowAndFocus() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1)), current = 1, total = 2))
            } else {
                Result.success(page(listOf(movie(3)), current = 1, total = 2))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            if (pageTwoCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(2)), current = 2, total = 2))
            } else {
                Result.failure(IOException("page two refresh failure"))
            }
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        val loaded = awaitContent(vm) { it.itemIds() == listOf(1, 2) }
        vm.onAction(CommonAction.ItemFocused(loaded.items.last()))

        vm.onAction(CommonAction.Refresh)

        val restored = awaitContent(vm) {
            !it.isRefreshing &&
                it.itemIds() == listOf(1, 2) &&
                pageTwoCalls.get() == 2
        }
        assertEquals(2, restored.focusedItemId())
        assertEquals(2, pageOneCalls.get())
        assertEquals(2, pageTwoCalls.get())
        verify { errorHandler.map(match { it.message == "page two refresh failure" }) }
    }

    @Test
    fun refreshPageMismatch_restoresStableWindowInsteadOfPublishingTruncatedData() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            val itemId = if (pageOneCalls.incrementAndGet() == 1) 1 else 3
            Result.success(page(listOf(movie(itemId)), current = 1, total = 2))
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            if (pageTwoCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(2)), current = 2, total = 2))
            } else {
                Result.success(page(listOf(movie(3)), current = 1, total = 2))
            }
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        val loaded = awaitContent(vm) { it.itemIds() == listOf(1, 2) }
        vm.onAction(CommonAction.ItemFocused(loaded.items.last()))

        vm.onAction(CommonAction.Refresh)

        val restored = awaitContent(vm) {
            !it.isRefreshing &&
                it.itemIds() == listOf(1, 2) &&
                pageTwoCalls.get() == 2
        }
        assertEquals(2, restored.focusedItemId())
        verify {
            errorHandler.map(
                match { it.message == "History pagination did not match the requested page" },
            )
        }
    }

    @Test
    fun refreshWhenPageCountShrinks_stopsAtNewBoundAndDropsOldTail() {
        val pageOneCalls = AtomicInteger()
        val pageTwoCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1)), current = 1, total = 2))
            } else {
                Result.success(page(listOf(movie(3)), current = 1, total = 1))
            }
        }
        coEvery { api.getHistoryData(2) } coAnswers {
            pageTwoCalls.incrementAndGet()
            Result.success(page(listOf(movie(2)), current = 2, total = 2))
        }
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm)
        vm.onAction(CommonAction.LoadMore)
        awaitContent(vm) { it.itemIds() == listOf(1, 2) }

        vm.onAction(CommonAction.Refresh)

        val refreshed = awaitContent(vm) {
            !it.isRefreshing && it.itemIds() == listOf(3)
        }
        assertFalse(refreshed.hasMorePages)
        assertEquals(2, pageOneCalls.get())
        assertEquals(1, pageTwoCalls.get())
    }

    @Test
    fun nextPageMismatchKeepsCurrentWindowRetryableInsteadOfSkippingPages() {
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(listOf(movie(1)), current = 1, total = 3),
        )
        coEvery { api.getHistoryData(2) } returns Result.success(
            page(listOf(movie(3)), current = 3, total = 3),
        )
        val vm = createVM().also(HistoryVM::testOnStart)
        val firstPage = awaitContent(vm)

        vm.onAction(CommonAction.LoadMore)

        val failed = awaitContent(vm) {
            !it.isLoadingMore &&
                it.itemIds() == listOf(1) &&
                it.nextPageErrorMessage ==
                    "History pagination did not match the requested next page"
        }
        assertEquals(firstPage.focusKey, failed.focusKey)
        assertTrue(failed.hasMorePages)
        coVerify(exactly = 1) { api.getHistoryData(2) }
    }

    @Test
    fun contextMenuStartOver_routesExplicitPlayerStartMode() {
        coEvery { api.getHistoryData(1) } returns Result.success(page(listOf(movie(1))))
        val vm = createVM().also(HistoryVM::testOnStart)
        val item = awaitContent(vm).items.single()
        val playerScreen = mockk<PuberScreen>()
        every {
            screens.player(
                itemId = item.itemId,
                seasonNumber = null,
                episodeNumber = null,
                videoNumber = item.videoNumber,
                startMode = PlayerStartMode.StartFromBeginning,
            )
        } returns playerScreen

        vm.onAction(HistoryAction.Play(item, PlayerStartMode.StartFromBeginning))

        verify { router.navigateTo(playerScreen) }
        verify { itemDetailsRepository.invalidate(item.itemId) }
    }

    private fun createVM(): HistoryVM {
        return HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = HistoryInteractor(api, itemDetailsRepository),
            mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
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
