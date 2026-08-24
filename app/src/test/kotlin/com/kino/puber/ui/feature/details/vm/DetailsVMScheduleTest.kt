package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.domain.interactor.details.WatchedUpdate
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.domain.model.ScheduleProvider
import com.kino.puber.domain.model.ScheduledEpisode
import com.kino.puber.domain.model.ScheduledSeason
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class DetailsVMScheduleTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val ITEM_ID = 42
        private const val SIMILAR_ITEM_ID = 100
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var interactor: DetailsInteractor
    private lateinit var episodeScheduleInteractor: EpisodeScheduleInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true) {
            every { screens } returns this@DetailsVMScheduleTest.screens
        }
        mapper = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        episodeScheduleInteractor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } returns Unit
        }

        coEvery { interactor.getItemDetails(ITEM_ID) } returns testItem
        coEvery { interactor.refreshItemDetails(ITEM_ID) } returns refreshedItem
        coEvery { interactor.isInWatchLaterFolder(any()) } returns false
        coEvery { interactor.getSimilarItems(ITEM_ID) } returns listOf(similarItem)
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns listOf(videoItem(id = SIMILAR_ITEM_ID))
    }

    @Test
    fun scheduleClicked_pushesParameterizedScheduleScreen_withoutResultNavigation() {
        val scheduleItem = testItem.copy(
            title = "Series title",
            imdb = "  tt123  ",
        )
        val scheduleScreen = mockk<PuberScreen>()
        coEvery { interactor.getItemDetails(ITEM_ID) } returns scheduleItem
        coEvery { episodeScheduleInteractor.getSchedule("tt123") } returns
            EpisodeScheduleResult.NoUpcomingReleases
        val scheduleParams = slot<EpisodeScheduleScreenParams>()
        every { screens.episodeSchedule(capture(scheduleParams)) } returns scheduleScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.ScheduleClicked)

        assertEquals(
            EpisodeScheduleScreenParams(
                itemId = ITEM_ID,
                title = "Series title",
                imdbId = "tt123",
            ),
            scheduleParams.captured,
        )
        verify(exactly = 1) { router.navigateTo(scheduleScreen) }
        verify(exactly = 0) {
            router.navigateForResult<ContentChangeSet>(any(), any(), any())
        }
    }

    @Test
    fun scheduleClicked_doesNotNavigateWhenImdbIsMissing() {
        val vm = startedVM()

        vm.onAction(DetailsAction.ScheduleClicked)

        verify(exactly = 0) { router.navigateTo(any()) }
        verify(exactly = 0) { screens.episodeSchedule(any()) }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["   "])
    fun scheduleLoad_missingImdbSkipsInteractorAndKeepsContent(imdbId: String?) {
        val item = testItem.copy(imdb = imdbId)
        coEvery { interactor.getItemDetails(ITEM_ID) } returns item
        every { mapper.map(item, false) } returns content()

        val vm = startedVM()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
        verify(exactly = 1) { mapper.map(item, false) }
        coVerify(exactly = 0) { episodeScheduleInteractor.getSchedule(any()) }
    }

    @Test
    fun scheduleLoad_publishesPrimaryContentBeforeScheduleCompletes() {
        val item = testItem.copy(imdb = "tt123")
        val pendingSchedule = CompletableDeferred<EpisodeScheduleResult>()
        val primaryContent = content()
        val scheduledContent = content(isWatched = true)
        coEvery { interactor.getItemDetails(ITEM_ID) } returns item
        coEvery { episodeScheduleInteractor.getSchedule("tt123") } coAnswers {
            pendingSchedule.await()
        }
        every { mapper.map(item, false) } returns primaryContent
        every { mapper.map(item, false, episodeSchedule) } returns scheduledContent

        val vm = startedVM()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
        assertFalse((vm.testStateValue as DetailsScreenState.Content).isWatched)
        verify(exactly = 1) { mapper.map(item, false) }
        verify(exactly = 0) {
            mapper.map(
                item = any(),
                isInWatchlist = any(),
                schedule = any(),
            )
        }

        pendingSchedule.complete(EpisodeScheduleResult.Available(episodeSchedule))

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isWatched)
        verify(exactly = 1) { mapper.map(item, false, episodeSchedule) }
    }

    @Test
    fun scheduleLoad_olderCompletionCannotOverwriteNewerItemSchedule() {
        val olderItem = testItem.copy(title = "Older item", imdb = "tt-older")
        val newerItem = testItem.copy(title = "Newer item", imdb = "tt-newer")
        val olderSchedule = episodeSchedule.copy(
            seasons = episodeSchedule.seasons.map { season ->
                season.copy(
                    episodes = season.episodes.map { episode ->
                        episode.copy(title = "Older schedule")
                    },
                )
            },
        )
        val newerSchedule = episodeSchedule.copy(
            seasons = episodeSchedule.seasons.map { season ->
                season.copy(
                    episodes = season.episodes.map { episode ->
                        episode.copy(title = "Newer schedule")
                    },
                )
            },
        )
        val olderResult = CompletableDeferred<EpisodeScheduleResult>()
        val newerResult = CompletableDeferred<EpisodeScheduleResult>()
        lateinit var olderRequestJob: Job
        coEvery { interactor.getItemDetails(ITEM_ID) } returns olderItem andThen newerItem
        coEvery { episodeScheduleInteractor.getSchedule("tt-older") } coAnswers {
            olderRequestJob = currentCoroutineContext()[Job]!!
            try {
                olderResult.await()
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    olderResult.await()
                }
            }
        }
        coEvery { episodeScheduleInteractor.getSchedule("tt-newer") } coAnswers {
            newerResult.await()
        }
        every { mapper.map(olderItem, false) } returns content()
        every { mapper.map(newerItem, false) } returns content()
        every { mapper.map(newerItem, false, olderSchedule) } returns
            content(isInWatchlist = true)
        every { mapper.map(newerItem, false, newerSchedule) } returns
            content(isWatched = true)
        val vm = startedVM()

        vm.onAction(CommonAction.RetryClicked)
        newerResult.complete(EpisodeScheduleResult.Available(newerSchedule))

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isWatched)
        olderResult.complete(EpisodeScheduleResult.Available(olderSchedule))

        val finalState = vm.testStateValue as DetailsScreenState.Content
        assertTrue(finalState.isWatched)
        assertFalse(finalState.isInWatchlist)
        assertTrue(olderRequestJob.isCancelled)
        verify(exactly = 1) { mapper.map(newerItem, false, newerSchedule) }
        verify(exactly = 0) { mapper.map(newerItem, false, olderSchedule) }
    }

    @Test
    fun scheduleLoad_identityChangeHidesOldScheduleWhenReplacementFails() {
        val originalItem = testItem.copy(title = "Original item", imdb = "tt-original")
        val refreshedItem = testItem.copy(title = "Refreshed item", imdb = "tt-replacement")
        val replacementFailure = CompletableDeferred<Unit>()
        coEvery { interactor.getItemDetails(ITEM_ID) } returns originalItem andThen refreshedItem
        coEvery { episodeScheduleInteractor.getSchedule("tt-original") } returns
            EpisodeScheduleResult.Available(episodeSchedule)
        coEvery { episodeScheduleInteractor.getSchedule("tt-replacement") } coAnswers {
            replacementFailure.await()
            throw IllegalStateException("replacement unavailable")
        }
        every { mapper.map(originalItem, false) } returns content()
        every { mapper.map(originalItem, false, episodeSchedule) } returns
            content(isWatched = true)
        every { mapper.map(refreshedItem, false) } returns content()
        every { mapper.map(refreshedItem, false, episodeSchedule) } returns
            content(isInWatchlist = true)
        val vm = startedVM()

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isWatched)

        vm.onAction(CommonAction.RetryClicked)

        val pendingState = vm.testStateValue as DetailsScreenState.Content
        assertFalse(pendingState.isWatched)
        assertFalse(pendingState.isInWatchlist)
        verify(exactly = 0) { mapper.map(refreshedItem, false, episodeSchedule) }

        replacementFailure.complete(Unit)

        val failedState = vm.testStateValue as DetailsScreenState.Content
        assertFalse(failedState.isWatched)
        assertFalse(failedState.isInWatchlist)
        verify(exactly = 0) { mapper.map(refreshedItem, false, episodeSchedule) }
    }

    @Test
    fun scheduleLoad_thrownFailureKeepsPrimaryContent() {
        val item = testItem.copy(imdb = "tt123")
        val pendingFailure = CompletableDeferred<Unit>()
        coEvery { interactor.getItemDetails(ITEM_ID) } returns item
        coEvery { episodeScheduleInteractor.getSchedule("tt123") } coAnswers {
            pendingFailure.await()
            throw IllegalStateException("schedule unavailable")
        }

        val vm = startedVM()
        val primaryContent = vm.testStateValue

        assertTrue(primaryContent is DetailsScreenState.Content)
        pendingFailure.complete(Unit)

        assertEquals(primaryContent, vm.testStateValue)
        verify(exactly = 0) {
            mapper.map(
                item = any(),
                isInWatchlist = any(),
                schedule = any(),
            )
        }
        verify(exactly = 0) { errorHandler.proceedInvoke(any(), any()) }
    }

    @Test
    fun scheduleLoad_missingCredentialsKeepsPrimaryContent() {
        verifyTypedUnavailableScheduleKeepsPrimaryContent(EpisodeScheduleResult.MissingCredentials)
    }

    @Test
    fun scheduleLoad_noMatchKeepsPrimaryContent() {
        verifyTypedUnavailableScheduleKeepsPrimaryContent(EpisodeScheduleResult.NoMatch)
    }

    @Test
    fun scheduleLoad_noUpcomingReleasesKeepsPrimaryContent() {
        verifyTypedUnavailableScheduleKeepsPrimaryContent(EpisodeScheduleResult.NoUpcomingReleases)
    }

    @Test
    fun scheduleLoad_availableScheduleIsRetainedAfterWatchlistRefresh() {
        val item = testItem.copy(imdb = "tt123")
        val refreshed = item.copy(title = "Series refreshed")
        coEvery { interactor.getItemDetails(ITEM_ID) } returns item
        coEvery { episodeScheduleInteractor.getSchedule("tt123") } returns
            EpisodeScheduleResult.Available(episodeSchedule)
        coEvery {
            savedItemInteractor.setSaved(ITEM_ID, isSeriesLike = true, saved = true)
        } returns Result.success(true)
        coEvery { interactor.refreshItemDetails(ITEM_ID) } returns refreshed
        every { mapper.map(item, false, episodeSchedule) } returns content()
        every { mapper.map(refreshed, true, episodeSchedule) } returns content(isInWatchlist = true)
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        verify(exactly = 1) {
            mapper.map(
                item = refreshed,
                isInWatchlist = true,
                schedule = episodeSchedule,
            )
        }
        assertTrue((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun scheduleLoad_availableScheduleIsRetainedAfterWatchedRemapAndRefresh() {
        val item = testItem.copy(imdb = "tt123")
        val refreshed = item.copy(title = "Series refreshed")
        coEvery { interactor.getItemDetails(ITEM_ID) } returns item
        coEvery { episodeScheduleInteractor.getSchedule("tt123") } returns
            EpisodeScheduleResult.Available(episodeSchedule)
        coEvery { interactor.setEpisodeWatched(ITEM_ID, 1, 2, true) } returns
            WatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(ITEM_ID) } returns refreshed
        every { mapper.map(item, false, episodeSchedule) } returns content()
        every { mapper.map(refreshed, false, episodeSchedule) } returns content()
        val vm = startedVM()

        vm.onAction(
            DetailsAction.EpisodeWatchedChanged(
                item = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
                watched = true,
            )
        )

        verify(exactly = 1) {
            mapper.map(
                item = match { mappedItem ->
                    mappedItem.seasons?.first()?.episodes?.first()?.watched == 1
                },
                isInWatchlist = false,
                schedule = episodeSchedule,
            )
        }
        verify(exactly = 1) {
            mapper.map(
                item = refreshed,
                isInWatchlist = false,
                schedule = episodeSchedule,
            )
        }
        assertTrue(vm.testStateValue is DetailsScreenState.Content)
    }

    private fun startedVM(): DetailsVM {
        return DetailsVM(
            router = router,
            params = DetailsScreenParams(itemId = ITEM_ID),
            mapper = mapper,
            interactor = interactor,
            episodeScheduleInteractor = episodeScheduleInteractor,
            savedItemInteractor = savedItemInteractor,
            resources = FakeResourceProvider(),
            errorHandler = errorHandler,
        ).also { it.testOnStart() }
    }

    private fun verifyTypedUnavailableScheduleKeepsPrimaryContent(
        result: EpisodeScheduleResult,
    ) {
        val item = testItem.copy(imdb = "tt123")
        val pendingResult = CompletableDeferred<EpisodeScheduleResult>()
        coEvery { interactor.getItemDetails(ITEM_ID) } returns item
        coEvery { episodeScheduleInteractor.getSchedule("tt123") } coAnswers {
            pendingResult.await()
        }

        val vm = startedVM()
        val primaryContent = vm.testStateValue

        assertTrue(primaryContent is DetailsScreenState.Content)
        pendingResult.complete(result)

        assertEquals(primaryContent, vm.testStateValue)
        verify(exactly = 0) {
            mapper.map(
                item = any(),
                isInWatchlist = any(),
                schedule = any(),
            )
        }
    }

    private fun content(
        isInWatchlist: Boolean = false,
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
            isWatched = isWatched,
            similarItems = emptyList(),
        )
    }

    private fun videoItem(
        id: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = "Item $id",
            imageUrl = "",
            bigImageUrl = "",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }

    private val testItem = Item(
        id = ITEM_ID,
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

    private val episodeSchedule = EpisodeSchedule(
        provider = ScheduleProvider.TMDB,
        seasons = listOf(
            ScheduledSeason(
                seasonNumber = 2,
                episodes = listOf(
                    ScheduledEpisode(
                        seasonNumber = 2,
                        episodeNumber = 1,
                        title = "Future episode",
                        airDate = LocalDate(2026, 9, 1),
                    )
                ),
            )
        ),
    )

    private val similarItem = Item(
        id = SIMILAR_ITEM_ID,
        title = "Similar",
        type = ItemType.MOVIE,
    )
}
