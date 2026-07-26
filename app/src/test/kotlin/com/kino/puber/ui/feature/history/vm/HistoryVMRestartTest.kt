package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

internal class HistoryVMRestartTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
    }

    @Test
    fun doubleRetryAcceptsOneCompleteOrderedRestart() {
        val api = mockk<KinoPubApiClient>()
        val pageOneCalls = AtomicInteger()
        val retryEntered = CountDownLatch(1)
        val releaseRetry = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            when (pageOneCalls.incrementAndGet()) {
                1 -> Result.failure(IOException("initial failure"))
                2 -> {
                    retryEntered.countDown()
                    check(releaseRetry.await(2, TimeUnit.SECONDS))
                    Result.success(page(listOf(movie(1), movie(2))))
                }
                else -> Result.success(page(emptyList()))
            }
        }
        val errorHandler = mockk<ErrorHandler>(relaxed = true)
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
        val vm = HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = HistoryInteractor(
                api = api,
                itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true),
            ),
            mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
            router = mockk<AppRouter>(relaxed = true),
            errorHandler = errorHandler,
        ).also(HistoryVM::testOnStart)
        awaitState(vm) { it is HistoryViewState.Error }

        vm.onAction(CommonAction.RetryClicked)
        assertTrue(retryEntered.await(2, TimeUnit.SECONDS))
        vm.onAction(CommonAction.RetryClicked)
        Thread.sleep(100)
        releaseRetry.countDown()

        val content = awaitState(vm) {
            it is HistoryViewState.Content &&
                it.items.map(HistoryItemUIState::itemId) == listOf(1, 2)
        } as HistoryViewState.Content
        assertEquals(listOf(1, 2), content.items.map(HistoryItemUIState::itemId))
        assertEquals(2, pageOneCalls.get())
        coVerify(exactly = 2) { api.getHistoryData(1) }
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
            ),
        )
    }
}
