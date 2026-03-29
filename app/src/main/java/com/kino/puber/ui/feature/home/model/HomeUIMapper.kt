package com.kino.puber.ui.feature.home.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection

internal class HomeUIMapper(
    private val videoItemMapper: VideoItemUIMapper,
    private val resources: ResourceProvider,
) {

    fun mapHeroItems(items: List<Item>): List<HeroItemState> {
        return items.map { item ->
            HeroItemState(
                id = item.id,
                title = item.title,
                wideImageUrl = item.posters?.wide.orEmpty(),
                fallbackImageUrl = item.posters?.big.orEmpty(),
                year = item.year?.toString().orEmpty(),
                rating = item.imdbRating?.toString().orEmpty(),
                genres = item.genres?.joinToString(", ") { it.title }.orEmpty(),
            )
        }
    }

    fun mapHistorySection(items: List<History>): HomeSectionState? {
        if (items.isEmpty()) return null
        return HomeSectionState(
            title = resources.getString(R.string.home_section_continue_watching),
            items = items.map { videoItemMapper.mapHistoryItem(it) },
            type = HomeSectionType.ContinueWatching,
        )
    }

    fun mapItemSection(items: List<Item>, type: HomeSectionType): HomeSectionState? {
        if (items.isEmpty()) return null
        val title = when (type) {
            HomeSectionType.Fresh -> resources.getString(R.string.home_section_fresh)
            HomeSectionType.PopularMovies -> resources.getString(R.string.home_section_popular_movies)
            HomeSectionType.PopularSeries -> resources.getString(R.string.home_section_popular_series)
            HomeSectionType.Bookmarks -> resources.getString(R.string.home_section_bookmarks)
            HomeSectionType.Hot -> resources.getString(R.string.home_section_hot)
            else -> ""
        }
        return HomeSectionState(
            title = title,
            items = videoItemMapper.mapShortItemList(items),
            type = type,
        )
    }

    fun mapCollectionSection(collections: List<KCollection>): HomeSectionState? {
        if (collections.isEmpty()) return null
        return HomeSectionState(
            title = resources.getString(R.string.home_section_collections),
            items = collections.map { collection ->
                VideoItemUIState(
                    id = collection.id,
                    title = collection.title,
                    imageUrl = collection.posters?.medium.orEmpty(),
                    bigImageUrl = collection.posters?.big.orEmpty(),
                    wideImageUrl = collection.posters?.wide.orEmpty(),
                    showTitle = true,
                )
            },
            type = HomeSectionType.Collections,
        )
    }
}
