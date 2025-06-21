package com.kino.puber.ui.feature.favorites.model

import com.kino.puber.core.ui.model.VideoItemTypeMapper
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.data.api.models.Item

internal class FavoriteItemUIMapper(
    val videoItemUIMapper: VideoItemUIMapper,
    val typeMapper: VideoItemTypeMapper,
) {

    fun mapToState(items: List<Item>, selectedItem: Item?): FavoriteViewState.Content {
        return FavoriteViewState.Content(
            gridState = mapList(items),
            selectedItem = selectedItem?.let(::mapDetailedItem),
        )
    }


    fun mapList(items: List<Item>): VideoGridUIState {
        return VideoGridUIState(
            list = buildList {
                val groupedItems = items.groupBy { it.type }
                groupedItems.forEach { (type, items) ->
                    if (groupedItems.size > 1) {
                        add(VideoGridItemUIState.Title(typeMapper.map(type)))
                    }
                    add(VideoGridItemUIState.Items(videoItemUIMapper.mapShortItemList(items)))
                }
            },
        )
    }

    fun mapDetailedItem(item: Item): VideoDetailsUIState {
        return videoItemUIMapper.mapDetailedItem(item)
    }

}