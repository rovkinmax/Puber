package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HomeVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: HomeInteractor
    private lateinit var mapper: HomeUIMapper
    private lateinit var apiDomainInteractor: ApiDomainInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        apiDomainInteractor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk { every { proceed(any()) } returns { } }

        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.Success(
            state = ApiDomainState(domain = "api.example", customDomain = null),
            changed = false,
        )
        coEvery { interactor.getHotItems(any(), any()) } returns Result.success(emptyList())
        coEvery { interactor.getWatchingItems() } returns Result.success(emptyList())
        coEvery { interactor.getFreshItems(any()) } returns Result.success(emptyList())
        coEvery { interactor.getPopularByType(any()) } returns Result.success(emptyList())
        coEvery { interactor.getWatchLaterItems() } returns Result.success(emptyList())
        coEvery { interactor.getGenericBookmarkItems() } returns Result.success(emptyList())
        coEvery { interactor.getCollections() } returns Result.success(emptyList())
        every { mapper.mapItemSection(any(), any()) } returns null
        every { mapper.mapCollectionSection(any()) } returns null
        every { mapper.mapHeroItems(any()) } returns emptyList()
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
    fun itemPlayed_navigatesForContentChangeResultToPlayer() {
        val screen = mockk<PuberScreen>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun returnedChanges_refreshContentStateSilently() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        coVerify(exactly = 2) { apiDomainInteractor.autoResolveWorkingDomain() }
    }

    @Test
    fun returnedChangesAndResume_keepOnlyLatestRefreshRunning() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        var resolveCalls = 0
        every { screens.details(42) } returns screen
        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } coAnswers {
            resolveCalls += 1
            if (resolveCalls > 1) {
                refreshGate.await()
            }
            ApiDomainAutoResolveResult.Success(
                state = ApiDomainState(domain = "api.example", customDomain = null),
                changed = false,
            )
        }
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        vm.onAction(CommonAction.OnResume)
        refreshGate.complete(Unit)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { interactor.getWatchingItems() }
    }

    private fun createVM() = HomeVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        apiDomainInteractor = apiDomainInteractor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    )

    private fun videoItem(id: Int) = VideoItemUIState(
        id = id,
        title = "Item $id",
        imageUrl = "",
        bigImageUrl = "",
    )
}
