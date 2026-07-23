package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.ShowAllScreen
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ContentListVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var refreshCoordinator: ContentListRefreshCoordinator
    private lateinit var interactor: ContentListInteractor
    private lateinit var mapper: VideoItemUIMapper

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        refreshCoordinator = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        coEvery { interactor.getItemDetails(42) } returns Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        every { mapper.mapDetailedItem(any()) } returns VideoDetailsUIState.Loading.copy(id = 42)
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
    fun showAll_navigatesForContentChangeResult() {
        val config = SectionConfig(id = "popular", title = "Popular")
        val vm = createVM()

        vm.onAction(ContentListAction.ShowAll(config))

        verify {
            router.navigateForResult<ContentChangeSet>(
                match { it is ShowAllScreen },
                RESULT_CONTENT_CHANGED,
                any(),
            )
        }
    }

    @Test
    fun emptyShowAllResult_consumesListenerWithoutRefreshingSections() {
        val config = SectionConfig(id = "popular", title = "Popular")
        val listener = slot<(ContentChangeSet?) -> Unit>()
        val vm = createVM()
        vm.onAction(ContentListAction.ShowAll(config))
        verify {
            router.navigateForResult<ContentChangeSet>(
                match { it is ShowAllScreen },
                RESULT_CONTENT_CHANGED,
                capture(listener),
            )
        }

        listener.captured(ContentChangeSet.empty())

        verify(exactly = 0) { interactor.invalidateFirstPageCache() }
        verify(exactly = 0) { refreshCoordinator.requestRefresh() }
    }

    @Test
    fun returnedChanges_invalidateFirstPageCacheAndRequestSectionRefresh() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        verifyOrder {
            interactor.invalidateFirstPageCache()
            refreshCoordinator.requestRefresh()
        }
    }

    @Test
    fun returnedChangesForFocusedItem_invalidateAndReloadSelectedDetails() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(151)
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        verify(exactly = 1) { interactor.invalidateItemDetails(42) }
    }

    @Test
    fun returnedChanges_invalidateDetailsForEveryChangedItem() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(
            ContentChangeSet
                .single(42, ContentChangeType.Watched)
                .merge(ContentChangeSet.single(100, ContentChangeType.Bookmark))
        )

        verify(exactly = 1) { interactor.invalidateItemDetails(42) }
        verify(exactly = 1) { interactor.invalidateItemDetails(100) }
    }

    private fun createVM() = ContentListVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        genreInteractor = mockk(relaxed = true),
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
            every { getNavigationMode() } returns NavigationMode.SideDrawer
        },
        contentListRefreshCoordinator = refreshCoordinator,
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")
}
