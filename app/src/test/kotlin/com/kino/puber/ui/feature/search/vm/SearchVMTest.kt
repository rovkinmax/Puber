package com.kino.puber.ui.feature.search.vm

import com.kino.puber.R
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.search.SearchInteractor
import com.kino.puber.ui.feature.search.model.SearchScreenParams
import com.kino.puber.ui.feature.search.model.SearchViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SearchVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: SearchInteractor
    private lateinit var mapper: VideoItemUIMapper
    private lateinit var resources: ResourceProvider

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        resources = mockk {
            every { getString(R.string.search_hint) } returns "Search hint"
            every { getString(R.string.search_no_results) } returns "No results"
            every { getString(R.string.search_actor_no_results) } returns "No actor results"
            every {
                getString(R.string.search_actor_title, "Tom Hanks")
            } returns "Featuring: Tom Hanks"
        }
        coEvery { interactor.search("query") } returns listOf(Item(id = 42, title = "Movie", type = ItemType.MOVIE))
        every { mapper.mapShortItemList(any()) } returns listOf(videoItem(42))
    }

    @Test
    fun itemSelected_navigatesForContentChangeResultToDetails() {
        val screen = mockk<PuberScreen>()
        every { screens.details(42) } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemSelected(videoItem(42)))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun returnedChanges_executeCurrentQuery() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.TextChanged("query", tag = Unit))
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        coVerify(exactly = 1) { interactor.search("query") }
    }

    @Test
    fun actorMode_loadsImmediatelyUsingOnlyActorFilter() {
        coEvery { interactor.searchByActor("Tom Hanks") } returns listOf(
            Item(id = 7, title = "Movie", type = ItemType.MOVIE),
        )
        val vm = createVM(SearchScreenParams(SearchScreenParams.SearchMode.Actor("Tom Hanks")))

        vm.testOnStart()

        val state = vm.testStateValue as SearchViewState.Content
        assertEquals("Featuring: Tom Hanks", state.presentation.title)
        assertEquals(false, state.presentation.showSearchInput)
        coVerify(exactly = 1) { interactor.searchByActor("Tom Hanks") }
        coVerify(exactly = 0) { interactor.search(any()) }
    }

    @Test
    fun actorMode_retryRepeatsTheImmutableActorQuery() {
        coEvery { interactor.searchByActor("Tom Hanks") } returnsMany listOf(
            emptyList(),
            listOf(Item(id = 7, title = "Movie", type = ItemType.MOVIE)),
        )
        val vm = createVM(SearchScreenParams(SearchScreenParams.SearchMode.Actor("Tom Hanks")))

        vm.testOnStart()
        vm.onAction(CommonAction.RetryClicked)

        assertTrue(vm.testStateValue is SearchViewState.Content)
        coVerify(exactly = 2) { interactor.searchByActor("Tom Hanks") }
        coVerify(exactly = 0) { interactor.search(any()) }
    }

    @Test
    fun actorMode_ignoresTitleTextChanges() {
        val vm = createVM(SearchScreenParams(SearchScreenParams.SearchMode.Actor("Tom Hanks")))

        vm.onAction(CommonAction.TextChanged("query", tag = Unit))

        coVerify(exactly = 0) { interactor.search(any()) }
    }

    @Test
    fun actorMode_failurePublishesErrorState() {
        coEvery { interactor.searchByActor("Tom Hanks") } throws IllegalStateException("failed")
        val vm = createVM(SearchScreenParams(SearchScreenParams.SearchMode.Actor("Tom Hanks")))

        vm.testOnStart()

        assertTrue(vm.testStateValue is SearchViewState.Error)
    }

    private fun createVM(
        params: SearchScreenParams = SearchScreenParams(),
    ) = SearchVM(
        router = router,
        errorHandler = mockk<ErrorHandler> {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } answers {
                secondArg<((ErrorEntity) -> Unit)?>()?.invoke(
                    ErrorEntity(message = "failed", code = "test"),
                )
            }
        },
        interactor = interactor,
        savedItemInteractor = mockk<SavedItemInteractor>(relaxed = true),
        mapper = mapper,
        resources = resources,
        params = params,
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")
}
