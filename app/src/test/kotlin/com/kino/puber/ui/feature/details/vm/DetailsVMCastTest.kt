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
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsCastMemberUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DetailsVMCastTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var interactor: DetailsInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true) {
            every { screens } returns this@DetailsVMCastTest.screens
        }
        mapper = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } returns Unit
        }

        coEvery { interactor.getItemDetails(42) } returns testItem
        coEvery { interactor.refreshItemDetails(42) } returns testItem
        coEvery { interactor.isInWatchLaterFolder(any()) } returns false
        coEvery { interactor.getSimilarItems(42) } returns emptyList()
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns emptyList()
    }

    @Test
    fun castMemberSelected_navigatesToActorResultsWithOriginalQuery() {
        val actorScreen = mockk<PuberScreen>()
        every { screens.actorItems("Original KinoPub Name") } returns actorScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.CastMemberSelected("Original KinoPub Name"))

        verify(exactly = 1) { router.navigateTo(actorScreen) }
    }

    @Test
    fun castEnrichment_updatesOnlyPhotosAfterDetailsContentIsPublished() {
        val cards = listOf(castCard())
        val enrichedCards = cards.map { card ->
            card.copy(photoUrl = "https://image/actor")
        }
        val item = testItem.copy(
            imdb = "tt123",
            trailer = Trailer(url = "https://trailer"),
        )
        val releaseCast = CompletableDeferred<List<TmdbCastMember>>()
        val castStarted = CompletableDeferred<Unit>()
        val stableSimilarItems = listOf(videoItem(id = 100))
        coEvery { interactor.getItemDetails(42) } returns item
        every { mapper.map(item, false) } returns content(
            similarItems = stableSimilarItems,
            isInWatchlist = true,
            isWatched = true,
            castCards = cards,
        )
        every { mapper.mapSimilarItems(any()) } returns stableSimilarItems
        coEvery { interactor.getTmdbCast("tt123") } coAnswers {
            castStarted.complete(Unit)
            releaseCast.await()
        }
        every {
            mapper.enrichCastCards(cards, listOf(TmdbCastMember("Actor", "https://image/actor")))
        } returns enrichedCards

        val vm = startedVM()
        assertTrue(vm.testStateValue is DetailsScreenState.Content)
        assertTrue(castStarted.isCompleted)

        vm.onAction(DetailsAction.TrailerClicked)
        vm.onAction(DetailsAction.SelectSeasonClicked)
        releaseCast.complete(listOf(TmdbCastMember("Actor", "https://image/actor")))

        val state = vm.testStateValue as DetailsScreenState.Content
        assertEquals(enrichedCards, state.info.castCards)
        assertEquals(stableSimilarItems, state.similarItems)
        assertEquals(true, state.isInWatchlist)
        assertEquals(true, state.isWatched)
        assertEquals(true, state.seasonsPanelVisible)
        assertEquals("https://trailer", state.trailerUrl)
    }

    @Test
    fun castEnrichmentFailure_preservesDetailsContentWithoutFullScreenError() {
        val cards = listOf(castCard(photoUrl = "https://image/previous"))
        val item = testItem.copy(imdb = "tt123")
        coEvery { interactor.getItemDetails(42) } returns item
        every { mapper.map(item, false) } returns content(castCards = cards)
        coEvery { interactor.getTmdbCast("tt123") } throws IllegalStateException("TMDB unavailable")

        val vm = startedVM()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
        assertEquals(cards, (vm.testStateValue as DetailsScreenState.Content).info.castCards)
        verify(exactly = 0) { errorHandler.proceedInvoke(any(), any()) }
    }

    @Test
    fun castEnrichmentCancellation_isRethrownWithoutDispatchingAnError() {
        val cards = listOf(castCard())
        val item = testItem.copy(imdb = "tt123")
        coEvery { interactor.getItemDetails(42) } returns item
        every { mapper.map(item, false) } returns content(castCards = cards)
        coEvery { interactor.getTmdbCast("tt123") } throws CancellationException("cancelled")

        val vm = startedVM()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
        verify(exactly = 0) { errorHandler.proceedInvoke(any(), any()) }
    }

    @Test
    fun castEnrichmentFatalError_isNotSuppressedByBestEffortFallback() {
        val cards = listOf(castCard())
        val item = testItem.copy(imdb = "tt123")
        val fatalError = AssertionError("fatal")
        coEvery { interactor.getItemDetails(42) } returns item
        every { mapper.map(item, false) } returns content(castCards = cards)
        coEvery { interactor.getTmdbCast("tt123") } throws fatalError

        val vm = startedVM()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
        verify(exactly = 1) { errorHandler.proceedInvoke(fatalError, any()) }
    }

    @Test
    fun detailsRefresh_preservesExistingCastPhotosWhenEnrichmentHasNoData() {
        val originalCards = listOf(castCard(photoUrl = "https://image/previous"))
        val refreshedCards = listOf(castCard())
        val item = testItem.copy(imdb = "tt123")
        val refreshedItem = item.copy(title = "Series refreshed")
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        coEvery { interactor.getItemDetails(42) } returns item
        coEvery { interactor.refreshItemDetails(42) } returns refreshedItem
        coEvery { interactor.getTmdbCast("tt123") } returns emptyList()
        every { mapper.map(item, false) } returns content(castCards = originalCards)
        every { mapper.map(refreshedItem, false) } returns content(castCards = refreshedCards)

        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(
                playerScreen,
                RESULT_CONTENT_CHANGED,
                capture(listener),
            )
        }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        assertEquals(
            originalCards,
            (vm.testStateValue as DetailsScreenState.Content).info.castCards,
        )
    }

    private fun startedVM(): DetailsVM = DetailsVM(
        router = router,
        params = DetailsScreenParams(itemId = 42),
        mapper = mapper,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    ).also { it.testOnStart() }

    private fun content(
        similarItems: List<VideoItemUIState> = emptyList(),
        isInWatchlist: Boolean = false,
        isWatched: Boolean = false,
        castCards: List<DetailsCastMemberUIState> = emptyList(),
    ) = DetailsScreenState.Content(
        details = VideoDetailsUIState.Loading,
        info = DetailsInfoUIState(
            description = "",
            ratings = emptyList(),
            primaryRows = emptyList(),
            secondaryRows = emptyList(),
            castCards = castCards,
        ),
        buttons = emptyList(),
        isInWatchlist = isInWatchlist,
        isWatched = isWatched,
        similarItems = similarItems,
    )

    private fun castCard(photoUrl: String? = null) = DetailsCastMemberUIState(
        actorQuery = "Actor",
        displayName = "Actor",
        photoUrl = photoUrl,
    )

    private fun videoItem(id: Int) = VideoItemUIState(
        id = id,
        title = "Item $id",
        imageUrl = "",
        bigImageUrl = "",
        isSeriesLike = false,
    )

    private val testItem = Item(
        id = 42,
        title = "Series",
        type = ItemType.SERIAL,
    )
}
