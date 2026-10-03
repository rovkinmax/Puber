package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
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
import com.kino.puber.ui.feature.details.model.DetailsAction
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
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val SIMILAR_ITEM_ID = 100

/**
 * Writes that succeed while the follow-up refresh fails, and the ordering of overlapping
 * mutations. Split out of [DetailsVMTest] to keep either class readable.
 */
class DetailsVMMutationTest {

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
            every { screens } returns this@DetailsVMMutationTest.screens
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
    fun watchlistWriteSuccess_refreshFailure_keepsConfirmedStateAndReturnsChange() {
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = true)
        } returns Result.success(true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watchlist)
    }

    @Test
    fun rapidSeriesWatchlistChanges_applyLatestDesiredStateAfterFirstFailure() {
        val releaseFirst = CompletableDeferred<Unit>()
        val failure = IllegalStateException("failed")
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = true)
        } coAnswers {
            releaseFirst.await()
            Result.failure(failure)
        }
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = false)
        } returns Result.success(false)
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        vm.onAction(DetailsAction.WatchlistToggleClicked)

        coVerify(exactly = 0) {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = false)
        }
        releaseFirst.complete(Unit)

        coVerify(exactly = 1) {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = false)
        }
        assertFalse((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun movieBookmarkAddWriteSuccess_refreshFailure_keepsRequestedStateAndReturnsChange() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieBookmarked(42, bookmarked = true) } returns MovieBookmarkUpdate(
            isBookmarked = true,
            folderTitle = "Watch later",
        )
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.BookmarkToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isBookmarked)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieBookmarkRemoveWriteSuccess_refreshFailure_keepsRequestedStateAndReturnsChange() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        every { mapper.map(movie, any()) } returns content(isBookmarked = true)
        coEvery {
            interactor.getBookmarkState(movie, BookmarkMode.Simple)
        } returns bookmarkState(isBookmarked = true)
        coEvery { interactor.setMovieBookmarked(42, bookmarked = false) } returns MovieBookmarkUpdate(
            isBookmarked = false,
            folderTitle = "Watch later",
        )
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.BookmarkToggleClicked)

        assertFalse((vm.testStateValue as DetailsScreenState.Content).isBookmarked)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieBookmarkWriteSuccess_membershipReadFailure_keepsRequestedStateAndReturnsChange() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieBookmarked(42, bookmarked = true) } returns MovieBookmarkUpdate(
            isBookmarked = true,
            folderTitle = "Watch later",
        )
        val vm = startedVM()
        coEvery { interactor.refreshItemDetails(42) } returns movie
        coEvery {
            interactor.getBookmarkState(movie, any())
        } throws IllegalStateException("read failed")

        vm.onAction(DetailsAction.BookmarkToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isBookmarked)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieBookmarkRemove_successfulRefreshKeepsQuickFolderResult() {
        val movie = movieItem()
        val refreshed = movie.copy(title = "Refreshed")
        coEvery { interactor.getItemDetails(42) } returns movie
        every { mapper.map(movie, any()) } returns content(isBookmarked = true)
        coEvery {
            interactor.getBookmarkState(movie, BookmarkMode.Simple)
        } returns bookmarkState(isBookmarked = true)
        coEvery { interactor.setMovieBookmarked(42, bookmarked = false) } returns MovieBookmarkUpdate(
            isBookmarked = false,
            folderTitle = "Watch later",
        )
        coEvery { interactor.refreshItemDetails(42) } returns refreshed
        coEvery {
            interactor.getBookmarkState(refreshed, any())
        } returns bookmarkState(isInWatchLaterFolder = true)
        every { mapper.map(refreshed, true) } returns content(isInWatchlist = true)
        val vm = startedVM()

        vm.onAction(DetailsAction.BookmarkToggleClicked)

        assertFalse((vm.testStateValue as DetailsScreenState.Content).isBookmarked)
        verify { mapper.map(refreshed, true) }
    }

    @Test
    fun movieWatchedWriteSuccess_refreshFailure_keepsConfirmedStateAndReturnsChange() {
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieWatched(42, watched = true) } returns MovieWatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isWatched)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun episodeWatchedWriteSuccess_refreshFailure_appliesRequestedStateAndReturnsChange() {
        coEvery { interactor.setEpisodeWatched(42, 1, 2, true) } returns WatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(
            DetailsAction.EpisodeWatchedChanged(
                item = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
                watched = true,
            )
        )

        verify {
            mapper.map(
                match { item -> item.seasons?.first()?.episodes?.first()?.watched == 1 },
                any(),
            )
        }
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun seasonWatchedWriteSuccess_refreshFailure_appliesRequestedStateAndReturnsChange() {
        coEvery { interactor.setSeasonWatched(42, 1, true) } returns WatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(
            DetailsAction.SeasonWatchedChanged(
                item = videoItem(id = 1, seasonNumber = 1),
                watched = true,
            )
        )

        verify {
            mapper.map(
                match { item -> item.seasons?.first()?.episodes.orEmpty().all { it.watched == 1 } },
                any(),
            )
        }
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun rapidMovieWatchedMutations_areSerializedAndAppliedInActionOrder() {
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val movie = movieItem()
        coEvery { interactor.getItemDetails(42) } returns movie
        coEvery { interactor.setMovieWatched(42, watched = true) } coAnswers {
            order += "start-true"
            releaseFirst.await()
            order += "finish-true"
            MovieWatchedUpdate(isWatched = true)
        }
        coEvery { interactor.setMovieWatched(42, watched = false) } coAnswers {
            order += "false"
            MovieWatchedUpdate(isWatched = false)
        }
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onAction(DetailsAction.WatchedToggleClicked)

        assertEquals(listOf("start-true"), order)
        coVerify(exactly = 0) { interactor.setMovieWatched(42, watched = false) }

        releaseFirst.complete(Unit)

        assertEquals(listOf("start-true", "finish-true", "false"), order)
        assertFalse((vm.testStateValue as DetailsScreenState.Content).isWatched)
    }

    @Test
    fun repeatedBackWhileMutationPending_isConsumedAndSendsExactlyOneResult() {
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
        vm.onBackPressed()
        vm.onBackPressed()

        verify(exactly = 0) { router.back(any(), any()) }
        verify(exactly = 3) { router.addBackDispatcher(vm) }

        releaseMutation.complete(Unit)

        verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    private fun startedVM(
        params: DetailsScreenParams = this.params,
    ): DetailsVM = createVM(params).also { it.testOnStart() }

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
