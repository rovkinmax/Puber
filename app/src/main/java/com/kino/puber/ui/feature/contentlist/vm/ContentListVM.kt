package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.ContentListViewState
import com.kino.puber.ui.feature.showall.ShowAllScreen
import kotlinx.coroutines.Job

internal class ContentListVM(
    router: AppRouter,
    private val interactor: ContentListInteractor,
    private val mapper: VideoItemUIMapper,
) : PuberVM<ContentListViewState>(router) {

    override val initialViewState = ContentListViewState()
    private var focusedItemJob: Job? = null

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is ContentListAction.ShowAll -> router.navigateTo(ShowAllScreen(action.config))
        }
    }

    private fun onItemFocused(item: VideoItemUIState) {
        focusedItemJob?.cancel()
        focusedItemJob = launch {
            updateViewState(ContentListViewState(selectedItem = VideoDetailsUIState.Loading))
            val details = interactor.getItemDetails(item.id)
            updateViewState(ContentListViewState(selectedItem = mapper.mapDetailedItem(details)))
        }
    }

    private fun onItemSelected(item: VideoItemUIState) {
        // TODO: navigate to details screen
    }
}
