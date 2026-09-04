package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsBookmarkState
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.domain.interactor.details.MovieBookmarkUpdate
import com.kino.puber.domain.interactor.details.MovieWatchedUpdate
import com.kino.puber.domain.interactor.details.WatchedUpdate
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val SIMILAR_ITEM_ID = 100

class DetailsVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var interactor: DetailsInteractor
    private lateinit var episodeScheduleInteractor: EpisodeScheduleInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private val bookmarkPreferences = BookmarkPreferencesRepository()
    private lateinit var errorHandler: ErrorHandler

    private val params = DetailsScreenParams(itemId = 42)

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true) {
            every { screens } returns this@DetailsVMTest.screens
        }
        mapper = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        episodeScheduleInteractor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } returns Unit
        }

        coEvery { interactor.getItemDetails(42) } returns testItem
        coEvery { interactor.refreshItemDetails(42) } returns refreshedItem
        coEvery { interactor.getBookmarkState(any(), any()) } returns bookmarkState()
        coEvery { interactor.getSimilarItems(42) } returns listOf(similarItem)
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns listOf(videoItem(id = SIMILAR_ITEM_ID))
    }

    @Test
    fun playClicked_navigatesForContentChangeResultToPlayer() {
        val playerScreen = mockk<PuberScreen>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.PlayClicked)

        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun episodeSelected_navigatesForContentChangeResultToEpisodePlayer() {
        val playerScreen = mockk<PuberScreen>()
        every { screens.player(42, 1, 2) } returns playerScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.EpisodeSelected(videoItem(id = 101)))

        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun commonItemPlayed_navigatesForContentChangeResultToItemPlayer() {
        val playerScreen = mockk<PuberScreen>()
        every { screens.player(100, null, null) } returns playerScreen
        val vm = startedVM()

        vm.onAction(CommonAction.ItemPlayed(videoItem(id = 100)))

        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun similarSelected_navigatesForContentChangeResultToDetails() {
        val detailsScreen = mockk<PuberScreen>()
        every { screens.details(100) } returns detailsScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.SimilarSelected(videoItem(id = 100)))

        verify {
            router.navigateForResult<ContentChangeSet>(detailsScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun commonItemSelected_navigatesForContentChangeResultToDetails() {
        val detailsScreen = mockk<PuberScreen>()
        every { screens.details(100) } returns detailsScreen
        val vm = startedVM()

        vm.onAction(CommonAction.ItemSelected(videoItem(id = 100)))

        verify {
            router.navigateForResult<ContentChangeSet>(detailsScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun historyEpisodeParamsOpenPanelWithExactEpisodeFocusTarget() {
        val target = DetailsEpisodeTarget(seasonNumber = 1, episodeNumber = 2)
        val mapped = content().copy(
            currentEpisode = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
            initialEpisodeFocusId = 101,
        )
        every {
            mapper.map(
                item = testItem,
                isInWatchlist = false,
                initialEpisode = target,
            )
        } returns mapped
        val vm = startedVM(
            DetailsScreenParams(
                itemId = 42,
                initialEpisode = target,
            ),
        )

        val content = vm.testStateValue as DetailsScreenState.Content
        assertTrue(content.seasonsPanelVisible)
        assertEquals(101, content.currentEpisode?.id)
        assertEquals(101, content.initialEpisodeFocusId)
    }

    @Test
    fun returnedChangesForCurrentItem_forceRefreshDetails() {
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, capture(listener))
        }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        coVerify(exactly = 1) { interactor.refreshItemDetails(42) }
    }

    @Test
    fun returnedChangesForCurrentItem_areReturnedWhenDetailsCloses() {
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, capture(listener))
        }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun returnedChangesForVisibleSimilarItem_reloadSimilarItemsOnly() {
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, capture(listener))
        }

        listener.captured(ContentChangeSet.single(100, ContentChangeType.Bookmark))

        coVerify(exactly = 0) { interactor.refreshItemDetails(any()) }
        coVerify(exactly = 2) { interactor.getSimilarItems(42) }
    }

    @Test
    fun backPressed_withoutContentChanges_consumesResultListenerWithEmptyChanges() {
        val vm = startedVM()

        vm.onBackPressed()

        verifyEmptyContentChangeBack()
    }

    @Test
    fun watchlistToggle_success_returnsWatchlistChange() {
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = true)
        } returns Result.success(true)
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watchlist)
    }

    @Test
    fun movieBookmarkToggle_success_returnsBookmarkChange() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieBookmarked(42, bookmarked = true) } returns MovieBookmarkUpdate(
            isBookmarked = true,
            folderTitle = "Watch later",
        )
        val vm = startedVM()

        vm.onAction(DetailsAction.BookmarkToggleClicked)
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieWatchedToggle_success_returnsWatchedChange() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieWatched(42, watched = true) } returns MovieWatchedUpdate(
            isWatched = true,
        )
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun episodeWatchedChanged_success_returnsWatchedChange() {
        coEvery { interactor.setEpisodeWatched(42, 1, 2, true) } returns WatchedUpdate(isWatched = true)
        val vm = startedVM()

        vm.onAction(
            DetailsAction.EpisodeWatchedChanged(
                item = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
                watched = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun seasonWatchedChanged_success_returnsWatchedChange() {
        coEvery { interactor.setSeasonWatched(42, 1, true) } returns WatchedUpdate(isWatched = true)
        val vm = startedVM()

        vm.onAction(
            DetailsAction.SeasonWatchedChanged(
                item = videoItem(id = 1, seasonNumber = 1),
                watched = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun similarItemSavedChanged_success_returnsBookmarkChangeForSimilarItem() {
        coEvery { savedItemInteractor.setSaved(100, isSeriesLike = false, saved = true) } returns Result.success(true)
        val vm = startedVM()

        vm.onAction(
            CommonAction.ItemSavedChanged(
                item = videoItem(id = 100),
                isSaved = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 100, ContentChangeType.Bookmark)
    }

    @Test
    fun similarSeriesSavedChanged_returnsWatchlistChangeForSimilarItem() {
        coEvery { savedItemInteractor.setSaved(100, isSeriesLike = true, saved = true) } returns Result.success(true)
        val vm = startedVM()

        vm.onAction(
            CommonAction.ItemSavedChanged(
                item = videoItem(id = 100, isSeriesLike = true),
                isSaved = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 100, ContentChangeType.Watchlist)
    }

    @Test
    fun backWaitsForPendingMutationAndReturnsItsChange() {
        val releaseMutation = CompletableDeferred<Unit>()
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieWatched(42, watched = true) } coAnswers {
            releaseMutation.await()
            MovieWatchedUpdate(isWatched = true)
        }
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()

        verify(exactly = 0) { router.back(any(), any()) }
        releaseMutation.complete(Unit)
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun failedMutationThenBackReturnsEmptyChanges() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieWatched(42, watched = true) } throws IllegalStateException("failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()

        verifyEmptyContentChangeBack()
    }

    private fun startedVM(
        params: DetailsScreenParams = this.params,
    ): DetailsVM = createVM(params).also { it.testOnStart() }

    @Test
    fun bookmarkPickerResult_marksTheSimilarItemBookmarkedAndReportsTheChangeOnBack() {
        val screen = mockk<PuberScreen>()
        every { screens.bookmarkPicker(any(), any()) } returns screen
        val vm = startedVM()

        vm.onAction(CommonAction.ItemBookmarksRequested(videoItem(SIMILAR_ITEM_ID)))
        val listener = slot<(BookmarkPickerResult?) -> Unit>()
        verify { router.navigateForResult<BookmarkPickerResult>(screen, any(), capture(listener)) }
        listener.captured(BookmarkPickerResult(itemId = SIMILAR_ITEM_ID, selectedFolderIds = listOf(3)))

        val state = vm.testStateValue as DetailsScreenState.Content
        assertTrue(state.similarItems.single().isBookmarked)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = SIMILAR_ITEM_ID, ContentChangeType.Bookmark)
    }

    @Test
    fun itemBookmarksRequested_opensTheBookmarkPickerForThatItem() {
        val screen = mockk<PuberScreen>()
        every {
            screens.bookmarkPicker(itemId = 42, resultCode = any())
        } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemBookmarksRequested(videoItem(42)))

        verify { router.navigateForResult<BookmarkPickerResult>(screen, any(), any()) }
    }

    private fun createVM(
        params: DetailsScreenParams = this.params,
    ) = DetailsVM(
        router = router,
        params = params,
        mapper = mapper,
        interactor = interactor,
        episodeScheduleInteractor = episodeScheduleInteractor,
        savedItemInteractor = savedItemInteractor,
        bookmarkPreferencesRepository = bookmarkPreferences,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    )

    private fun content(
        similarItems: List<VideoItemUIState> = emptyList(),
        isInWatchlist: Boolean = false,
        isBookmarked: Boolean = false,
        isWatched: Boolean = false,
    ): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = VideoDetailsUIState.Loading,
            info = DetailsInfoUIState(
                description = "",
                ratings = emptyList(),
                primaryRows = emptyList(),
                secondaryRows = emptyList(),
                castCards = emptyList(),
            ),
            buttons = emptyList(),
            isInWatchlist = isInWatchlist,
            isBookmarked = isBookmarked,
            isWatched = isWatched,
            similarItems = similarItems,
        )
    }

    private fun videoItem(
        id: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        isSeriesLike: Boolean = false,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = "Item $id",
            imageUrl = "",
            bigImageUrl = "",
            isSeriesLike = isSeriesLike,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }

    private fun verifyContentChangeBack(itemId: Int, vararg expectedTypes: ContentChangeType) {
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    val changes = result as? ContentChangeSet ?: return@match false
                    changes.changes[itemId] == expectedTypes.toSet()
                },
            )
        }
    }

    private fun verifyEmptyContentChangeBack() {
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    (result as? ContentChangeSet)?.isEmpty == true
                },
            )
        }
    }

    private fun movieItem(): Item {
        return Item(
            id = 42,
            title = "Movie",
            type = ItemType.MOVIE,
        )
    }

    private val testItem = Item(
        id = 42,
        title = "Series",
        type = ItemType.SERIAL,
        seasons = listOf(
            Season(
                id = 1,
                number = 1,
                episodes = listOf(Episode(id = 101, number = 2, title = "Episode 2")),
            )
        ),
    )

    private val refreshedItem = testItem.copy(title = "Series refreshed")

    private val similarItem = Item(
        id = 100,
        title = "Similar",
        type = ItemType.MOVIE,
    )
}

private fun bookmarkState(
    isInWatchLaterFolder: Boolean = false,
    isBookmarked: Boolean = false,
) = DetailsBookmarkState(
    isInWatchLaterFolder = isInWatchLaterFolder,
    isBookmarked = isBookmarked,
)
