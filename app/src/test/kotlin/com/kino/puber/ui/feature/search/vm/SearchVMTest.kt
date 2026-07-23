package com.kino.puber.ui.feature.search.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
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
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
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

    private fun createVM() = SearchVM(
        router = router,
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
        interactor = interactor,
        savedItemInteractor = mockk<SavedItemInteractor>(relaxed = true),
        mapper = mapper,
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")
}
