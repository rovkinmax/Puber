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
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

class HistoryVMDeletionInputGuardTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var vm: HistoryVM

    @BeforeEach
    fun setUp() {
        api = mockk()
        router = mockk(relaxed = true)
        screens = mockk(relaxed = true)
        val errorHandler = mockk<ErrorHandler>(relaxed = true)
        every { router.screens } returns screens
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
        vm = HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = spyk(
                HistoryInteractor(
                    api = api,
                    itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true),
                ),
            ),
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
    fun deletionAndReconciliationRejectNavigationAndMenuActionsUntilSettled() {
        val historyCalls = AtomicInteger()
        val mutationEntered = CountDownLatch(1)
        val releaseMutation = CountDownLatch(1)
        val reconciliationEntered = CountDownLatch(1)
        val releaseReconciliation = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            if (historyCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1), movie(2))))
            } else {
                reconciliationEntered.countDown()
                check(releaseReconciliation.await(2, TimeUnit.SECONDS))
                Result.success(page(listOf(movie(2))))
            }
        }
        coEvery { api.clearExactMediaHistory(any()) } coAnswers {
            mutationEntered.countDown()
            check(releaseMutation.await(2, TimeUnit.SECONDS))
            Result.success(Unit)
        }
        val destination = mockk<PuberScreen>()
        every { screens.details(2) } returns destination
        vm.testOnStart()
        val initial = awaitContent()
        val deletingItem = initial.items.first()
        val retainedItem = initial.items.last()

        val deletionThread = thread {
            vm.onAction(HistoryAction.DeleteExactMedia(deletingItem))
        }
        assertTrue(mutationEntered.await(2, TimeUnit.SECONDS))
        dispatchNavigationAndMenuActions(deletingItem, retainedItem)
        assertNavigationAndMenuWereRejected()

        releaseMutation.countDown()
        assertTrue(reconciliationEntered.await(2, TimeUnit.SECONDS))
        dispatchNavigationAndMenuActions(deletingItem, retainedItem)
        assertNavigationAndMenuWereRejected()

        releaseReconciliation.countDown()
        deletionThread.join(2_000)
        val settled = awaitContent {
            it.itemIds() == listOf(2) && !it.isRefreshing && it.deletingKeys.isEmpty()
        }
        vm.onAction(CommonAction.ItemSelected(settled.items.single()))

        verify(exactly = 1) { router.navigateTo(destination) }
        coVerify(exactly = 1) { api.clearExactMediaHistory(deletingItem.deletionMediaId) }
    }

    private fun dispatchNavigationAndMenuActions(
        deletingItem: HistoryItemUIState,
        retainedItem: HistoryItemUIState,
    ) {
        vm.onAction(CommonAction.ItemSelected(retainedItem))
        vm.onAction(HistoryAction.OpenContextMenu(retainedItem))
        vm.onAction(
            HistoryAction.Play(
                item = retainedItem,
                startMode = PlayerStartMode.ResumeIfAvailable,
            ),
        )
        vm.onAction(HistoryAction.OpenDetails(retainedItem))
        vm.onAction(HistoryAction.DeleteExactMedia(deletingItem))
    }

    private fun assertNavigationAndMenuWereRejected() {
        verify(exactly = 0) { router.navigateTo(any()) }
        assertEquals(null, awaitContent().openMenuKey)
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

    private fun HistoryViewState.Content.itemIds(): List<Int> {
        return items.map(HistoryItemUIState::itemId)
    }

    private fun page(items: List<History>): PaginatedResponse<History> {
        return PaginatedResponse(
            items = items,
            pagination = Pagination(
                current = 1,
                perpage = 20,
                total = 1,
                totalItems = items.size,
            ),
        )
    }

    private fun movie(itemId: Int): History {
        return History(
            item = Item(
                id = itemId,
                title = "Synthetic movie $itemId",
                type = ItemType.MOVIE,
            ),
            video = Video(
                id = itemId * 100,
                number = itemId,
                watching = WatchingInfo(time = 120, duration = 600),
            ),
        )
    }
}
