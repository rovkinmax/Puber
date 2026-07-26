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
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

class HistoryVMPlaybackTest {

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
    private lateinit var vm: HistoryVM

    @BeforeEach
    fun setUp() {
        api = mockk()
        itemDetailsRepository = mockk(relaxed = true)
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        val errorHandler = mockk<ErrorHandler>(relaxed = true)
        every { router.screens } returns screens
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
    fun selection_opensMovieAndExactEpisodeDetailsWithoutStartingPlayback() {
        startWith(
            movie(itemId = 10, videoNumber = 2),
            episode(itemId = 20, season = 3, episode = 4),
            episode(itemId = 30, season = null, episode = 5),
        )
        val movieDetails = mockk<PuberScreen>()
        val episodeDetails = mockk<PuberScreen>()
        val fallbackDetails = mockk<PuberScreen>()
        every { screens.details(10) } returns movieDetails
        every {
            screens.details(
                itemId = 20,
                initialEpisode = DetailsEpisodeTarget(
                    seasonNumber = 3,
                    episodeNumber = 4,
                ),
            )
        } returns episodeDetails
        every { screens.details(30) } returns fallbackDetails

        awaitContent().items.forEach { vm.onAction(CommonAction.ItemSelected(it)) }

        verify(exactly = 0) { screens.player(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { itemDetailsRepository.invalidate(any()) }
        verify { router.navigateTo(movieDetails) }
        verify { router.navigateTo(episodeDetails) }
        verify { router.navigateTo(fallbackDetails) }
    }

    @Test
    fun contextMenu_openAndDismissPreserveSourceFocus() {
        startWith(movie(itemId = 1, videoNumber = 1))
        val item = awaitContent().items.single()

        vm.onAction(HistoryAction.OpenContextMenu(item))
        val open = awaitContent { it.openMenuKey == item.rowKey }
        assertEquals(item.rowKey, open.focusKey)

        vm.onAction(HistoryAction.DismissContextMenu)
        val dismissed = awaitContent { it.openMenuKey == null }
        assertEquals(item.rowKey, dismissed.focusKey)
    }

    @Test
    fun play_opensExactMovieVideoAfterInvalidatingItemDetails() {
        startWith(movie(itemId = 10, videoNumber = 7))
        val item = awaitContent().items.single()
        val player = mockk<PuberScreen>()
        every {
            screens.player(
                itemId = 10,
                seasonNumber = null,
                episodeNumber = null,
                videoNumber = 7,
                startMode = PlayerStartMode.StartFromBeginning,
            )
        } returns player

        vm.onAction(HistoryAction.Play(item, PlayerStartMode.StartFromBeginning))

        verifyOrder {
            itemDetailsRepository.invalidate(10)
            screens.player(
                itemId = 10,
                seasonNumber = null,
                episodeNumber = null,
                videoNumber = 7,
                startMode = PlayerStartMode.StartFromBeginning,
            )
            router.navigateTo(player)
        }
        verify(exactly = 0) { screens.details(any()) }
    }

    @Test
    fun play_opensExactSeriesEpisodeAfterInvalidatingItemDetails() {
        startWith(episode(itemId = 20, season = 8, episode = 4))
        val item = awaitContent().items.single()
        val player = mockk<PuberScreen>()
        every {
            screens.player(
                itemId = 20,
                seasonNumber = 8,
                episodeNumber = 4,
                videoNumber = null,
                startMode = PlayerStartMode.ResumeIfAvailable,
            )
        } returns player

        vm.onAction(HistoryAction.Play(item, PlayerStartMode.ResumeIfAvailable))

        verifyOrder {
            itemDetailsRepository.invalidate(20)
            screens.player(
                itemId = 20,
                seasonNumber = 8,
                episodeNumber = 4,
                videoNumber = null,
                startMode = PlayerStartMode.ResumeIfAvailable,
            )
            router.navigateTo(player)
        }
        verify(exactly = 0) { screens.details(any()) }
    }

    private fun startWith(vararg items: History) {
        coEvery { api.getHistoryData(1) } returns Result.success(page(items.toList()))
        vm.testOnStart()
    }

    private fun awaitContent(
        predicate: (HistoryViewState.Content) -> Boolean = { true },
    ): HistoryViewState.Content {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testStateValue
            if (state is HistoryViewState.Content && predicate(state)) return state
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for History content; last=${vm.testStateValue}")
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

    private fun movie(
        itemId: Int,
        videoNumber: Int,
    ): History {
        return History(
            item = Item(id = itemId, title = "Synthetic movie $itemId", type = ItemType.MOVIE),
            video = Video(
                id = itemId * 100,
                number = videoNumber,
                watching = WatchingInfo(time = 120, duration = 600),
            ),
        )
    }

    private fun episode(
        itemId: Int,
        season: Int?,
        episode: Int,
    ): History {
        return History(
            item = Item(id = itemId, title = "Synthetic series $itemId", type = ItemType.SERIAL),
            video = Video(id = itemId * 100, number = episode),
            season = season,
        )
    }
}
