package com.kino.puber.ui.feature.collections.detail.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.ui.feature.collections.detail.model.CollectionDetailViewState

internal class CollectionDetailVM(
    router: AppRouter,
    private val collectionId: Int,
    private val collectionTitle: String,
    private val interactor: CollectionInteractor,
    private val mapper: VideoItemUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<CollectionDetailViewState>(router) {

    override val initialViewState = CollectionDetailViewState.Loading

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is CollectionDetailViewState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(CollectionDetailViewState.Error(error.message))
        }
    }

    override fun onStart() {
        loadItems()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                router.navigateTo(router.screens.details(item.id))
            }
            is CommonAction.RetryClicked -> loadItems()
            else -> super.onAction(action)
        }
    }

    private fun loadItems() {
        updateViewState(CollectionDetailViewState.Loading)
        launch {
            val items = interactor.getCollectionItems(collectionId)
            updateViewState(
                CollectionDetailViewState.Content(
                    title = collectionTitle,
                    items = mapper.mapShortItemList(items),
                )
            )
        }
    }
}
