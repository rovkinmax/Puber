package com.kino.puber.ui.feature.favorites.model

import com.kino.puber.core.ui.model.VideoItemTypeMapper
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike

internal class FavoriteItemUIMapper(
    val videoItemUIMapper: VideoItemUIMapper,
    val typeMapper: VideoItemTypeMapper,
) {

    fun mapToState(items: List<Item>, selectedItem: Item?): FavoriteViewState.Content {
        val seriesOnly = items.filter { it.type.isSeriesLike() }
        val effectiveSelected = if (selectedItem?.type?.isSeriesLike() == true) selectedItem
            else seriesOnly.firstOrNull()
        return FavoriteViewState.Content(
            gridState = mapList(items),
            selectedItem = effectiveSelected?.let(::mapDetailedItem) ?: VideoDetailsUIState.Loading,
        )
    }


    fun mapList(items: List<Item>): VideoGridUIState {
        val seriesOnly = items.filter { it.type.isSeriesLike() }
        return VideoGridUIState(
            list = buildList {
                val groupedItems = seriesOnly.groupBy { it.type }
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