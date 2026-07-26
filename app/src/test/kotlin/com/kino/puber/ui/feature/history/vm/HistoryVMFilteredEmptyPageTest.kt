package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger

class HistoryVMFilteredEmptyPageTest {

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
    private lateinit var router: AppRouter
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setUp() {
        api = mockk()
        itemDetailsRepository = mockk(relaxed = true)
        interactor = HistoryInteractor(api, itemDetailsRepository)
        mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider()))
        router = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
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
    fun initialLoad_continuesPastFilteredEmptyPageUntilRenderableRow() {
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(emptyList(), current = 1, total = 2),
        )
        coEvery { api.getHistoryData(2) } returns Result.success(
            page(listOf(movie(2)), current = 2, total = 2),
        )

        val content = awaitContent(createVM().also(HistoryVM::testOnStart))

        assertEquals(listOf(2), content.items.map { it.itemId })
        assertFalse(content.hasMorePages)
        coVerify(exactly = 1) { api.getHistoryData(1) }
        coVerify(exactly = 1) { api.getHistoryData(2) }
    }

    @Test
    fun deletionReconciliation_continuesPastFilteredEmptyPageUntilRenderableRow() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(listOf(movie(1)), current = 1, total = 2))
            } else {
                Result.success(page(emptyList(), current = 1, total = 2))
            }
        }
        coEvery { api.getHistoryData(2) } returns Result.success(
            page(listOf(movie(2)), current = 2, total = 2),
        )
        coEvery { api.clearExactMediaHistory(any()) } returns Result.success(Unit)
        val vm = createVM().also(HistoryVM::testOnStart)
        val deleted = awaitContent(vm).items.single()

        vm.onAction(HistoryAction.DeleteExactMedia(deleted))

        val reconciled = awaitContent(vm) { content ->
            content.items.map { it.itemId } == listOf(2)
        }
        assertEquals(listOf(2), reconciled.items.map { it.itemId })
        assertFalse(reconciled.hasMorePages)
        assertEquals(2, pageOneCalls.get())
        coVerify(exactly = 1) { api.getHistoryData(2) }
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
        fail("Timed out waiting for History content; last=${vm.testStateValue}")
    }

    private fun page(
        items: List<History>,
        current: Int,
        total: Int,
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
            ),
        )
    }
}
