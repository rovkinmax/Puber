package com.kino.puber.ui.feature.collections.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.ui.feature.collections.detail.CollectionDetailScreen
import com.kino.puber.ui.feature.collections.model.CollectionUIState
import com.kino.puber.ui.feature.collections.model.CollectionsViewState

internal class CollectionsVM(
    router: AppRouter,
    private val interactor: CollectionInteractor,
) : PuberVM<CollectionsViewState>(router) {

    override val initialViewState: CollectionsViewState = CollectionsViewState.Loading
    private var currentPage = 1
    private var hasMore = true

    override fun onStart() {
        loadCollections()
    }

    override fun onAction(action: UIAction) {
        super.onAction(action)
    }

    fun onCollectionClick(collection: CollectionUIState) {
        router.navigateTo(CollectionDetailScreen(collection.id, collection.title))
    }

    fun onLoadMore() {
        if (!hasMore) return
        loadCollections()
    }

    private fun loadCollections() {
        launch {
            interactor.getCollections(currentPage).onSuccess { response ->
                val items = response.items.map { c ->
                    CollectionUIState(
                        id = c.id,
                        title = c.title,
                        imageUrl = c.posters?.medium.orEmpty(),
                        wideImageUrl = c.posters?.wide.orEmpty(),
                        count = c.count ?: 0,
                    )
                }
                hasMore = response.pagination.current < response.pagination.total
                currentPage++

                val currentState = when (val s = initialViewState) {
                    is CollectionsViewState.Content -> s
                    else -> CollectionsViewState.Content()
                }
                updateViewState<CollectionsViewState> {
                    when (this) {
                        is CollectionsViewState.Content -> copy(collections = collections + items)
                        else -> CollectionsViewState.Content(collections = items)
                    }
                }
            }.onFailure { error ->
                if (currentPage == 1) {
                    updateViewState(CollectionsViewState.Error(error.message ?: "Error"))
                }
            }
        }
    }
}
