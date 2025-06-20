package com.kino.puber.core.ui.model

import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item

class VideoItemUIMapper {

    fun mapList(items: List<Item>): List<VideoItemUIState> {
        return items.map { map(it) }
    }

    fun map(item: Item): VideoItemUIState {
        return VideoItemUIState(
            title = item.title,
            imageUrl = item.posters?.medium.orEmpty(),
        )
    }
}